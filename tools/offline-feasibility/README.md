# Offline feasibility research

This is disposable Pass 2 engineering tooling, not a Common Tongue desktop product or a production AI interface. The production `:app` and `:core:domain` do not depend on this directory or the standalone Android spike.

## Prepare on Windows

Requirements: Python 3.13, existing free Visual Studio C++ build tools, JDK 17, Android SDK platform/build tools 36, NDK 27.1.12297006, and CMake 3.22.1. Tested runtime packages are pinned in `requirements.txt`. Source/model artifacts and SHA-256 values are pinned in `artifacts.lock.json`.

```powershell
py -3.13 -m venv .local/offline-env
.local/offline-env/Scripts/python.exe -m pip install -r tools/offline-feasibility/requirements.txt
python tools/offline-feasibility/prepare.py
$env:ANDROID_HOME = 'C:\Users\YOUR_NAME\AppData\Local\Android\Sdk'
tools/offline-feasibility/build-desktop.ps1
```

Only preparation downloads artifacts. `prepare.py --resolve` is a maintainer refresh that deliberately changes immutable identities/checksums; ordinary reproduction omits it. Files with mismatched hashes fail instead of being accepted. Large assets, native source checkouts, build outputs, WAVs, and local research logs remain under ignored `.local/` or standard ignored Android build directories.

The official eSpeak 1.52.0 Windows MSI executable crashed in this environment. The working baseline compiles the pinned source instead. Piper is the pinned archived 2023.11.14-2 research distribution with separate voice provenance records. Neither engine nor voice is approved for production.

## Run with networking prevented

Use an OS process/network sandbox or firewall that denies outbound networking to the Python executable **and its native children**. The recorded runs used this session's restricted process sandbox; connections to GitHub and Hugging Face returned Windows socket error 10013 before inference. Do not disable networking for an unrelated user's whole computer.

`--offline` installs a Python socket tripwire and disables Hugging Face online/telemetry flags. This alone cannot prove native DLLs/child executables cannot access a network. `--require-network-unavailable` also refuses a run if either preflight network canary succeeds. The recorded proof combines the OS restriction, rejected canaries, and zero post-preflight Python socket attempts. It is not a packet capture of every native syscall.

```powershell
.local/offline-env/Scripts/python.exe tools/offline-feasibility/harness.py generate-audio --offline --require-network-unavailable --tts piper --output .local/feasibility-results/piper
.local/offline-env/Scripts/python.exe tools/offline-feasibility/harness.py benchmark --offline --require-network-unavailable --tts piper --output .local/feasibility-results/piper
.local/offline-env/Scripts/python.exe tools/offline-feasibility/harness.py pipeline --offline --require-network-unavailable --wav .local/feasibility-audio-piper/en01-clean.wav --direction en-es --asr tiny --tts piper --output .local/single-en-es
.local/offline-env/Scripts/python.exe tools/offline-feasibility/harness.py pipeline --offline --require-network-unavailable --wav .local/feasibility-audio-piper/es01-clean.wav --direction es-en --asr base --tts piper --output .local/single-es-en
```

For the formant baseline, use `--tts espeak` and a distinct output directory. `--translator romance` selects the compact multilingual English-to-Romance alternate for EN→ES only. ASR accepts `tiny` or `base`, both multilingual Q5_1; threads default to four. Translation uses greedy decoding without a KV cache, suppresses padding, and fails on limits instead of claiming truncated output is complete. Model paths are local and missing files fail; inference has no model downloader or cloud fallback.

Each benchmark has 32 unique text cases: 16 per direction, and 16 extra EN→ES outputs for the alternate. Six clean synthetic WAVs plus two deterministic 10 dB Gaussian-noise versions run through both ASR models: 16 speech pipelines per TTS fixture set. Synthetic speech does not establish natural-speaker quality, accent coverage, background-noise robustness, or fluent reviewer approval. Per-case important meaning, source, output, ASR word error rate, timing, approximate process-tree memory, CPU use, configuration, and offline observations are in JSON. Python RSS sampling includes the parent and native subprocesses; shared pages can be double-counted and it is not the same as Android PSS.

## Android proof

The standalone Gradle project is `spikes/offline-feasibility`; it is deliberately outside normal root project configuration. It uses SDK 26/36/36 and builds `arm64-v8a` and `x86_64`. Native libraries explicitly use Release optimization inside the debug APK, with CPU-only generic settings. SentencePiece's native build fetches free Abseil 20260526.0 during preparation/build (recorded commit in the candidate document); complete builds before the offline inference test. Normal CI runs foundation checks and six stdlib research tests without downloading research models or configuring the spike.

```powershell
./gradlew.bat -p spikes/offline-feasibility --no-daemon assembleDebug verifyOfflineManifest
tools/offline-feasibility/run-android.ps1 -Serial emulator-5554 -Asr tiny -Fixture piper
# Wait for PROOF_RESULT / PROOF_FAILED in adb logcat -s OfflineProof.
python tools/offline-feasibility/transfer-android.py --adb "$env:ANDROID_HOME/platform-tools/adb.exe" --serial emulator-5554 --asr tiny --collect .local/android-tiny.json
```

The script installs the independently distributed GPL eSpeak research TTS engine and the spike APK locally. It disables emulator Wi-Fi/mobile data and enables airplane mode on the tested API 36 image. Use a dedicated development emulator; do not point this command at a personal phone without deliberately accepting those changes. The API 26 image's Wi-Fi shell command failed; its separately executed minimum-SDK proof and emulator-only network-disable procedure are documented in [Android results](../../docs/feasibility/ANDROID_RESULTS.md).

`transfer-android.py` stages each file under a unique `/data/local/tmp/` name, copies it with the debug `run-as` account into app-private files, verifies its device SHA-256, and removes only that temporary staging file. This avoids Android shared-storage ownership problems and Windows raw-stdin transfer truncation/hangs. No storage permission, model downloader, Internet permission, microphone capture, or production UI integration is needed. Transfers require a debuggable APK.

The spike removes ONNX Runtime 1.30.0's transitive Internet/network-state permissions and telemetry ContentProvider, and calls `setTelemetry(false)`. Its manifest guard requires zero permissions/providers and the prescribed SDKs. Verify the packaged APK with `aapt2 dump permissions` as well. It uses real WAV→Whisper→Marian/ONNX→Android TTS file synthesis in both directions. The selected `en`/`es` voices must report `isNetworkConnectionRequired=false`; unavailable voices or synthesis failures fail the proof. This is a separately installed interim voice engine, not proof those voices are bundled into Common Tongue.

## Lightweight verification

```powershell
python -m unittest discover -s tools/offline-feasibility/tests -v
./gradlew.bat clean test lint spotlessCheck :app:verifyFoundationManifest assembleDebug :app:assembleDebugAndroidTest
```

See [Pass 2 report](../../docs/feasibility/PASS_2_OFFLINE_FEASIBILITY.md), [model records](../../docs/feasibility/MODEL_CANDIDATES.md), [benchmark observations](../../docs/feasibility/BENCHMARK_RESULTS.md), and [Android evidence](../../docs/feasibility/ANDROID_RESULTS.md). No production capability interface or Pass 3 work is implemented.
