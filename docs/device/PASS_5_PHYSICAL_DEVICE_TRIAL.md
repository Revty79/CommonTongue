# Pass 5 physical-device and human conversation trial

Status: **PASS 5 ACCEPTED ON PHYSICAL S25 v7 PROOF**. The user-reviewed v7 result passes direct/file TTS checks in English and Spanish, real microphone EN→ES and ES→EN through TTS_COMPLETE, and the prerecorded full pipeline. Preserve 0.0.7-pass5-research and its unchanged pack/runtime. See [final closeout](PASS_5_CLOSEOUT.md) and [accepted baseline evidence](../../tools/device-trial/evidence/s25-v7-acceptance.json). Earlier sections/receipts describe historical corrections and publication-time pending states. Broader devices, conversations, sustained testing and characterization are follow-up validation. Substantial Pass 6 implementation follows the requested final commit/CI handoff; Pass 7 is not authorized.

## Prepared research stack

The separate `spikes/physical-trial` Android project uses the exact locked MADLAD400-3B-MT `model-q4k.gguf`, `config.json` and `tokenizer.json` from Google revision `fa184c675da0b5c9e1c8694fccd4e12e2d422094`. There is no format conversion, re-quantization, replacement model, cloud fallback or trusted semantic retry gate.

The Rust C ABI port uses Candle 0.11.0 commit `31f35b147389700ed2a178ee66a91c3cc25cc80d`, CPU only, both thread pools set to four. It preserves the Pass 4 language prefix, tokenizer configuration, 512-source-token cap, greedy seed/configuration, KV reset, start/EOS IDs and 128-generation-token limit. The ABI shim serializes access inside a private Android process. A capped output is an error, not a completed translation. ARM64 cross-compilation succeeds; that alone does not prove Android execution.

Whisper Base Q5_1 is the primary local ASR configuration, with Tiny available as a research comparison. The two locked ONNX int8 OPUS translators remain a control, with the same greedy no-KV implementation and Pass 4 128-token cap. The phone's installed default TTS engine must supply English and Spanish voices that explicitly report no network requirement. The separately installed pinned eSpeak NG 1.52.0 research app remains an optional engine; no system voices are redistributed or selected for production. The optional Qwen hybrid was not needed for the accepted S25 proof and remains unimplemented.

The research APK requests **RECORD_AUDIO only**. Debug and release merged manifests pass the permission/provider guard. Packaged native libraries and uncompressed ZIP entries pass the 16 KiB alignment audit. The APK imports one already-downloaded, pinned test pack into app-private storage using Android's file picker and verifies the destination/whole-package SHA-256. Developer ADB transfer remains optional. Production app/core source, permissions and model-neutral contracts remain unchanged.

## Historical preparation evidence

Local foundation validation passes `clean`, `test`, `lint`, `spotlessCheck`, `verifyCoreBoundaries`, `:app:verifyFoundationManifest`, `assembleDebug` and `:app:assembleDebugAndroidTest`. The 101 foundation JVM tests and 87 model-free research harness tests pass; the standalone importer also has 13 small model-free transaction tests. The separate research APK also passes build, merged-manifest verification and lint; Windows installer argument/path checks and workflow syntax validation pass. [Preparation build evidence](../../tools/device-trial/evidence/preparation-build.json) records the current local APK/setup ZIP hashes and exact runtime requirements. Those hashes describe a preparation build, not a physically accepted final artifact; remote Actions and physical/human testing remain pending.

The [research installation preview](https://github.com/Revty79/CommonTongue/releases/tag/pass5-research-preview-2026-10-08) was published on 2026-10-08. All nine asset byte sizes and GitHub SHA-256 digests match the local artifacts; anonymous download checks for the APK and single pack return HTTP 200. [Publication evidence](../../tools/device-trial/evidence/preview-artifacts.json) records URLs and hashes. The tag identifies the approved Pass 4 baseline; this preparation APK uses locally validated, uncommitted Pass 5 working source. The final Pass 5 source commit awaits available physical/human trials. The preview is a prerelease and is not marked latest.

The Windows C ABI control executes all 14 fixed text regressions offline. **13/14 outputs match Pass 4 byte for byte; all 14 complete.** The default portable desktop build changes es04 from “Do not start the engine until you remove the wires to pass the power.” to “Do not start the engine until you remove the power wires.” Both retain the prohibition but fail to name jumper cables clearly. This is documented phrasing divergence, not a corrected gold or physical-phone result. The Pass 4 worker used x86-64-v3; this control uses the default portable x86 target and thin LTO. Android ARM floating-point/kernel behavior must be checked separately.

Fourteen known synthetic WAV controls cover ordinary conversation, negation, numbers, automotive, construction, ranch and Mexican slang/idiom intent, seven per direction. Their sources are original Pass 2 regressions, synthesized locally by the pinned eSpeak source and resampled using the Pass 2 method. They are approximately 2 MB total; committed WAVs are **synthetic controls, never participant recordings**. The corresponding text checks preserve Pass 4 outputs as deterministic references, not linguistic golds.

## Original trial protocol and follow-up execution order

1. Have the tester install the research APK normally and download the single test-pack ZIP. The app records an allowlisted anonymous inventory before model loading. No development connection is required.
2. Follow [phone-only deployment](LOCAL_TEST_PACK_SETUP.md): **Install Test Pack**, choose the downloaded ZIP, wait for validation, and prepare offline voices if requested. No radio settings are changed by tooling. Developer ADB/Windows deployment remains optional.
3. Ask the tester to turn mobile data OFF, airplane mode ON and Wi-Fi OFF. Confirm the settings observations and the physical screen. A Wi-Fi-only device records absence of telephony instead of assuming a missing mobile-data setting is enabled.
4. On the S25, load MADLAD + Whisper Base once. On an older device, inspect RAM/free memory/storage first; a marginal device receives one controlled attempt. After allocation failure/OOM/OS kill, stop and record it. Do not change system memory settings or repeatedly retry.
5. Run fixed WAV/text checks before human trials. Compare exact-text outputs with both preserved desktop outputs and document divergences. Separate recognition errors from translation errors. Capture load, first/warm latency, memory and offline TTS behavior.
6. Complete the scripted microphone trial and, when a fluent Spanish participant is available, a short natural bilingual conversation. Follow [human trial](HUMAN_TRIAL.md). Record participant ratings, not inferred human approval.
7. Run an S25 sustained 20–30-turn or 10–15-minute session. Record battery/temperature/thermal status and memory progression. Stop if the tester reports uncomfortable heat.
8. Test cancellation, background/foreground, idle screen off/on, Activity recreation, process restart and model reload. Cancellation/backgrounding exits the private inference process and requires a reload; it does not pretend Candle's blocking tensor calls cooperate.
9. Inventory and test an older device if provided. OPUS success is a stack comparison, not an acceptable quality tier. Document unavailable hardware/participants as PARTIAL.

## Historical S25 correction and diagnostics

The corrected APK removes radio requirements from model loading, microphone capture, inference, fixture controls and TTS. Local translation works with Wi-Fi/cellular on or off. Settings observations remain visible and recorded; only an explicit offline-proof trial with the required radio conditions counts as that proof. No Internet permission or network voice is added.

The UI now distinguishes **Pack Installed → Loading Models → Ready to Speak**. A pack receipt survives restart, but live model handles require a new load. Language/fixture/replay controls remain disabled until a successful worker Ready event and are disabled during another turn. Microphone permission is requested before starting the expensive load, so the permission dialog cannot abandon a newly loaded worker. Completion is delivered only after the worker releases its busy slot, avoiding a race with immediate fixture requests.

The actual loaders and diagnostics share one resolver: MADLAD uses `files/test-packs/installed/models/madlad/{model-q4k.gguf,config.json,tokenizer.json}` and Whisper uses `files/test-packs/installed/models/whisper/ggml-base-q5_1.bin` (or the pinned Tiny control). An incomplete imported directory cannot silently select legacy developer paths. The worker reports destination hash checks, JNI/ARM64 runtime verification, translation loading and ASR loading separately; errors and process exits retain the last stage. The no-ADB export includes known relative filenames/presence/sizes and finite failure codes, with raw errors/absolute paths/human text omitted.

The update keeps the application ID, signing certificate and exact pack contract. Do not uninstall, clear app data or redownload the 2 GB pack for this correction. The received version 3 diagnostic establishes pack persistence and successful destination hash verification; it isolates the remaining failure to native linking before models load. The subsequent version 4 export verifies native/model loading; its post-capture worker exit is covered below.

The [S25 APK update](https://github.com/Revty79/CommonTongue/releases/tag/pass5-s25-correction-2026-10-08) is published as a research prerelease. [Correction evidence](../../tools/device-trial/evidence/s25-correction.json) records the user report, source findings, update compatibility, 20 passing importer/state tests, 42 passing research-tool tests, successful foundation checks and uploaded hashes. APK SHA-256: `c8023d9aca109e6a83b9428f82136723ed656fe3d456402d5955536730a43fc7`. The tag still identifies the approved older baseline; the APK uses the locally validated Pass 5 working source pending the one final commit.


## Historical S25 native linker correction

The on-device version 3 export confirms Android 16/API 36, actual ARM64 worker execution and an intact private pack, followed by NATIVE_LINK_FAILED / UnsatisfiedLinkError. Static ELF inspection finds all four expected ARM64 shared libraries and correct JNI/Rust exports, but the Rust library has no SONAME and the JNI library records its absolute build-host path in DT_NEEDED. This explains failure at System.loadLibrary before Candle or Whisper reads model files.

Version 4 adds the Rust SONAME on both build platforms, checks it at CMake configuration and rebuilds the JNI dependency as `libtrial_t5.so`. APK validation rejects dependency paths/missing libraries/missing exports. All strong imported symbols resolve against packaged libraries or public API 26 NDK stubs; the eleven compiled JNI descriptors match the native signatures. The update preserves app ID, signing certificate, private pack contract, runtime/model pins, inference algorithms, microphone flow and radio status behavior. Safe diagnostics export only a known library basename or a bounded ELF symbol token, never raw linker text or private paths.

Download the [S25 native link update](https://github.com/Revty79/CommonTongue/releases/tag/pass5-s25-native-link-2026-10-08), choose **Update**, reuse the installed pack and tap **Start Testing**. No uninstall, clear-data, pack download/import or ADB action is needed. APK SHA-256: `4433987d060b4f829075a90b8ab80e43b18c85daed65983e8a5d5997cf732dc2`. The research prerelease includes audit/checksum files and exact requirements; [native correction evidence](../../tools/device-trial/evidence/s25-native-link-correction.json) records the received diagnostic, packaging defect, local 25 JVM/52 host tests and publication verification. At publication the version 4 device run was pending; the subsequent export now confirms native/model loading, while generation remains unestablished. The tag still identifies the approved older source baseline pending the single final Pass 5 commit.


## S25 post-capture worker diagnostic

The version 4 native correction is confirmed on the physical S25: MADLAD loads in 3.013?3.516 seconds and Whisper Base in 0.087?0.122 seconds across three fresh-worker loads. All reach READY. A 1.98, 4.10 or 1.98 second microphone capture is followed by an unexpected disconnect approximately 2?3 seconds after release. Loaded worker PSS is 2,899,419?2,902,300 KiB and RSS is 2,973,368?2,975,712 KiB. Android's system-low-memory observation is false at each READY snapshot; it does not establish the later exit reason. There is no completed ASR/translation/human turn in this export.

The historical [v5 research update](https://github.com/Revty79/CommonTongue/releases/tag/pass5-s25-crash-diagnostic-2026-10-08) introduced independent component checks and synced stages/matched Android exit metadata. Its pack contract and four native libraries are byte-identical to v4. APK SHA-256: `6c4f1d60589eb6d842ba5f98f12e8fe2b2bc5458578e1d4a1fd0051563130229`. [Diagnostic publication evidence](../../tools/device-trial/evidence/s25-crash-diagnostic.json) records its 32 JVM/60 host checks and publication-time pending status; the subsequent v5 export now verifies standalone Whisper and isolates the translation crash.

## Historical S25 translation inference correction

The matched v5 full-pipeline exit reports CRASH_NATIVE/SIGSEGV/status 11 after ASR_COMPLETE and TRANSLATION_START. The code inspection identifies unsigned raw model addresses serialized as JSON numbers; AOSP host execution proves tagged values can saturate through Double/getLong. Version 6 replaces these addresses with a checked, owned registry of opaque IDs and adds durable TOKENIZE_START/COMPLETE, ENCODER_START/COMPLETE, DECODER_START and FIRST_TOKEN callbacks. Model/runtime pins, Whisper, tensor kernels and generation settings remain unchanged. See [crash isolation](CRASH_ISOLATION.md) for lifetime/ownership inspection and the limit of current attribution.

The [historical v6 research update](https://github.com/Revty79/CommonTongue/releases/tag/pass5-s25-translation-fix-2026-10-08) and [correction evidence](../../tools/device-trial/evidence/s25-translation-inference-correction.json) retain their publication-time pending status and local handle proof. The subsequent v6 S25 export now confirms translation completion. Its newest full prerecorded check completes ASR in 1,691 ms and translation in 2,923 ms; two subsequent microphone turns complete ASR in 1,871/1,578 ms and translation in 1,166/1,219 ms. These are diagnostic checkpoint intervals, with instrumentation overhead, not semantic quality or release-to-audio measurements. All three then fail at TTS; the v6 error code/type is blank. Old component results in that cumulative export must not be attributed to this new session.

## Historical S25 TTS isolation and subsequent success

Install **CommonTongue-Pass5-S25-TTS-Diagnostic-Update.apk** from the [v7 research update](https://github.com/Revty79/CommonTongue/releases/tag/pass5-s25-tts-diagnostic-2026-10-08), choosing **Update**. Keep the pack and voices. Tap **Check offline speech (English + Spanish)** once, listen for four short phrases and export; then run the full recorded-sample check once and export again. See [TTS diagnostics](TTS_DIAGNOSTICS.md) for engine/voice return codes, listener/Android errors, WAV metadata and playback evidence. Version 7 preserves all native libraries byte for byte and changes only research TTS integration/diagnostics, UI and safe export support. The subsequent user-reviewed v7 result passes all four speech-only checks and the full prerecorded/microphone pipeline in both directions. The observed engine is com.google.android.tts with offline en-US/es-US voices. No voice/model replacement or additional Pass 5 APK is needed; see [closeout](PASS_5_CLOSEOUT.md).

## Final decisions and follow-up validation

| Question | Current answer |
| --- | --- |
| MADLAD executes on the S25 ARM64 | YES: accepted v7 microphone EN→ES/ES→EN and prerecorded full-pipeline completion |
| Complete physical local push-to-talk pipeline | YES on S25 v7 with selected offline voices and no INTERNET permission; separate radio-state proof flags not supplied in the reviewed summary |
| Actual S25 release-to-audio latency | First post-load human EN→ES approximately 4,665 ms with AudioTrack evidence; warm distribution not supplied |
| Practical S25 model memory | READY worker PSS/RSS measured; active inference peak not established |
| Older-device behavior | NOT_TESTED; hardware not provided yet |
| Acceptable lower-resource selected-model operation | UNKNOWN |
| Human bilingual use | Both microphone directions reach TTS_COMPLETE; larger conversations and participant ratings remain follow-up |
| Scripted correct / mostly correct / wrong | No observations; no accuracy percentage |
| Biggest current blocker | None for the accepted S25 Pass 5 proof; production adaptation and licensing remain Pass 6 work |
| Next step | Commit/push Pass 5, verify CI, report and stop before substantial Pass 6 changes |

The already published v7 APK is the accepted final Pass 5 research artifact, SHA-256 3eb9bdd9329737666510047c8238007b9a44441424f0fd516aed34a01b2b72d0. Preserve it, the installed pack and voices; do not rebuild the APK to remove history. The one authorized source commit/push and ordinary model/device-free CI closeout are recorded separately. The dispatch-only [research build workflow](../../.github/workflows/research-apk.yml) remains optional and is not run for this closeout, so a new CI signer/build cannot replace the proven S25 baseline. No production release is created.

See [device matrix](DEVICE_MATRIX.md), [performance protocol/results](PERFORMANCE_RESULTS.md), [failure records](FAILURE_CORPUS.md), [Android port](ANDROID_PORT.md), and [tooling instructions](../../tools/device-trial/README.md).
