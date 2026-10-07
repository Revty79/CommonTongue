plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target(
            "app/src/**/*.kt",
            "core/domain/src/**/*.kt",
            "spikes/offline-feasibility/src/**/*.kt",
        )
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
    kotlinGradle {
        target(
            "*.gradle.kts",
            "app/*.gradle.kts",
            "core/domain/*.gradle.kts",
            "spikes/offline-feasibility/*.gradle.kts",
        )
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
    format("projectText") {
        target("*.md", "docs/**/*.md", "*.properties", "gradle/**/*.toml", ".github/**/*.yml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
