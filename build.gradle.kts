import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.UnresolvedDependencyResult

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
}

val verifyCoreBoundaries =
    tasks.register("verifyCoreBoundaries") {
        group = "verification"
        description =
            "Allows only approved JVM dependencies and neutral production imports in core modules."
        inputs.files(fileTree("core") { include("*/build.gradle.kts", "*/src/**/*.kt") })
        doLast {
            val standard = setOf("org.jetbrains.kotlin:kotlin-stdlib", "org.jetbrains:annotations")
            val modules =
                mapOf(
                    ":core:domain" to standard,
                    ":core:translation" to
                        standard +
                            setOf(
                                "org.jetbrains.kotlinx:kotlinx-coroutines-core",
                                "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm",
                                "org.jetbrains.kotlinx:kotlinx-coroutines-bom",
                            ),
                )
            val imports = Regex("^import\\s+([^\\s;]+)", RegexOption.MULTILINE)
            val forbidden =
                Regex(
                    "\\b(android|androidx|onnx|whisper|ggml|tensorflow|litert|pytorch|executorch|espeak|piper|qwen|openai|anthropic)\\b",
                    RegexOption.IGNORE_CASE,
                )
            val platformIo = Regex("\\bjava\\.(net|io|nio\\.file)\\.")
            for ((path, allowed) in modules) {
                val module = project(path)
                for (configurationName in listOf("runtimeClasspath", "testRuntimeClasspath")) {
                    val approved =
                        if (configurationName == "testRuntimeClasspath")
                            allowed +
                                setOf(
                                    "junit:junit",
                                    "org.hamcrest:hamcrest-core",
                                    "org.jetbrains.kotlinx:kotlinx-coroutines-test",
                                    "org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm",
                                )
                        else allowed
                    val configuration = module.configurations.getByName(configurationName)
                    check(
                        configuration.allDependencies.toList().all {
                            it is ExternalModuleDependency || it is ProjectDependency
                        }
                    ) {
                        "Unreviewable file/self-resolving core dependency in $path"
                    }
                    val graph = configuration.incoming.resolutionResult
                    check(graph.allDependencies.none { it is UnresolvedDependencyResult }) {
                        "Unresolved core dependency in $path"
                    }
                    val components = graph.allComponents
                    for (component in components) {
                        when (val id = component.id) {
                            is ModuleComponentIdentifier ->
                                check("${id.group}:${id.module}" in approved) {
                                    "Unapproved core dependency in $path: $id"
                                }
                            is ProjectComponentIdentifier ->
                                check(
                                    id.projectPath == path ||
                                        (path == ":core:translation" &&
                                            id.projectPath == ":core:domain")
                                ) {
                                    "Invalid core project dependency: $path -> $id"
                                }
                            else ->
                                error("Unknown core dependency identity in $path: ${component.id}")
                        }
                    }
                }
                for (source in module.fileTree("src") { include("**/*.kt") }) {
                    val text = source.readText()
                    check(!forbidden.containsMatchIn(text) && !platformIo.containsMatchIn(text)) {
                        "Provider/platform leakage in ${source.name}"
                    }
                    for (match in imports.findAll(text)) {
                        val name = match.groupValues[1]
                        check(
                            name.startsWith("kotlin.") ||
                                name.startsWith("kotlinx.coroutines.") ||
                                name.startsWith("com.commontongue.domain.") ||
                                name.startsWith("com.commontongue.translation.") ||
                                name.startsWith("java.math.") ||
                                name.startsWith("java.time.") ||
                                (source.invariantSeparatorsPath.contains("/src/test/") &&
                                    name.startsWith("org.junit.")) ||
                                name in
                                    setOf(
                                        "java.util.Locale",
                                        "java.util.IllformedLocaleException",
                                        "java.util.Collections",
                                    )
                        ) {
                            "Unapproved core import in ${source.name}: $name"
                        }
                    }
                }
                logger.lifecycle(
                    "$path: pure JVM production/test graphs and neutral source imports verified"
                )
            }
        }
    }

spotless {
    kotlin {
        target(
            "app/src/**/*.kt",
            "core/domain/src/**/*.kt",
            "core/translation/src/**/*.kt",
            "platform/android-speech/src/**/*.kt",
            "spikes/offline-feasibility/src/**/*.kt",
            "spikes/physical-trial/src/**/*.kt",
        )
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
    kotlinGradle {
        target(
            "*.gradle.kts",
            "app/*.gradle.kts",
            "core/domain/*.gradle.kts",
            "core/translation/*.gradle.kts",
            "platform/android-speech/*.gradle.kts",
            "spikes/offline-feasibility/*.gradle.kts",
            "spikes/physical-trial/*.gradle.kts",
        )
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
    format("projectText") {
        target("*.md", "docs/**/*.md", "*.properties", "gradle/**/*.toml", ".github/**/*.yml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
