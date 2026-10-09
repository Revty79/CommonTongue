# Pass 5 physical research tooling

This is a separate physical-device spike, not the production translator. The user accepts the successful S25 v7 proof; broader device/human/sustained validation is follow-up. Preserve the existing v7 APK, pack and installed voices. The primary stack remains locked MADLAD400-3B-MT Q4_K / Candle T5 CPU, Whisper Base Q5_1 and installed offline Android English/Spanish voices. The accepted observed engine is com.google.android.tts with en-US/es-US voices, not a hard-coded engine requirement. eSpeak remains an optional research control; OPUS int8 and Whisper Tiny are controls. See [closeout](../../docs/device/PASS_5_CLOSEOUT.md).

## Preferred tester path: install locally on the phone

Use the [phone-only test-pack instructions](../../docs/device/LOCAL_TEST_PACK_SETUP.md): install the research APK, download the single test-pack ZIP, tap **Install Test Pack**, and choose it from Downloads. ADB is no longer an S25 acceptance requirement. The app checks storage, the exact pack/version, every destination hash and the complete ZIP hash before publishing a complete private installation. Offline voices are checked separately. **Export Research Results** saves engineering evidence through Android's file picker without ADB, human recordings or saved human text.

`test-pack.py` creates the deterministic large pack in ignored local artifact storage and its small immutable APK contract. `test-pack.py --verify-contract` checks pinned metadata without reading any weights.

## Developer convenience: one-click Windows USB setup

The Windows USB setup remains an optional developer/tester convenience. The phone-only import above is the preferred S25 path. Research preview and physically accepted final artifacts are clearly distinguished.

1. Download and extract the complete setup ZIP.
2. Connect the authorized phone using a USB data cable, unlock it and approve USB debugging when requested.
3. Double-click **Install Common Tongue Test.bat**. Keep the PC online during setup. The script handles connection tools, pinned downloads, checksums, APK/TTS installation, private model transfers and fixed test fixtures.
4. After “Installation complete,” open the research app, tap **Start Testing**, grant microphone access and wait for **Ready to Speak**. Hold either language button to speak. Radios may be on or off; airplane mode with Wi-Fi/mobile data off is only the explicit offline-proof test condition.

Samsung developer setup, when needed: Settings → About phone → Software information → tap Build number seven times; enable USB debugging in Developer options. The script never changes permanent device settings, radios, unrelated data or bootloader/root state. A USB driver can be necessary on some Windows systems; use the device manufacturer's official driver, not an untrusted driver bundle.

Requirements: Windows PowerShell 5.1+, an authorized ARM64 Android API 26+ device, at least 4.5 GB staging/free phone storage, PC download/cache space and adequate device memory. The installer reports limited available memory and directs one controlled load attempt. Acceptable performance is unknown until measured. It includes the APK and approximately 2 MB of known synthetic WAVs; multi-GB weights are downloaded once and cached alongside setup, never bundled in the ZIP/APK or Git.

The final standalone **CommonTongue-Pass5-Research.apk** is also supplied, with SHA-256 and exact requirements in `build-artifacts.json` / `setup-manifest.json`. **APK-only installation cannot translate.** Install the local test pack; the Windows setup is an optional alternative. Artifacts are research builds, not public production releases. Local and Actions debug builds can have different signing certificates; an incompatible existing research installation is preserved and reported to the coordinator, never automatically erased.

## Coordinator/developer preparation

The following commands are for reproduction and test coordination; testers do not need them. The standalone project retains SDK 26/36/36, AGP 9.4.1, NDK 27.1.12297006 and CMake 3.22.1. Install Rust 1.91.0 and its `aarch64-linux-android` target. Source archives/model digests are inherited from Pass 2/4 and the bounded setup lock.

```powershell
python tools/device-trial/prepare-build.py
rustup target add aarch64-linux-android --toolchain 1.91.0
powershell -NoProfile -ExecutionPolicy Bypass -File tools/device-trial/build-t5.ps1 -Fetch
./gradlew.bat -p spikes/physical-trial --no-daemon assembleDebug verifyResearchManifest lint
python tools/device-trial/verify-apk.py --apk spikes/physical-trial/build/outputs/apk/debug/physical-device-trial-debug.apk --merged-manifest spikes/physical-trial/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml --output .local/device/apk-audit.json
python tools/device-trial/package.py --apk spikes/physical-trial/build/outputs/apk/debug/physical-device-trial-debug.apk --platform-tools-archive .local/device/setup-assets/platform-tools-windows.zip --output .local/device/artifacts
```

The build workflow is dispatch-only, downloads pinned native **sources**, compiles/audits the research APK and uploads the APK/setup ZIP/checksums/commit marker. It does not download translation/ASR models, run inference, use a phone or create a release. Ordinary CI remains model-free, device-free and credential-free. Linux uses the same Rust/NDK/ABI settings as the Windows script. Model provisioning takes place only on the authorized tester's PC/device.

Native builds require the Rust SONAME `libtrial_t5.so`; CMake rejects stale libraries without it. The APK audit validates dynamic dependency closure, basename-only DT_NEEDED/SONAME, eleven JNI exports, four Rust C ABI exports and ARM64/16 KiB alignment. Host tests reproduce the invalid build-host dependency regression and missing dependency/export cases without downloading models. The [version 4 native link update](https://github.com/Revty79/CommonTongue/releases/tag/pass5-s25-native-link-2026-10-08) corrected the linker defect; the current version 5 diagnostic update below preserves that native binary and the imported pack.

`package.py --stage-local-cache` optionally hardlinks already verified local model files into an adjacent ignored cache for development; that cache is explicitly excluded from the ZIP. `prepare-fixtures.py` reproduces synthetic controls using the existing Pass 2 Python environment and built eSpeak source under an OS network restriction. The authoritative committed fixed fixtures are the WAVs and plan in `fixtures/`; regenerating an engine/platform variant must produce a separate plan with observed hashes, not overwrite benchmark expectations.

## Anonymized inventory and evidence

`device.py list` prints anonymous endpoint indices and authorization state. Serials exist only in ephemeral command routing and are never printed, exported or retained. `inventory` is read-only; missing hardware properties remain unknown. For example:

```powershell
python tools/device-trial/device.py list --adb "$env:ANDROID_HOME/platform-tools/adb.exe"
python tools/device-trial/device.py inventory --adb "$env:ANDROID_HOME/platform-tools/adb.exe" --device-index 0 --label device-high-01
python tools/device-trial/device.py collect --adb "$env:ANDROID_HOME/platform-tools/adb.exe" --device-index 0 --label device-high-01
```

Advanced `install`, `provision` and `fixtures` commands exist for a coordinator, but the tester setup handles these automatically. Physical tooling refuses emulator evidence. A marginal/failed device must not be repeatedly loaded. Default collection exports only allowlisted metrics and known fixture outputs. Raw diagnostic strings are redacted. Human text exports require `--include-human-text --reviewed-for-personal-data` and still require actual review/redaction before Git; no human audio is exported. Explicitly saved human text stays private/local until that step.

`schemas.py` validates device profiles, metric snapshots, merged permissions, failure stage and participant ratings. `analyze.py` reports observed counts/percentiles and keeps missing directions empty. Script ratings alone never establish a completed live conversation or all acceptance criteria. Use [human script](human-script.json) and the [physical protocol](../../docs/device/PASS_5_PHYSICAL_DEVICE_TRIAL.md); no qualified human feedback has been collected yet.

```powershell
python -m unittest discover -s tools/device-trial/tests -v
```

The 65 lightweight tests require no model, runtime, device or network. They verify sanitization, permission leakage, pinned assets, transfer safety/checksums, synthetic WAV provenance, original Unicode preservation, human-only ratings, native ELF linkage and translation/TTS failure/metric schemas. Physical microphone/TTS/lifecycle behavior still requires the phone. The T5 Rust registry retains four separate host ownership/stale-handle tests; the research Android project has 40 JVM tests, including TTS WAV format/silence/bounds and safe configuration metadata.

## Privacy and cancellation

No Internet/network-state/storage/camera/location/contact/account permission is added to the research APK. The separate pinned eSpeak APK also reports no requested permissions in the local audit. Every chosen voice must report no network requirement. The physical test must additionally prove success with airplane mode on and radios off; manifest inspection alone is not physical offline proof.

Microphone capture runs only while held in a foreground Activity, capped at 30 seconds. Temporary human WAVs are removed after inference and on cancellation/backgrounding/restart; human text is kept in memory until explicit Save. Metrics avoid source/translation content. The private native process exits on cancellation/backgrounding or lost UI ownership; no native jobs accumulate, and models reload before another turn. Worker/UI PSS and external TTS memory must be distinguished.

See [port/provenance](../../docs/device/ANDROID_PORT.md), [device matrix](../../docs/device/DEVICE_MATRIX.md), [performance](../../docs/device/PERFORMANCE_RESULTS.md) and [human trial](../../docs/device/HUMAN_TRIAL.md). Pass 6 has not begun.

The [accepted v7 research artifact](https://github.com/Revty79/CommonTongue/releases/tag/pass5-s25-tts-diagnostic-2026-10-08) requires no replacement APK or pack import for the existing S25. Four English/Spanish direct/file TTS checks pass; both microphone directions and the prerecorded full pipeline reach TTS_COMPLETE. The accepted file route remains synthesize-to-file → PCM16 WAV → AudioTrack, with all four v6 native libraries and the pack unchanged. [TTS diagnostics](../../docs/device/TTS_DIAGNOSTICS.md) documents the comparison controls, safe exports and actual playback evidence. Keep old crash records as historical evidence; do not assign them to current v7 turns. The final Pass 5 source/CI handoff precedes substantial Pass 6 implementation.
