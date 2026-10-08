# Common Tongue

Common Tongue is the working name for an Android-first, offline-first conversational translation product. This repository is `common-tongue`. iPhone support is planned later; this pass contains native Android only.

**The production app remains a foundation build.** Pass 1 provides its Kotlin/Jetpack Compose screen, architecture boundaries, tests, and build automation. Pass 2 demonstrates offline speech translation in isolated desktop tooling and a standalone Android research app. Experimental models and runtimes are outside the production app and domain.

Pass 3 adds model-neutral capability contracts, structured quality/context/failure values, a cancellable text use case, and a scoped deterministic integer verifier in `:core:translation`. The UI still reports no installed engine. See [translation contracts](docs/TRANSLATION_CONTRACTS.md) and [quality architecture](docs/QUALITY_ARCHITECTURE.md).

The intended first product language pair is English and Latin-American Spanish, initially emphasizing Mexican/Latin-American Spanish. The research proof uses multilingual Whisper, OPUS-MT, and local TTS; regional/domain quality and physical-phone performance still need evaluation. See [Pass 2 findings](docs/feasibility/PASS_2_OFFLINE_FEASIBILITY.md).

## Toolchain

| Component | Selected version |
| --- | --- |
| Android Gradle plugin | 9.4.1, built-in Kotlin enabled |
| Gradle wrapper | 9.6.0 |
| Kotlin / Compose compiler plugin | 2.4.20 |
| Compose stable BOM | 2026.06.01 |
| Material 3 | Managed by the stable Compose BOM |
| JDK / JVM target | 17 |
| Android minimum | Android 8.0 / API 26 |
| Android compile / target | API 36 / API 36 |
| Android SDK Build Tools | 36.0.0 (AGP default) |
| Formatting | Spotless 8.10.3 with ktfmt 0.64 |

Versions are centralized in [gradle/libs.versions.toml](gradle/libs.versions.toml); the wrapper pins Gradle and its distribution checksum. Only stable dependencies are used. See [toolchain notes](docs/TOOLCHAIN.md) for sources and compatibility details.

The application ID **`com.commontongue.prototype` is TEMPORARY** and must be finalized before any Play Store release. Debug uses `com.commontongue.prototype.debug`, the launcher label `Common Tongue (Dev)`, and a `-dev` version suffix. The working product name lives in Android resources. Production signing is not configured; release builds are unsigned.

## Project structure

```text
app/                         Android application
  src/main/.../platform/     Activity and Android entry points
  src/main/.../ui/           Foundation screen, ViewModel, state, Material 3 theme
  src/test/                  ViewModel unit tests
  src/androidTest/           Compose startup/UI smoke test
core/domain/                 Pure Kotlin/JVM product concepts and unit tests
core/translation/            Pure Kotlin/JVM capabilities, text use case, scoped verification
gradle/                      Version catalog and wrapper
.github/workflows/android.yml
docs/                        Architecture, privacy, model policy, ADRs, validation
```

The root has three modules: `:app -> :core:translation -> :core:domain`. Both core modules are pure Kotlin/JVM with no Android, Compose, network or AI runtime dependency. Translation exports domain values and uses the existing coroutines dependency. The separate research build at `spikes/offline-feasibility` is invoked with `-p`, outside normal CI configuration. Desktop research is in `tools/offline-feasibility`. Constructor injection suffices for the text use case; the foundation UI is unchanged.

## Build and run

Install JDK 17 and a current stable Android Studio compatible with AGP 9.4, or use Android command-line tools. Install `platforms;android-36`, `build-tools;36.0.0`, and platform tools. Set `ANDROID_HOME` to the SDK directory or set `sdk.dir` in an untracked `local.properties`. Set `JAVA_HOME` to JDK 17. Do not commit machine-specific paths.

Windows PowerShell:

```powershell
.\gradlew.bat clean test lint spotlessCheck verifyCoreBoundaries :app:verifyFoundationManifest assembleDebug :app:assembleDebugAndroidTest
```

macOS/Linux:

```sh
./gradlew clean test lint spotlessCheck verifyCoreBoundaries :app:verifyFoundationManifest assembleDebug :app:assembleDebugAndroidTest
```

The first build downloads development dependencies. This does not add network access to the installed application. Later builds can use Gradle's `--offline` option once the required artifacts are cached.

Open the root directory in Android Studio, sync, and run `app` on an Android API 26+ emulator/device. Alternatively:

```powershell
.\gradlew.bat :app:installDebug
adb shell am start -n com.commontongue.prototype.debug/com.commontongue.prototype.platform.MainActivity
```

The screen displays Common Tongue, Foundation Build, the absent engine status, and the offline-first prototype description. It uses system light/dark behavior, scalable Material typography, safe drawing insets, a heading semantic, and scrolling for large text/small screens. There are no interactive controls or permission prompts.

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. For an unsigned optimized release APK, run `:app:assembleRelease`. No publishing is configured.

Windows environment note: if unit-test Java reports `Could not find or load main class Files`, check PATH for unmatched quotes. This host had one malformed PATH entry; validation removed quotes only in the build process (`$env:Path = $env:Path.Replace('"', '')`), without modifying the machine's saved environment. Use a valid PATH and JDK 17 for normal builds. Local downloaded tools and emulator data are ignored under `.local/`.

## Checks and tests

- `test`: domain/translation JVM tests and ViewModel unit tests; no models or device needed. New contract/use-case tests include scoped numeric verification, offline preflight, support limitations and coroutine cancellation.
- `verifyCoreBoundaries`: checks production/test core dependency graphs and imports against neutral JVM allowlists; rejects platform/provider/I/O coupling. Core `check` tasks also run it.
- `lint`: Android lint for the app and both pure JVM core modules, including dependencies; errors fail the build and warnings remain visible. Version-update advisories for the deliberate SDK/toolchain pins are documented in the validation report.
- `spotlessCheck`: one consistent Kotlin/Kotlin DSL formatter plus text whitespace checks. Use `spotlessApply` to format.
- `:app:verifyFoundationManifest`: examines debug/release merged manifests, enforces minSdk 26 / targetSdk 36, and rejects permissions except AndroidX's internal app-scoped signature permission.
- `assembleDebug`: compiles/packages the development application.
- `:app:assembleDebugAndroidTest`: compiles/packages the UI test without claiming it ran.
- `:app:connectedDebugAndroidTest`: actually launches the Activity and runs the Compose smoke test on connected devices.

Run instrumentation locally after starting an emulator or attaching a device. Windows uses `.\gradlew.bat :app:connectedDebugAndroidTest`; macOS/Linux uses `./gradlew :app:connectedDebugAndroidTest`. A skipped device test is not a pass. See [Pass 1 validation](docs/PASS_1_VALIDATION.md) for executed results and environment limits.

The GitHub Actions workflow runs build, unit tests, lint, formatting, manifest verification, debug assembly, UI-test compilation, and lightweight stdlib research tests on push/pull request or manual dispatch. It uses JDK 17, the stable SDK, read-only repository permission, and basic open-source Gradle caching. CI downloads no research models. Emulator/model integration tests remain explicit local research commands. Remote validation status is recorded in the pass reports.

## Architecture and privacy

Read [ARCHITECTURE.md](docs/ARCHITECTURE.md), [PRIVACY.md](docs/PRIVACY.md), [MODEL_POLICY.md](docs/MODEL_POLICY.md), and the [architecture decisions](docs/DECISIONS/). Offline translation, ephemeral conversations, replaceable model implementations, low-end phones, and no mandatory account are future product constraints. Pass 1 stores no conversations and makes no network calls.

For isolated research reproduction, see [the harness README](tools/offline-feasibility/README.md), [candidate licenses](docs/feasibility/MODEL_CANDIDATES.md), [benchmarks](docs/feasibility/BENCHMARK_RESULTS.md), and [Android evidence](docs/feasibility/ANDROID_RESULTS.md). No model is approved for production. [Pass 3 validation](docs/PASS_3_VALIDATION.md) records local contract/guard tests; the completion report includes the new commit and its remote CI result. No Pass 4 experimentation is implemented.
