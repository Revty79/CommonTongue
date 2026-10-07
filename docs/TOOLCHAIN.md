# Toolchain selection

Selection date: October 7, 2026. Pin stable artifacts available from Google Maven, Maven Central, and the Gradle Plugin Portal. No dynamic versions or prerelease dependencies are configured.

| Tool/library | Version | Purpose |
| --- | --- | --- |
| Android Gradle plugin | 9.4.1 | Native Android build, manifest processing, lint, R8; built-in Kotlin |
| Gradle | 9.6.0 | AGP 9.4's documented compatible default; checked-in wrapper |
| Kotlin JVM / Compose compiler | 2.4.20 | Domain/app Kotlin and matched Compose compiler plugin |
| Compose BOM | 2026.06.01 | Stable, consistent UI/runtime/test library versions |
| Activity Compose | 1.12.4 | ComponentActivity, Compose content, edge-to-edge |
| Lifecycle runtime/viewmodel Compose | 2.10.0 | Screen ViewModel and lifecycle-aware StateFlow observation |
| Coroutines core / test | 1.11.0 | StateFlow; deterministic unit-test coroutine execution |
| JUnit | 4.13.2 | App/domain JVM tests |
| AndroidX test JUnit / runner | 1.3.0 / 1.7.0 | Device test runner and AndroidJUnit4 |
| Espresso core | 3.7.0 | Current stable device-test bridge; overrides Compose's old transitive test dependency |
| Spotless / ktfmt | 8.10.3 / 0.64 | One Kotlin/Kotlin DSL formatting mechanism |
| JDK / bytecode target | 17 | Android build and pure JVM domain compilation |

Compose UI, foundation, runtime, Material 3, and UI-test JUnit versions are resolved by the BOM. The exact selected runtime versions and executed results are in [Pass 1 validation](PASS_1_VALIDATION.md). No navigation, DI framework, database, network client, AI/runtime library, or unrelated SDK is added. AndroidX dependencies are regular on-device UI/lifecycle libraries; development Maven repositories are not application cloud dependencies.

The newest stable AndroidX releases were checked first. Activity 1.13.0, Lifecycle 2.11.0, and Compose UI 1.12.x require compileSdk 37 in their AAR metadata. To preserve compileSdk 36, this project uses the latest compatible stable Activity 1.12.4, Lifecycle 2.10.0, and Compose BOM 2026.06.01 (UI/foundation/runtime 1.11.4 and Material 3 1.4.0). AAR metadata was inspected directly and Gradle's metadata check must pass; SDK requirements are not relaxed.

The standalone `com.android.lint` plugin uses the same AGP version to analyze the pure JVM domain. It is a build-time quality tool, adds no Android runtime dependency to the domain, and avoids leaving domain sources outside lint coverage. Spotless remains the single formatter.

[AndroidX Test's stable Espresso 3.7.0](https://developer.android.com/jetpack/androidx/releases/test) is pinned for instrumentation only: the compatible Compose test artifact otherwise selects Espresso 3.5.0, which failed against the current emulator's input-manager API. APK installation explicitly uses the stable AGP `installation.installOptions` option `-r` so repeated installs/tests work on API 26. Neither change adds product functionality or an application runtime dependency.

## Compatibility and sources

- [AGP 9.4 release compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes): supports API 36; Gradle 9.6.0, SDK Build Tools 36.0.0, and JDK 17 are documented defaults/minimums. Google's Maven metadata identifies 9.4.1 as a stable patch.
- [Built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin): AGP 9 builds Android Kotlin without `org.jetbrains.kotlin.android`. The root Kotlin JVM plugin keeps the compiler dependency aligned with the Compose plugin. No legacy Kotlin/DSL opt-out flags are used.
- [Kotlin releases](https://kotlinlang.org/releases.html) and [Gradle compatibility](https://kotlinlang.org/docs/gradle-configure-project.html): Kotlin 2.4.20 is stable and supports Gradle 9.6.0. Its fully tested AGP range ends at 9.3.1; newer stable AGP is allowed by Kotlin documentation, but this project must verify compatibility through actual compilation/tests/lint rather than assume it. The JVM plugin is used for the pure domain; Android uses AGP's built-in Kotlin.
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom) and [AndroidX releases](https://developer.android.com/jetpack/androidx/versions): use the stable BOM and stable Activity/Lifecycle/test artifacts, not alpha/beta BOMs.
- [Spotless](https://github.com/diffplug/spotless/tree/main/plugin-gradle) and [ktfmt](https://github.com/facebook/ktfmt): pinned formatter tooling, confined to the build.

## CI

Maintained action families are [actions/checkout](https://github.com/actions/checkout) v7, [actions/setup-java](https://github.com/actions/setup-java) v6, and [gradle/actions/setup-gradle](https://github.com/gradle/actions) v6. The Gradle action explicitly uses `cache-provider: basic`, the open-source caching path. No proprietary enhanced caching, build scan publication, paid service, or custom secrets are required. No remote CI execution is claimed by creating the workflow file.

The minimum remains API 26 and compile/target remain exactly API 36 even if newer Android platform releases exist. Raising the minimum or changing product boundaries requires explicit approval.
