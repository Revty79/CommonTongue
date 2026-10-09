# S25 post-capture crash isolation

The received v5 S25 export isolates the failure to translation: Whisper-only passes in 1,475.845 ms, while MADLAD-only and the full pipeline exit at `TRANSLATION_START`. The matched full-pipeline worker reports `CRASH_NATIVE / SIGSEGV / status 11`; ASR completed beforehand. MADLAD-only's matched record reports `CRASH / status 0`, and the worker was still alive when cleanup was requested; that observation is preserved separately, not rewritten to match the native crash. READY memory alone did not establish the cause.

Version 6 corrects a concrete native handle-transport defect and adds finer checkpoints. The subsequent v6/v7 S25 results confirm translation completion and the accepted v7 full pipeline; see [closeout](PASS_5_CLOSEOUT.md). Historical v4 load/capture observations and v5 publication metadata remain in their original evidence files.

## Translation handle correction

The old Rust loader converted `Box<Model>` into an unsigned address, serialized it as a JSON number, and Kotlin retrieved it with `JSONObject.getLong()` before JNI passed it back for an unsafe dereference. Android ARM64 heap addresses may carry a tag in the top byte. Such addresses can exceed signed `Long`; AOSP's parser then falls back to `Double`, and `getLong()` can saturate at `Long.MAX_VALUE` or lose address bits. That permits successful model load/READY followed by a fault on the first model access. This is a desktop assumption in the FFI handle transport, before any encoder/decoder kernel. See [Android tagged pointers](https://source.android.com/docs/security/test/tagged-pointers) and the [AOSP JSON parser](https://android.googlesource.com/platform/libcore/+/refs/heads/main/json/src/main/java/org/json/JSONTokener.java).

Host execution of AOSP's unmodified JSON classes reproduces the conversion using a synthetic tagged value; only annotation stubs are supplied for host compilation. No device pointer is collected. This proves the code defect, not the exact pointer used in the S25 crash. The v5 export contains no native backtrace/raw handle, so its attribution remains an inference until the update succeeds on the phone.

Rust now owns each boxed model in a mutex-protected registry. JSON/JNI exchange only monotonically allocated opaque integer IDs in `1..2^32-1`, never addresses; IDs are not recycled, and unknown/closed IDs return errors without pointer casts. The registry keeps ownership through generation and serializes close against inference. Kotlin requires a matching `opaque-id-v1` contract and an actual integer type/range, avoiding JSON's permissive numeric coercion. C ABI widths are explicitly `uint64_t`/Rust `u64`.

Inspection of pinned Candle confirms `VarBuilder::from_gguf` reads quantized tensors into owned CPU storage retained by the network's `Arc`s; no borrowed mmap backing is used here. Tokenizer encodings own their buffers, `Tensor::new` constructs owned tensor storage, and the encoder tensor remains live throughout decoding. Request UTF-8 storage and progress callback references remain live throughout the synchronous C call. The JNI callback runs on the caller's thread, and Rust stops before further tensor work if a checkpoint callback fails. Thread-local scratch/pools and ARM64 kernels remain pinned and unchanged; the handle fix does not disable pointer tagging, change alignment, or replace the model.

## Tester procedure

Install **CommonTongue-Pass5-S25-Translation-Fix-Update.apk** from the [v6 research update](https://github.com/Revty79/CommonTongue/releases/tag/pass5-s25-translation-fix-2026-10-08). Choose **Update**. Keep the existing app data, installed pack and voices. No pack download/import or ADB connection is needed.

1. Tap **2. Check translation only** once. It loads MADLAD alone and translates the fixed en01 English sentence; it performs no ASR or TTS. This directly exercises the corrected handle and first encoder/decoder calls.
2. If it succeeds, tap **3. Check the full recorded-sample pipeline** once. It loads both models and runs the already-installed synthetic `fixture-en01.wav` through ASR, translation and installed offline TTS.
3. The **1. Check speech recognition only** control remains available for comparison; Whisper already passed v5 and its path has not changed.
4. After any worker-stopped message, allow several seconds for Android history to arrive. Use **Export Research Results** and give the coordinator the JSON. Do not repeatedly retry a failing check.
5. If the full recorded-sample check completes, try microphone capture once. The already-loaded full worker is reused. A microphone permission prompt appears before ordinary Start Testing when permission has not previously been granted.

The component checks use a fresh worker and allocate only the required model; the shared JNI library still maps the same transitive native dependencies. They require no microphone input. Radios may stay on. Airplane mode is only for the explicit offline-proof trial. Results are diagnostics, not translation-quality acceptance or a production installer.

## Boundary evidence

| Stage | Meaning |
| --- | --- |
| AUDIO_CAPTURE_COMPLETE | Microphone stopped/joined; a closed local PCM16 WAV is ready for transfer |
| AUDIO_DECODE_START / AUDIO_DECODE_COMPLETE | Local WAV parsing begins/ends; successful decoding records the actual sample count |
| ASR_START / ASR_COMPLETE | The boundary immediately before/after the native Whisper call |
| TRANSLATION_START / TRANSLATION_COMPLETE | The boundary before/after translation; complete requires uncapped, nonblank output |
| TOKENIZE_START / TOKENIZE_COMPLETE | Rust immediately before tokenizer encode / after successful source-token limit validation |
| ENCODER_START / ENCODER_COMPLETE | Immediately before / after the first T5 encoder forward call |
| DECODER_START | Immediately before the decoding loop and first decoder forward call |
| FIRST_TOKEN | First decoder forward and greedy sampling succeeded, even if the sampled token is EOS; the token itself is never exported |
| TTS_START / TTS_COMPLETE | Offline synthesis begins / AudioTrack playback head reaches the synthesized frame count |

UI and worker keep separate finite-metadata journals. Each checkpoint is synced and atomically replaced before entering the next native boundary. After a disconnect the UI recovers the matching worker/run checkpoint, including a stage whose IPC notification never arrived. No waveform, transcript, private filename/path or model text is included. Temporary partial checkpoint files are ignored. Component outcomes are recorded separately as PASS, FAILED or WORKER_EXIT; no automatic inference retry runs.

The six Rust translation substages call synchronously through JNI into that same durable journal/platform-summary/IPC mechanism. Only finite stage codes cross the callback. Diagnostic fsync/callback overhead is included in measured wall time; do not compare diagnostic timings to an uninstrumented performance baseline.

The app also sends a bounded private process-state summary to Android when supported. Android may throttle that optional API; the synced local journal remains authoritative. Private run correlation tokens and PIDs are excluded from exports.

## Android exit information

On API 30+ the app asks for its own worker's historical exit metadata. A current exit must match the private PID, inference-process role and worker creation-time window. Queries occur immediately and at bounded delays up to five seconds; unsupported, missing identity, unavailable history and query failure remain explicit availability states. The export includes reported reason/code, status, recognized signal where applicable, importance, PSS/RSS, relative age, low-memory reporting capability and a matching finite stage summary when present. It also records whether the worker was still alive at disconnect and whether subsequent cleanup was requested. A later cleanup exit must not be mistaken for the initiating failure.

The [ApplicationExitInfo API](https://developer.android.com/reference/android/app/ApplicationExitInfo) distinguishes native crashes, Java crashes, signals and system memory kills. Signal status and the platform's low-memory reporting capability must be interpreted together: SIGKILL on a device without low-memory reporting does not uniquely establish the cause. Historical memory can be a prior sample or unavailable; it is not guaranteed to be the allocation peak. The [ActivityManager APIs](https://developer.android.com/reference/android/app/ActivityManager) are used only within this app's scope, without adding permissions.

The export also includes the latest eight worker history records labeled **UNASSIGNED_HISTORY**. They may include deliberate unloads, backgrounding or updates; they are not automatically attributed to the current microphone turn. Raw process names, PIDs, UIDs, absolute timestamps, descriptions, traces/tombstones and device identifiers are never read into the exported report. Android reason/status are retained as reported; no reason is rewritten to OOM based on READY memory alone.

## Subsequent v6 translation completion and TTS blocker

The next S25 export reaches TOKENIZE_COMPLETE, ENCODER_COMPLETE, FIRST_TOKEN and TRANSLATION_COMPLETE on prerecorded and real microphone EN→ES turns, then fails at TTS_START. This supports the opaque-handle correction. It does not establish audible full-pipeline or translation-quality acceptance. The cumulative export also retains older v5 component/exit records; they are not new v6 crashes. Follow [v7 TTS diagnostics](TTS_DIAGNOSTICS.md) for the current update and procedure, preserving the installed pack and all v6 native libraries.

## Historical v6 validation and publication status

Version 6 preserves the app ID/certificate and exact pack contract. Only the research T5 handle registry, JNI progress adapter and translation diagnostics change; model files, runtime/dependency versions, tensor kernels, Whisper capture/inference and generation parameters remain unchanged. The two T5/JNI libraries are rebuilt; the two OPUS runtime libraries remain byte-identical to v5. Validation covers tagged numeric transport, ownership/stale handles, failed checkpoint callbacks, all six stages, existing PCM/import/privacy cases, research build/lint/manifest and native packaging. [Translation correction evidence](../../tools/device-trial/evidence/s25-translation-inference-correction.json) records the v5 observations, local proof and v6 publication. These checks describe the v6 publication-time status. Subsequent v7 user review accepts the S25 full pipeline; [closeout](PASS_5_CLOSEOUT.md) records the later result and final source/CI handoff without rewriting old receipts.
