# Pass 1 validation

Validated October 7, 2026, in `D:\CommonTongue` on Windows. Pass 1 contains native Android foundation work only. Translation, AI interfaces, AI models/runtimes, cloud services, accounts, conversation storage, and Pass 2 work are absent.

## Selected configuration

- Project: `common-tongue`; modules: `:app` and `:core:domain` only.
- Android Gradle plugin 9.4.1; Gradle 9.6.0; Kotlin/Compose compiler 2.4.20.
- Compose stable BOM 2026.06.01: Compose UI/foundation/runtime 1.11.4; Material 3 1.4.0.
- Activity Compose 1.12.4; Lifecycle 2.10.0; coroutines 1.11.0.
- JUnit 4.13.2; AndroidX test JUnit 1.3.0; runner 1.7.0; Espresso core 3.7.0.
- Spotless 8.10.3; ktfmt 0.64; actionlint 1.7.12 for local workflow validation.
- JDK: Temurin 17.0.20.1+1; Java/Kotlin JVM toolchain target 17.
- minSdk **26**; targetSdk **36**; compileSdk **36**; SDK Build Tools 36.0.0.
- Temporary application ID: `com.commontongue.prototype`; debug suffix `.debug`.
- Normal debug signing only. Release APK is deliberately unsigned, with R8/resource shrinking enabled.

See [toolchain selection](TOOLCHAIN.md) for dependency purposes, stable-release sources, and compatibility constraints. The pure domain runtime graph contains Kotlin standard library 2.4.20 and JetBrains annotations 13.0, with no Android, Compose, network, or AI runtime dependency. Both production runtime graphs were inspected; no AI/model, network-client, cloud, analytics, or authentication SDK is present. Test tooling is confined to test configurations.

## Executed checks

| Check | Final result |
| --- | --- |
| `clean` | PASS; executed during the full clean-build validation |
| `test` | PASS; 3 JVM tests, 0 failures/errors/skips |
| Domain unit test | PASS; 1 test verifies an absent engine cannot translate |
| Foundation ViewModel unit tests | PASS; 2 tests verify initial state and immediate StateFlow delivery |
| `lint` | PASS; app has 0 errors and 7 version-update warnings; domain has 0 issues |
| `spotlessApply` / `spotlessCheck` | PASS; Kotlin, Kotlin DSL, and configured project text |
| `:app:verifyFoundationManifest` | PASS; debug/release permissions and SDK boundaries |
| `assembleDebug` | PASS; native debug APK built |
| `:app:assembleDebugAndroidTest` | PASS; instrumentation APK compiled and packaged |
| `:app:assembleRelease` | PASS; unsigned R8-optimized, resource-shrunk APK built |
| `:app:connectedDebugAndroidTest` | PASS; 1 Compose startup smoke test on each of 2 emulators, 0 failures/errors/skips |
| Dark mode with system font scale 2.0 | PASS; same UI smoke test executed directly through AndroidJUnitRunner on current Android, `OK (1 test)` |
| Device installation/startup | PASS; actual Activity installed/launched on API 26 and API 36 |
| Screenshot inspection | PASS; expected content inspected in light mode on both devices and dark mode with large text |
| Runtime dependency reports | PASS; app and pure-domain production configurations inspected |
| AAPT APK permission/SDK inspection | PASS; packaged debug/release APKs match manifest/configuration |
| GitHub Actions workflow static validation | PASS; actionlint 1.7.12, exit 0; ShellCheck was not available/run |

The final build/check invocation completed with `BUILD SUCCESSFUL`:

```powershell
.\gradlew.bat --no-daemon --console=plain --warning-mode=all test lint spotlessCheck :app:verifyFoundationManifest assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
```

`clean` was executed in the preceding clean build; formatting and test-harness corrections were followed by the final successful checks above. Device smoke testing completed separately with `BUILD SUCCESSFUL`:

```powershell
.\gradlew.bat --no-daemon --console=plain --warning-mode=all :app:connectedDebugAndroidTest
```

The extra dark/large-font smoke used `adb -s emulator-5554 shell am instrument -w -r -e class com.commontongue.prototype.ui.foundation.FoundationScreenTest com.commontongue.prototype.debug.test/androidx.test.runner.AndroidJUnitRunner` after setting system night mode and font scale 2.0. This checks actual Android configuration rather than only a preview.

## Device coverage and limits

Android Emulator 36.2.12 with usable AEHD acceleration ran two project-local AVDs:

- `foundation_api26`: Android 8.0.0 / API 26, Google APIs x86_64 image revision 16.
- `foundation_api36`: Android 16 / API 36, installed 36.1 Google Play x86_64 image revision 3.

Wi-Fi and mobile data were disabled for device validation. Both devices rendered all four expected strings. The API 36 device also passed dark mode and 2x system font scaling. No instrumentation test was skipped or treated as passing without execution.

No physical OEM device, TalkBack session, Android Studio interactive launch, or remote GitHub Actions run was exercised. These are unverified coverage, not failures. Unsigned release assembly was verified; an unsigned release APK was not installed/launched. This is foundation validation, not translation quality, real-device low-end performance, or human acceptance of a finished product. No device benchmarking/profiling or tier execution was implemented.

## Final manifest permissions

The app's merged manifests and packaged APKs request exactly:

| Build | Requested permission |
| --- | --- |
| Debug | `com.commontongue.prototype.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` |
| Release | `com.commontongue.prototype.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` |

AndroidX declares these as **signature** permissions to protect internal receivers. They do not provide Internet, microphone, camera, storage, or account access. `android.permission.INTERNET`, `android.permission.RECORD_AUDIO`, and `android.permission.CAMERA` are absent from both application manifests/APKs. No runtime permission prompt is needed.

The AndroidX profile-install receiver has `android:permission="android.permission.DUMP"`, which restricts its callers; the app does **not** request DUMP. The separate instrumentation APK requests `android.permission.REORDER_TASKS` for the test harness. It is not an application permission and is not bundled into the shipped debug/release app. The test APK also has no Internet/microphone/camera permission.

## Warnings and concerns retained

Android lint warnings are visible, not suppressed or baselined. Its seven app warnings are:

1. `OldTargetApi`: API 37 is newer than the explicitly required target 36.
2. `AndroidGradlePluginVersion`: Gradle 9.8.1 is available; 9.6.0 is the documented AGP 9.4 default and remains in Kotlin's fully supported Gradle range.
3. `GradleDependency`: compileSdk 37 is newer than the explicitly required 36.
4. `GradleDependency`: newer Compose BOM 2026.09.00 needs compileSdk 37 through its UI artifacts.
5. `GradleDependency`: newer Activity 1.13.0 needs compileSdk 37.
6. `GradleDependency`: newer Lifecycle runtime Compose 2.11.0 needs compileSdk 37.
7. `GradleDependency`: newer Lifecycle ViewModel Compose 2.11.0 needs compileSdk 37.

The SDKs were preserved and the newest compatible stable UI versions selected by inspecting AAR metadata. Normal lint errors still fail the build. The domain's standalone Android lint plugin reports zero issues and adds no Android runtime dependency.

Gradle reports that `libandroidx.graphics.path.so` could not be stripped and is packaged as provided in debug/release. It is an AndroidX UI dependency, not an AI runtime. Both APK builds, R8/resource shrinking, and debug launches/tests passed. This nonfatal symbol-stripping concern remains recorded for release-tooling review; no native model integration or special AI keep rules were added.

The emulators emitted unsupported host CPU-feature messages (XSAVE/AVX) and an initial missing emulator-update INI warning. They booted and completed tests with software graphics. The sandbox initially denied the acceleration probe; an authorized tooling run confirmed AEHD usable.

Kotlin 2.4.20's published fully tested AGP range ends at 9.3.1; its documentation permits newer AGP, and current stable AGP 9.4.1 passed this foundation's compilation, lint, tests, and packaging. This result does not prove compatibility with every future plugin/runtime. Monitor the toolchain as later passes add capabilities.

The application ID and product name remain provisional. There is no production signing or publication configuration.

## Resolved failures during setup

- AGP's disabled generated-resource feature: moved the development launcher label into a debug resource overlay.
- Newest AndroidX requiring compileSdk 37: pinned compatible stable releases rather than changing SDK requirements.
- Windows Java test launcher failed with `Could not find or load main class Files`: traced to an unmatched quote in host PATH copied into `java.library.path`. Quotes were removed only in the build process; saved machine PATH was not edited. A current checksum-verified Temurin JDK was installed under ignored `.local/`.
- API 27 navigation-bar theme attribute in a minimum-26 resource: removed it; Activity edge-to-edge setup handles system bars.
- Backup warnings: configured `allowBackup=false`, `fullBackupContent=false`, and explicit Android 12+ cloud/transfer exclusions. No persistence was created.
- Deprecated Compose test rule: used the stable v2 rule API.
- API 26 test APK installation rejected an existing app: configured supported reinstall option `-r`.
- Current Android UI test failed in Espresso 3.5.0's input-manager lookup: explicitly pinned stable test-only Espresso 3.7.0. The subsequent smoke tests passed on both devices.
- Command-line setup encountered Windows batch/JDK-path and SDK-root issues; the official tools were installed in the SDK and the current Android CLI used directly with `--no-metrics` for the API 26 image download. No app telemetry was added.
- Regenerating the Windows wrapper while executing it produced a transient batch `r` error; the generated wrapper then ran all subsequent checks successfully.

Initial failures are not counted as passing tests. Relevant diagnostic logs, final Gradle logs, dependency reports, UI reports, and captured screenshots remain untracked under ignored `.local/` or standard module `build/` output.

## Build artifacts

| Artifact | Path | Size |
| --- | --- | --- |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` | 11,503,877 bytes |
| Unsigned optimized release APK | `app/build/outputs/apk/release/app-release-unsigned.apk` | 852,245 bytes |
| UI-test APK | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 1,050,886 bytes |

SHA-256 values for these validated artifacts:

```text
debug:   1cf48e3546d52ac0fe63c3997799c4b7db6a133db6a842c68fb01c3e8eae1be8
release: 19cec63faecef3611ec84a1c3cb87d86a1d9e185876f2d4889c7ec51037ee81e
test:    f69002f5d1226800970e9e4bc6583c367f8e0fbbee499cf740bf8a2b2df21e60
```

No APK, local tool archive, emulator data, keystore, SDK path, or credential belongs in Git. The existing repository's remote configuration was preserved; no push, remote CI invocation, publication, or Pass 2 action was performed. The final local commit hash and clean-tree check are reported in the completion message; this document belongs to that one foundation commit.

An initial snapshot of this same Pass 1 source appeared during validation. The local `main` history was consolidated into one complete foundation commit, with the prior snapshots retained under `refs/backup/pass1-pre-consolidation` for recovery. No unrelated source or remote branch was changed.
