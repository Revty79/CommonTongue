# Common Tongue

Common Tongue is the working name for an Android-first, offline-first conversational translation product. This repository is `common-tongue`. iPhone support is planned later; this pass contains native Android only.

**The translation UI remains a foundation build; Pass 6 adds its production offline speech adapter.** Pass 1 provides the Kotlin/Jetpack Compose foundation, boundaries and automation. Experimental ASR/translation models and runtimes remain outside the production app and domain. The debug-only voice check exercises the production `SpeechSynthesizer` adapter without a model pack.

Pass 6's requested S25 phone-speaker proof is complete: English, Spanish and replay pass through the production adapter with installed offline voices and airplane mode off; the tester confirmed audibility. Keep the tested version 7 APK. See [Pass 6 closeout and remaining release concerns](docs/voice/PASS_6_CLOSEOUT.md). Pass 7 has not begun and requires review/authorization.

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
platform/android-speech/     Installed offline TTS adapter and controlled playback
tools/voice-layer/            Model-free phone check packaging and baseline verification
gradle/                      Version catalog and wrapper
.github/workflows/android.yml
docs/                        Architecture, privacy, model policy, ADRs, validation
```

The root has four modules: app depends on the Android speech adapter and the neutral translation/domain core. Both core modules remain pure Kotlin/JVM with no Android, Compose, network or AI runtime dependency. Translation exports domain values and uses the existing coroutines dependency. Research builds remain separate, outside normal CI configuration. Constructor injection suffices; the foundation route is unchanged. See [Pass 6 implementation and acceptance](docs/voice/PASS_6_OFFLINE_VOICE_LAYER.md) and [installed-voice licensing](docs/voice/LICENSING_PROVENANCE.md).

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

The foundation screen displays Common Tongue and the absent translation engine status. The debug APK also provides a **Common Tongue Voice Check** launcher for fixed English/Spanish speech, replay, replacement and results export. It requires installed offline voices, no model pack or microphone. Wi-Fi/cellular may stay enabled. Follow [START-HERE](tools/voice-layer/START-HERE.txt); existing Pass 5 testers retain their separate research installation and data.

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. For an unsigned optimized release APK, run `:app:assembleRelease`. No publishing is configured.

Windows environment note: if unit-test Java reports `Could not find or load main class Files`, check PATH for unmatched quotes. This host had one malformed PATH entry; validation removed quotes only in the build process (`$env:Path = $env:Path.Replace('"', '')`), without modifying the machine's saved environment. Use a valid PATH and JDK 17 for normal builds. Local downloaded tools and emulator data are ignored under `.local/`.

## Checks and tests

- `test`: core/ViewModel tests and Android speech unit tests, including offline voice selection, replay, lifecycle and rapid cancellation; no models or device needed.
- `verifyCoreBoundaries`: checks production/test core dependency graphs and imports against neutral JVM allowlists; rejects platform/provider/I/O coupling. Core `check` tasks also run it.
- `lint`: Android lint for the app and both pure JVM core modules, including dependencies; errors fail the build and warnings remain visible. Version-update advisories for the deliberate SDK/toolchain pins are documented in the validation report.
- `spotlessCheck`: one consistent Kotlin/Kotlin DSL formatter plus text whitespace checks. Use `spotlessApply` to format.
- `:app:verifyFoundationManifest`: examines debug/release merged manifests, enforces minSdk 26 / targetSdk 36, and rejects permissions except AndroidX's internal app-scoped signature permission.
- `assembleDebug`: compiles/packages the development application.
- `:app:assembleDebugAndroidTest`: compiles/packages the UI test without claiming it ran.
- `:app:connectedDebugAndroidTest`: runs UI and real installed-voice integration on connected devices. The voice test skips if required voices are absent; a skip is not acceptance. Phone-only voice-check installation/export is available without ADB.

Run instrumentation locally after starting an emulator or attaching a device. Windows uses `.\gradlew.bat :app:connectedDebugAndroidTest`; macOS/Linux uses `./gradlew :app:connectedDebugAndroidTest`. A skipped device test is not a pass. See [Pass 1 validation](docs/PASS_1_VALIDATION.md) for executed results and environment limits.

GitHub Actions runs build, unit tests, lint, formatting, manifest verification, debug/test APK assembly, research tooling tests and unchanged native/model baseline verification. It uploads the Pass 6 model-free voice-check APK, checksums, requirements and test reports. It uses JDK 17 and read-only repository permission. CI downloads no research models; physical acceptance is recorded separately.

## Architecture and privacy

Read [ARCHITECTURE.md](docs/ARCHITECTURE.md), [PRIVACY.md](docs/PRIVACY.md), [MODEL_POLICY.md](docs/MODEL_POLICY.md), and the [architecture decisions](docs/DECISIONS/). Offline translation, ephemeral conversations, replaceable model implementations, low-end phones, and no mandatory account are future product constraints. Pass 1 stores no conversations and makes no network calls.

For isolated Pass 2 reproduction, see [the harness README](tools/offline-feasibility/README.md), [candidate licenses](docs/feasibility/MODEL_CANDIDATES.md), [benchmarks](docs/feasibility/BENCHMARK_RESULTS.md), and [Android evidence](docs/feasibility/ANDROID_RESULTS.md). No model is approved for production. [Pass 3 validation](docs/PASS_3_VALIDATION.md) records local contract/guard tests.

Pass 4 compares dedicated MT, direct local LLM translation, contextual post-editing, and bounded verification/repair using 196 balanced text fixtures. Read the [selection report](docs/quality/PASS_4_QUALITY_BAKEOFF.md), [benchmark specification](docs/quality/BENCHMARK_SPEC.md), and [reproduction commands](tools/translation-quality/README.md). Its model-free schema/scoring/blinding tests run in ordinary CI; large research models and native toolchains remain ignored under `.local/`. All app/core source, model-neutral contracts, permissions and production dependencies remain unchanged. Physical-phone testing and production adapters remain later work.

Pass 5 is accepted on the successful physical Galaxy S25 v7 proof. Use the existing [accepted v7 research APK](https://github.com/Revty79/CommonTongue/releases/tag/pass5-s25-tts-diagnostic-2026-10-08) with the unchanged [test pack](https://github.com/Revty79/CommonTongue/releases/tag/pass5-research-preview-2026-10-08); existing testers keep their installed app/data/pack/voices. Direct and file TTS checks pass in English and Spanish, both real microphone directions reach TTS_COMPLETE, and the prerecorded full pipeline passes. See the [closeout and exact baseline](docs/device/PASS_5_CLOSEOUT.md) and [phone-only setup](docs/device/LOCAL_TEST_PACK_SETUP.md). First post-load EN→ES playback-start evidence is approximately 4,665 ms; broader human/device/sustained validation remains follow-up. Historical crashes remain regression history. APK/models/signing/private exports stay outside Git; the 14 WAV controls are synthetic. The final Pass 5 commit is `5ee45413e3c72493ae3f13ec6f913728828a3e0f`. Pass 6 preserves that native/model research baseline while adding the independent production voice layer; its S25 acceptance must use new results.
