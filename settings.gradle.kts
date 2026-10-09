pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "common-tongue"

include(
    ":app",
    ":core:domain",
    ":core:translation",
    ":platform:android-speech",
    ":platform:local-ai",
    ":platform:android-local-ai",
)
