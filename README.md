# Common Tongue

Common Tongue is the working name for an Android-first, offline-first conversational translation product. This repository is `common-tongue`. iPhone support is planned later; this pass contains native Android only.

**Pass 8 integrates the real one-device PTT translator.** Hold English or Espa?ol, speak and release: microphone ? accepted production recognition ? translation ? offline system voice ? controlled playback/replay. Four selectable appearances share one functional screen. Models stay in private storage, separate from APK/Git.

Pass 7 is physically accepted on the S25 at `d22b8fa513b79da29857e620affc6485993b4742` ([closeout](docs/PASS_7_CLOSEOUT.md)). Pass 6's voice implementation is unchanged. Pass 8's own real-product physical acceptance remains pending. See [product ownership, pack reuse and phone checklist](docs/PASS_8_PRODUCT.md), [validation](docs/PASS_8_VALIDATION.md), [locked provenance](docs/LOCAL-AI-PROVENANCE.md) and [roadmap](docs/ROADMAP.md). No Pass 9 work is included.

The initial pair is English/Spanish, with installed offline regional Spanish voices preferred by the accepted voice policy. Regional/domain translation quality remains unproven. Capability confidence and meaning verification are not fabricated. Core contracts and earlier controlled research evidence are retained.

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

The application ID **`com.commontongue.prototype` is TEMPORARY** and must be finalized before any Play Store release. Debug uses `com.commontongue.prototype.debug`, the launcher label `Common Tongue`, and a `-dev` version suffix. The working product name lives in Android resources. Production signing is not configured; release builds are unsigned.

## Project structure

```text
app/                         Android application
  src/main/.../platform/     Activity and Android entry points
  src/main/.../ui/           Shared translator screen and four selectable token sets
  src/test/                  ViewModel, microphone and theme unit tests
  src/androidTest/           Compose product, touch/theme and voice integration tests
core/domain/                 Pure Kotlin/JVM product concepts and unit tests
core/translation/            Pure Kotlin/JVM capabilities, turn coordinator, text use case, scoped verification
platform/android-speech/     Installed offline TTS adapter and controlled playback
platform/local-ai/           Production recognition/translation adapters and shared lifecycle
platform/android-local-ai/   Resource bindings, private worker and checked JNI
tools/local-ai/              Locked native preparation, component checks and artifact packaging
tools/voice-layer/            Model-free phone check packaging and baseline verification
gradle/                      Version catalog and wrapper
.github/workflows/android.yml
docs/                        Architecture, privacy, model policy, ADRs, validation
```

The root has six modules. Both core modules remain pure Kotlin/JVM with no Android, Compose, network or AI runtime dependency. Constructor injection supplies neutral capabilities to UI/use cases. Normal CI builds native runtimes and runs model-free tests; it never acquires model weights. Accepted research sources/evidence and production TTS stay unchanged.

## Build and run

Install JDK 17 and a current stable Android Studio compatible with AGP 9.4, or use Android command-line tools. Install `platforms;android-36`, `build-tools;36.0.0`, and platform tools. Set `ANDROID_HOME` to the SDK directory or set `sdk.dir` in an untracked `local.properties`. Set `JAVA_HOME` to JDK 17. Do not commit machine-specific paths.

Windows PowerShell:

```powershell
# Prepare native sources/runtime once with Rust 1.91.0 + aarch64-linux-android,
# NDK 27.1.12297006 and CMake 3.22.1 installed. No models are downloaded here.
python tools/local-ai/prepare.py
./tools/local-ai/build-native.ps1
.\gradlew.bat clean test lint spotlessCheck verifyCoreBoundaries :app:verifyFoundationManifest assembleDebug :app:assembleDebugAndroidTest
```

macOS/Linux:

```sh
python3 tools/local-ai/prepare.py
bash tools/local-ai/build-native.sh
./gradlew clean test lint spotlessCheck verifyCoreBoundaries :app:verifyFoundationManifest assembleDebug :app:assembleDebugAndroidTest
```

The first build downloads development dependencies. This does not add network access to the installed application. Later builds can use Gradle's `--offline` option once the required artifacts are cached.

Open the root directory in Android Studio, sync, and run `app` on an Android API 26+ emulator/device. Alternatively:

```powershell
.\gradlew.bat :app:installDebug
adb shell am start -n com.commontongue.prototype.debug/com.commontongue.prototype.platform.MainActivity
```

The single launcher opens the translator. Settings ? Appearance ? Theme selects a locally remembered style. Settings also contains internal adapter/voice checks and narrow resource setup in debug builds. Existing validated Pass 7 resources are reused without a download. Wi-Fi/cellular may stay enabled. The [signed tester update and physical checklist](docs/PASS_8_VALIDATION.md) provide a phone-only installation path; no ADB, uninstall or data clear is needed.

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. For an unsigned optimized release APK, run `:app:assembleRelease`. No publishing is configured.

Windows environment note: if unit-test Java reports `Could not find or load main class Files`, check PATH for unmatched quotes. This host had one malformed PATH entry; validation removed quotes only in the build process (`$env:Path = $env:Path.Replace('"', '')`), without modifying the machine's saved environment. Use a valid PATH and JDK 17 for normal builds. Local downloaded tools and emulator data are ignored under `.local/`.

## Checks and tests

- `test`: core/ViewModel tests and Android speech unit tests, including offline voice selection, replay, lifecycle and rapid cancellation; no models or device needed.
- `verifyCoreBoundaries`: checks production/test core dependency graphs and imports against neutral JVM allowlists; rejects platform/provider/I/O coupling. Core `check` tasks also run it.
- `lint`: Android lint for the app and both pure JVM core modules, including dependencies; errors fail the build and warnings remain visible. Version-update advisories for the deliberate SDK/toolchain pins are documented in the validation report.
- `spotlessCheck`: one consistent Kotlin/Kotlin DSL formatter plus text whitespace checks. Use `spotlessApply` to format.
- `:app:verifyFoundationManifest`: examines debug/release manifests, enforces SDK 26/36, one launcher, a private inference worker, and approved permissions only. Debug alone permits the pinned test-resource downloader; release has no Internet permission.
- `assembleDebug`: compiles/packages the development application.
- `:app:assembleDebugAndroidTest`: compiles/packages the UI test without claiming it ran.
- `:app:connectedDebugAndroidTest`: runs UI and real installed-voice integration on connected devices. The voice test skips if required voices are absent; a skip is not acceptance. Phone-only voice-check installation/export is available without ADB.

Run instrumentation locally after starting an emulator or attaching a device. Windows uses `.\gradlew.bat :app:connectedDebugAndroidTest`; macOS/Linux uses `./gradlew :app:connectedDebugAndroidTest`. A skipped device test is not a pass. See [Pass 1 validation](docs/PASS_1_VALIDATION.md) for executed results and environment limits.

GitHub Actions runs build, unit tests, lint, formatting, manifest verification, debug/test APK assembly, research tooling tests and unchanged native/model baseline verification. It uploads the Pass 8 translator APK, checksums, requirements and test reports. The direct S25 tester APK uses the existing local signer; an ordinary CI debug signer cannot update that installation. It uses JDK 17 and read-only repository permission. CI downloads no research models; physical acceptance is recorded separately.

## Architecture and privacy

Read [ARCHITECTURE.md](docs/ARCHITECTURE.md), [PRIVACY.md](docs/PRIVACY.md), [MODEL_POLICY.md](docs/MODEL_POLICY.md), and the [architecture decisions](docs/DECISIONS/). Offline translation, ephemeral conversations, replaceable model implementations, low-end phones, and no mandatory account are future product constraints. Pass 1 stores no conversations and makes no network calls.

For isolated Pass 2 reproduction, see [the harness README](tools/offline-feasibility/README.md), [candidate licenses](docs/feasibility/MODEL_CANDIDATES.md), [benchmarks](docs/feasibility/BENCHMARK_RESULTS.md), and [Android evidence](docs/feasibility/ANDROID_RESULTS.md). No model is approved for production. [Pass 3 validation](docs/PASS_3_VALIDATION.md) records local contract/guard tests.

Pass 4 compares dedicated MT, direct local LLM translation, contextual post-editing, and bounded verification/repair using 196 balanced text fixtures. Read the [selection report](docs/quality/PASS_4_QUALITY_BAKEOFF.md), [benchmark specification](docs/quality/BENCHMARK_SPEC.md), and [reproduction commands](tools/translation-quality/README.md). Its model-free schema/scoring/blinding tests run in ordinary CI; large research models and native toolchains remain ignored under `.local/`. All app/core source, model-neutral contracts, permissions and production dependencies remain unchanged. Physical-phone testing and production adapters remain later work.

Pass 5 is accepted on the successful physical Galaxy S25 v7 proof. Use the existing [accepted v7 research APK](https://github.com/Revty79/CommonTongue/releases/tag/pass5-s25-tts-diagnostic-2026-10-08) with the unchanged [test pack](https://github.com/Revty79/CommonTongue/releases/tag/pass5-research-preview-2026-10-08); existing testers keep their installed app/data/pack/voices. Direct and file TTS checks pass in English and Spanish, both real microphone directions reach TTS_COMPLETE, and the prerecorded full pipeline passes. See the [closeout and exact baseline](docs/device/PASS_5_CLOSEOUT.md) and [phone-only setup](docs/device/LOCAL_TEST_PACK_SETUP.md). First post-load EN→ES playback-start evidence is approximately 4,665 ms; broader human/device/sustained validation remains follow-up. Historical crashes remain regression history. APK/models/signing/private exports stay outside Git; the 14 WAV controls are synthetic. The final Pass 5 commit is `5ee45413e3c72493ae3f13ec6f913728828a3e0f`. Pass 6 preserves that native/model research baseline while adding the independent production voice layer; its S25 acceptance must use new results.
