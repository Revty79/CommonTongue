# Pass 2 Android results

**PASS: real WAV → local Whisper → local OPUS-MT/ONNX → local Android TTS audio completed in both directions.** Tiny and Base ran on API 36; Tiny also ran at the unchanged API 26 minimum. Physical phones were UNAVAILABLE. arm64-v8a compiled and packaged successfully but was NOT RUN on physical hardware.

## Environment and exact stack

Windows host: Ryzen 5 5600G, 6 cores/12 threads, 31.4 GiB physical RAM. Dedicated headless Android emulators use four virtual CPUs, configured 2 GiB RAM, hardware acceleration, and software graphics. API 36 reports 2,013,632 kB MemTotal. Its image is `android-36.1/google_apis_playstore/x86_64`, revision 3; Android reports SDK 36 / `sdk_gphone64_x86_64`. API 26 uses `android-26/google_apis/x86_64`, reporting `Android SDK built for x86_64`.

Actual inference ABI is **x86_64** in all executed runs. API 36's `Build.SUPPORTED_ABIS` also lists arm64-v8a because of its native bridge; that does not mean this proof executed arm64 inference. JNI reports the actual compiled ABI and optimization setting.

Standalone project: [spikes/offline-feasibility](../../spikes/offline-feasibility/build.gradle.kts), outside the root production build. AGP 9.4.1 built-in Kotlin, JDK 17, SDK min/compile/target **26/36/36**, NDK **27.1.12297006**, CMake **3.22.1**. Native inference compiles as **Release** inside a debuggable research APK, CPU only, four threads, generic CPU settings, no GPU or cloud backend. Native `GGML_NATIVE`, OpenMP and x86 AVX/AVX2/FMA/F16C tuning are off. Debug APK status alone does not describe native optimization.

Whisper runtime **1.9.5**, multilingual Tiny/Base **Q5_1**; greedy best-of 1, no temperature fallback, explicit source language, transcription only. Marian **OPUS-MT EN→ES / ES→EN**, publisher-quantized ONNX split encoder/decoder, **ONNX Runtime Android 1.30.0**, **SentencePiece 0.2.2**, four intra-op threads/one inter-op thread, greedy full-sequence decoder without KV cache. Input/output limits fail rather than silently truncate. Exact source/model revisions, files, sizes, SHA-256 and licenses are in the [candidate record](MODEL_CANDIDATES.md) and [lock](../../tools/offline-feasibility/artifacts.lock.json).

TTS: separately installed official **eSpeak NG 1.52.0** Android app, `com.reecedunn.espeak`, GPL-3.0-or-later. Selected voices `es` (locale `es`) and `en-us` (locale `en-US`) each reported `requires_network=false` and produced WAV files locally. This engine is an interim research baseline; it sounds robotic and does not establish bundling rights or product voice quality. No neural voice weights were placed in the Android app.

## Executed performance

Single phrase per direction per configuration; input durations 3.52 seconds EN / 3.96 seconds ES, generated locally with research Piper voices. These are compatibility/feasibility measurements, not a phone benchmark or steady-state distribution.

| Emulator / ASR | Cold ASR load ms | Direction | ASR inference ms | Cold MT load ms | MT inference ms | TTS synthesis ms | Pipeline ms |
| --- | ---: | --- | ---: | ---: | ---: | ---: | ---: |
| API 36 / Tiny | 313.2 | EN→ES | 2966.2 | 2294.3 | 1073.9 | 22.3 | 6439.3 |
| API 36 / Tiny | shared loaded model | ES→EN | 2588.9 | 1015.6 | 1030.6 | 7.6 | 4701.0 |
| API 36 / Base | 399.8 | EN→ES | 6133.7 | 1474.6 | 1032.2 | 30.3 | 8773.7 |
| API 36 / Base | shared loaded model | ES→EN | 5400.4 | 1163.6 | 1264.6 | 8.0 | 7898.0 |
| API 26 / Tiny | 157.2 | EN→ES | 3740.8 | 1305.2 | 1492.5 | 25.4 | 6721.6 |
| API 26 / Tiny | shared loaded model | ES→EN | 2897.7 | 1084.0 | 1151.3 | 9.8 | 5229.8 |

Pipeline includes ASR inference, cold translator creation, translation, output-file TTS and intervening work. It **excludes** one-time ASR load, initial Android TTS service binding/initialization, and input WAV parsing. Adding the ASR load gives the measured first-phrase portion of startup, not complete launch-to-audio latency. TTS engine/voice selection occurs before its timing; external eSpeak process initialization and its CPU are excluded. Separate Android TTS cold-load time is UNAVAILABLE. Synthesis writes a file; audio playback/speaker latency is NOT RUN.

The first unoptimized native Debug run also passed, but took **39.8/40.1 seconds** with Tiny. [Its raw evidence](evidence/android-api36-unoptimized-tiny.json) is retained. Rebuilding native libraries with optimization reduced the subsequent runs substantially; these were separate single runs, not a controlled optimization experiment. No claim about real-phone speed follows from either run.

## Memory, CPU and storage

| Configuration | ASR-loaded PSS MiB | Sampled PSS at MT load MiB (EN / ES) | Process RSS high-water MiB | End PSS after closing models MiB |
| --- | ---: | ---: | ---: | ---: |
| API 36 / Tiny | 115.8 | 369.0 / 369.7 | 721.6 | 129.3 |
| API 36 / Base | 154.9 | 416.6 / 414.7 | 684.0 | 139.1 |
| API 26 / Tiny | 83.7 | 349.9 / 369.1 | 642.6 | 578.7 |

PSS comes from `Debug.MemoryInfo` at boundaries, **not continuous peak sampling**. RSS high-water is kernel `VmHWM` across the entire process run, including inference allocations; it is not per-stage PSS. API 26 retained much more memory after closing sessions than API 36; allocator/runtime retention warrants physical-device investigation. `Debug.getNativeHeapAllocatedSize()` is recorded separately and can include allocations that are not resident; it must not be mistaken for physical RAM.

Separate eSpeak service sampled idle PSS **25.9 MiB**, RSS **98.7 MiB** on API 36. These are not its synthesis peak. The app's CPU totals exclude that service. App process CPU per phrase was **15.84/14.71 CPU-seconds** Tiny and **28.76/26.26 CPU-seconds** Base on API 36, roughly 2.5–3.3 busy CPU cores over the measured pipeline. Four inference threads impose substantial CPU work. Heat, battery and sustained throttling are **UNAVAILABLE** without a physical phone. No OOM or native crash occurred in completed inference runs.

Required local runtime assets for both directions:

| Item | Bytes | MiB |
| --- | ---: | ---: |
| EN→ES runtime pack (six files) | 116,090,916 | 110.7 |
| ES→EN runtime pack (six files) | 116,090,881 | 110.7 |
| Tiny Q5_1 | 32,152,673 | 30.7 |
| Base Q5_1 | 59,707,625 | 56.9 |
| Both translators + Tiny | 264,334,470 | 252.1 |
| Both translators + Base | 291,889,422 | 278.4 |
| Research APK, two ABIs, no model assets | 82,974,032 | 79.1 |
| Separate eSpeak APK | 10,446,659 | 10.0 |

APK download bytes are not installed storage: extraction, dex/native libraries, both-ABI packaging, model copies, generated audio and filesystem overhead add space. A physical single-ABI distribution would differ and was NOT BUILT as a shipping pack. Testing both ASR models leaves both weights locally; the provisional configuration footprints above count one selected ASR. Piper voice weights are desktop-only in this Android proof.

The formatted APK used on API 26 has SHA-256 `1375cd934a64af3c6c6a1e85dfa7d91c173df10fe3fae62168d3eca8b88e3a24`. API 36 optimized runs used pre-format APK `3c1551763bc56be696d7fea0f73805a3b3335ffb2e12bf3f191a289c4a9f92a5`; formatting changed packaged Kotlin line metadata, with the same native binaries/model/decoding configuration. Full inventory is in [environment.json](evidence/environment.json).

## Offline proof and reproducibility

The spike's debug **and release merged manifests passed a guard requiring zero requested permissions, zero ContentProviders, minSdk 26 and targetSdk 36**. `aapt2 dump permissions` on the packaged debug APK showed the package and no requested permissions; badging showed min 26, target 36 and both ABIs. It has no Internet, network-state, microphone or storage permission. The independent eSpeak APK also requests no permissions.

ONNX Runtime's AAR adds Internet/network-state permissions and a telemetry provider by default. The spike explicitly removes these transitive declarations and calls `OrtEnvironment.setTelemetry(false)` before sessions. Root production permissions/dependencies are unchanged. Model inference uses only app-private cached files; neither adapter has an HTTP client/model downloader/cloud fallback.

On API 36, Wi-Fi/mobile data were disabled with `svc`, airplane mode enabled with `cmd connectivity`, `ip route` returned no routes and `dumpsys connectivity` reported **Active default network: none**. Both TTS voices explicitly rejected a network requirement. ASR, translation and WAV synthesis succeeded under that state.

API 26's `svc wifi disable` command exited abnormally on this emulator image, and its protected airplane broadcast rejected shell UID. For the **dedicated developer emulator only**, `adb root` succeeded; `ip link set wlan0 down` and `ip link set radio0 down` removed all routes before inference. Framework connectivity metadata can remain stale, so the proof relies on disabled links/empty routes plus zero permissions in both apps. Root was used only to control this old test image's network; inference ran as the regular app UID. Physical-device rooting is neither required nor recommended by this spike.

Reproduce downloads/builds/runs using the [harness README](../../tools/offline-feasibility/README.md). The normal launcher targets the API 36 workflow. For API 26, install both APKs, initialize eSpeak, disable networking as above on a root-capable dedicated image, run `transfer-android.py --serial emulator-5556 --asr tiny`, then start `.SpikeActivity --es asr tiny`. Use `--collect` to retrieve JSON. The binary-safe transfer verifies device SHA-256 for all 15 files and removes its unique shell staging files. Raw [Tiny transfer hashes](evidence/android-transfer-tiny.json) and [Base transfer hashes](evidence/android-transfer-base.json) match the pinned model files and fixture hashes.

Raw completed runs: [API 36 Tiny](evidence/android-api36-tiny.json), [API 36 Base](evidence/android-api36-base.json), [API 26 Tiny](evidence/android-api26-tiny.json). Each includes actual transcripts, translations, voice names, output WAV sizes, per-stage timings, CPU and memory. No WAV/model/APK is committed.

## ABI coverage and limits

| ABI | Finding | Execution status |
| --- | --- | --- |
| arm64-v8a | NDK compiled Whisper/SentencePiece/JNI; ORT and eSpeak supply compatible libraries; packaged native spike library 4,611,984 bytes | BUILD PASS; physical execution NOT RUN |
| x86_64 | Packaged native spike library 4,837,720 bytes; ORT/eSpeak available | RUN PASS on API 26 and 36 |
| armeabi-v7a | ORT AAR and eSpeak APK supply this ABI; whisper.cpp has upstream 32-bit Android examples. Spike needs another native build plus memory/address-space/device testing | NOT BUILT / NOT RUN; practical Lite support UNKNOWN |

The ORT AAR minimum is 24, so the prescribed minSdk 26 is preserved. Compilation establishes an arm64 path, not phone performance or full device compatibility. Two short deterministic phrases do not establish sustained conversation reliability, microphone capture, speaker playback, or latency/quality across accents. The current memory use makes a 2–3 GiB low-end phone path uncertain.

The research build/lint/manifest checks passed with zero lint errors. Visible warnings include deliberate SDK/dependency pins, research UI literal text/no icon, version-catalog advice and backup configuration advice. These warnings are recorded rather than suppressing lint broadly. Earlier integration failures (JNI include path, Java tensor cast, Android file ownership and raw stdin transfer behavior) were corrected in the isolated spike; production code was not changed.
