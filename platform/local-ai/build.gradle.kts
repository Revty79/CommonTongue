plugins {
    id("java-library")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.android.lint)
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":core:translation"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.register<JavaExec>("realModelCheck") {
    group = "verification"
    description =
        "Explicit controlled host-native check through the production adapters; never part of model-free CI."
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "com.commontongue.local.HostModelCheckKt"
    args(rootDir.absolutePath)
}
