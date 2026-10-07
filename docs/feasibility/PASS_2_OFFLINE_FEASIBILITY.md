# Common Tongue — Pass 2 offline feasibility proof

Executed **2026-10-07**, starting from approved Pass 1 commit `06f30d4e0479a17b51794a9388bfd5db5b8cf39f`. The isolated research code performs real **English ⇄ Spanish WAV → ASR → translation → synthesized WAV** on Windows and Android with networking prevented. It does not provide the production app's translation feature.

## Feasibility decision

| Question | Verdict | Evidence / implication |
| --- | --- | --- |
| A. Technically perform English ⇄ Spanish completely offline? | **YES** | Two cached desktop benchmark runs and Android full chains in both directions used local model inference and local TTS. |
| B. Plausible Android-local stack? | **YES** | Tiny/Base + OPUS-MT + local eSpeak completed on API 36; Tiny completed on API 26. arm64 native libraries compiled; no Internet permission. Phone execution remains unverified. |
| C. Quality promising enough to continue? | **NEEDS MORE EVALUATION** | Simple conversation/numbers/negation often work, but regional Spanish and technical terms fail materially. Idiom handling can omit a whole sentence. Native/fluent review is NOT RUN. |
| D. Plausible low-end-phone path? | **UNKNOWN** | No physical phone. App RSS high-water about 643–722 MiB, substantial multi-core CPU work, and high allocator retention on API 26. Emulator results cannot establish a Lite phone tier. |
| E. Biggest technical risk? | **Meaning preservation** | Wrong automotive/construction/ranch vocabulary, omitted clauses, ASR errors and regional ambiguity undermine dependable conversational translation even when inference succeeds. |

Research demonstrates feasibility; it does **not** approve these models/voices for production or validate use for technical safety instructions. Physical arm64 latency, sustained memory, battery/thermals and model/voice redistribution provenance are additional unresolved risks.

## Stack and licensing

| Tested candidate | Runtime / format | License evidence and classification |
| --- | --- | --- |
| Multilingual Whisper Tiny Q5_1; Base Q5_1 | whisper.cpp 1.9.5, CPU GGML; Windows CLI / Android JNI | Model/runtime MIT; **A provisional**, conversion/notice review still required |
| OPUS-MT EN→ES; ES→EN | Xenova split quantized Marian ONNX, ONNX Runtime 1.30.0, SentencePiece 0.2.2 | Upstream **model Apache-2.0**, ORT MIT, tokenizer Apache-2.0; **A provisional**, converted-artifact historical base revision/license notices unresolved |
| Compact alternate OPUS-MT EN→ROMANCE, Spanish target | Same ONNX adapter; EN→ES text comparison only | Upstream model Apache-2.0; **A provisional**, same provenance caveat; ES→EN comparison NOT RUN |
| eSpeak NG English / Spanish | 1.52.0 source-built Windows executable; independently installed Android engine | Engine/language/voice rules GPL-3.0-or-later; **B research only**, copyleft redistribution obligations |
| Archived Piper English LJ Speech / Spanish CarlFM | 2023.11.14-2 Windows distribution; ONNX voices; bundled ORT 1.14 | Piper MIT, bundled eSpeak/phonemizer GPL obligations; individual dataset cards public domain, explicit derived-weight rights need review; **B research only** |

No paid/cloud inference, proprietary commercial component, account, contextual LLM, model server or voice cloning was used. Exact model/runtime revisions, publishers, filenames, byte sizes, checksums, commercial/redistribution evidence, quantization and ambiguity are in [MODEL_CANDIDATES.md](MODEL_CANDIDATES.md). The [artifact lock](../../tools/offline-feasibility/artifacts.lock.json) pins every downloaded model/config/card/source archive. Metadata's upstream card snapshot does not pretend to identify the converter's historical base commit.

The Windows eSpeak MSI binary crashed with access violation `3221225477`; the user dismissed its error dialog. Pinned source compilation produced a working formant baseline. The harness now suppresses native Windows error dialogs and bounds subprocess runtime. This failure is part of the result, not hidden behind the successful replacement.

## Desktop evidence

Host: **AMD Ryzen 5 5600G, six cores/twelve threads, 33,687,609,344 bytes physical RAM (31.4 GiB), Windows**, Python 3.13; CPU inference uses four threads. Python ORT 1.30.0; Piper's archived binary contains ORT 1.14, separately identified. Exact package/tool versions are pinned or recorded in the [harness README](../../tools/offline-feasibility/README.md), requirements and [environment inventory](evidence/environment.json).

The [32-case corpus](../../tools/offline-feasibility/corpus.json) covers conversation, idioms/slang, negation, money/numbers, dates/time, measurements, automotive, construction, ranch/agriculture, interruption and ambiguous/regional language. Each source has a category and meaning that must survive. Six clean synthetic speech files (three per language) plus two deterministic 10 dB noise variants run with Tiny and Base. Two complete runs produced **96 text outputs and 32 full speech pipelines**. No sensitive recordings or large model/audio files are committed.

Neural-fixture run medians (milliseconds):

| Direction / ASR | ASR incl. cold load | Warm translation | TTS incl. cold load | Total | Peak process-tree RSS |
| --- | ---: | ---: | ---: | ---: | ---: |
| EN→ES Tiny | 515.5 | 495.7 | 563.8 | 1581.6 | 647.3 MiB |
| EN→ES Base | 1008.0 | 466.7 | 619.7 | 2115.0 | 696.0 MiB |
| ES→EN Tiny | 571.3 | 555.2 | 955.1 | 2134.9 | 703.6 MiB |
| ES→EN Base | 1004.7 | 542.0 | 905.4 | 2478.8 | 705.5 MiB |

Translator cold loads were **981.7 / 474.0 / 471.0 ms** EN→ES / ES→EN / alternate; pipeline totals exclude translator loading because its sessions are reused. Text-only median MT was **607.2 / 492.0 / 549.8 ms**. The timing/memory measurement limits and initial formant run are retained in [BENCHMARK_RESULTS.md](BENCHMARK_RESULTS.md). Separate TTS model-load time is UNAVAILABLE; its startup is included in each desktop TTS wall time. These are small single-pass samples, not guaranteed latency.

Mean synthetic-speech WER in the neural run was **0.086 / 0.040** EN Tiny/Base and **0.258 / 0.150** ES Tiny/Base. Base handles the two tested noise variants better, but both models make modal/person changes and ranch-word errors. Formant Spanish was much worse. Fixture voice and Whisper decoding differed between the first formant and later neural runs, so their difference cannot be attributed solely to voice quality. Synthetic WER does not estimate natural accents or real microphone behavior.

Desktop output examples from the neural fixtures:

- EN audio reference: “I can pick you up after work, but I cannot stay for dinner.” ASR: “I **could** pick you up after work, but I cannot stay for dinner.” MT: “Podría recogerte después del trabajo, pero no puedo quedarme a cenar.” Spanish WAV produced. The modal changed during ASR.
- ES audio reference: “Te puedo llevar después del trabajo, pero hoy no me puedo quedar a cenar.” ASR: “te **puede** llevar después del trabajo, pero hoy no me puedo quedar a cenar.” MT: “I can take you after work, but I can't stay for dinner today.” English WAV produced. The ASR person changed; the result is not proof of exact meaning fidelity.
- Perfect ES text “No prendas el motor…” becomes “Don't **wear** the engine…”; “cimbra” becomes “cipher”; vehicle “gato” becomes “cat” despite explicit tool context. These are MT failures independent of ASR. “Arm and a leg. I am pretty broke now.” loses the second sentence in both translators. The compact alternate is not consistently better.

All cases, tested outputs, perfect-source translations versus ASR-fed translations, per-stage metrics, fixture hashes and observations are preserved in [benchmark results](BENCHMARK_RESULTS.md) and raw JSON.

## Android proof

Standalone [research project](../../spikes/offline-feasibility/build.gradle.kts), SDK **26/36/36**, CPU JNI Whisper/SentencePiece plus ORT Android 1.30.0. It accepts app-private prerecorded WAVs and locally synthesizes both outputs through explicitly offline eSpeak voices. API 36 Tiny completed at **6.44/4.70 seconds** EN→ES / ES→EN, Base **8.77/7.90 seconds**. API 26 Tiny completed at **6.72/5.23 seconds**. These totals include cold translator loading but exclude one-time ASR loading and initial TTS binding/WAV parsing.

The emulators are **x86_64 with 2 GiB configured RAM**, not low-end phones. arm64-v8a compiled/packaged, physical execution NOT RUN. The first unoptimized native build took about 40 seconds per phrase; that completed evidence remains available. Optimized final native configuration is explicit. No OOM/native crash occurred in completed Android inference. Peak RSS and sampled PSS differ; external TTS memory is recorded separately. API 26 retained more memory after session closure, another reason to avoid a Lite-tier claim.

See [ANDROID_RESULTS.md](ANDROID_RESULTS.md) for exact device/image/ABI, APK hashes, model transfers, native libraries, stage loads/times, CPU/PSS/RSS, initial failures, 32-bit investigation and replication. armeabi-v7a has upstream runtime/engine support but the spike was NOT BUILT/NOT RUN for it. Physical phone battery/heat are UNAVAILABLE.

## Explicit offline proof

**Desktop PASS:** artifacts were downloaded in a separate preparation phase. Full inference ran in the session's OS-restricted process sandbox: GitHub and Hugging Face TCP 443 canaries both failed with **WinError 10013** before inference. A Python socket audit tripwire installed before heavy inference imports recorded **zero inference socket attempts**; offline/telemetry environment flags were also set. The OS restriction covers native subprocesses; the Python tripwire alone would not. This is scoped process-network prevention, not a whole-host disconnect or exhaustive packet capture. Inference has no downloader or cloud fallback.

**Android PASS:** debug/release merged manifests and packaged APK inspection showed **zero requested permissions**. The separate eSpeak engine likewise requests no permissions. The spike removes ORT's transitive Internet/network-state declarations and telemetry initializer, and disables native telemetry. API 36 had disabled Wi-Fi/mobile data, airplane mode enabled, no IP routes and no active default network. API 26's old shell Wi-Fi command failed; root ADB disabled both network links on that dedicated development image, leaving no routes. Both local voices explicitly report no network synthesis requirement. All transferred files were checksum-verified before inference. No remote inference endpoint is present.

## Provisional tiers and installed footprint

| Tier | Research recommendation | Both-direction model assets, excluding APK/engine |
| --- | --- | ---: |
| Lite | **No validated Lite configuration yet.** Tiny Q5_1 + bilingual OPUS-MT + external offline TTS is the smallest tested baseline; memory/physical-phone performance and quality remain unresolved. | 252.1 MiB |
| Standard | Base Q5_1 + same translators is a stronger ASR research candidate; synthetic noise results improve, but translation failures remain. Physical arm64 testing required. | 278.4 MiB |
| Enhanced | Deferred. No contextual LLM, Whisper Small or M2M100 test; no implemented tier selector. | UNKNOWN |

Optional desktop neural TTS adds **87.4 MiB** of two voice weights; corresponding total model footprints about **339.5 MiB Tiny / 365.8 MiB Base**, excluding configs/engines. Android proof APK is **79.1 MiB** for two ABIs and no weights; external eSpeak APK is **10.0 MiB**. Actual installed storage adds extraction, generated WAVs and overhead. Downloaded source archives/toolchains/alternate research model are development assets, not a claimed production pack size.

## Implementation boundaries and reproducibility

- Added the desktop CLI, explicit online preparation with immutable checksums, reproducible native build commands, two local TTS choices, original mini corpus, binary-safe Android transfer/collection, and six model-free stdlib tests in `tools/offline-feasibility`.
- Added a minimal separate Android app with CPU JNI inference, Marian decoding, local TTS, recorded timing/memory/CPU and a zero-permission manifest guard in `spikes/offline-feasibility`. It is invoked with root wrapper `-p` and is outside root module configuration.
- Added the four feasibility documents and small text-only raw evidence files; updated README/model policy to distinguish research proof from the unchanged production foundation.
- Extended root Spotless targets to format the spike's Kotlin/Kotlin DSL without configuring its build. Added a model-free Python test step to existing Actions. Added Python cache ignores. Normal CI downloads/configures no research model/native source project.

Production `app/`, `core/`, SDK requirements, permissions and dependency catalogs are unchanged. Root remains `:app` and pure `:core:domain`; no model/runtime-specific domain concept, capability interface, final translation UI, pack store, account or Pass 3 implementation was added. Follow the [harness README](../../tools/offline-feasibility/README.md) for online preparation, explicit offline runs and separate Android builds.

## Validation and Git status

| Check | Status |
| --- | --- |
| `clean test lint spotlessCheck :app:verifyFoundationManifest assembleDebug :app:assembleDebugAndroidTest` | **PASS**, 105 tasks; three existing unit tests, zero lint errors, prescribed manifests |
| Existing `:app:connectedDebugAndroidTest` | **PASS**, one smoke test on each API 26/API 36 emulator; XML confirms zero failures/errors/skips |
| Spike `assembleDebug lint verifyOfflineManifest` | **PASS**, arm64/x86_64 built; debug/release permission/provider/SDK guards passed; zero lint errors |
| Python `unittest discover -s tools/offline-feasibility/tests -v` | **PASS**, six tests; no model/runtime downloads |
| `actionlint` 1.7.12 | **PASS**, edited workflow syntax |
| Documentation links, staged asset/scope inspection, `git diff --check` | **PASS**, no large/binary artifact or production-code change |
| Desktop cached model-dependent benchmarks | **PASS**, both directions, both TTS fixture sets, actual outputs preserved |
| Android cached model-dependent integration | **PASS**, API 36 Tiny/Base and API 26 Tiny, both directions |
| Arbitrary WAV filename containing multiple periods | **PASS**, full offline Tiny/Piper pipeline after correcting transcript-output path handling |
| Physical arm64, 32-bit execution, native/fluent quality review | **NOT RUN / UNAVAILABLE** as described above |
| Remote GitHub Actions for approved baseline `06f30d4…` | **PASS**, independently rechecked [run 37694219295](https://github.com/Revty79/CommonTongue/actions/runs/37694219295) |
| Remote Actions for new Pass 2 local commit | **NOT RUN**; the brief requires a local commit and separate push authorization. Baseline green is not a new-commit CI result. |

Local Windows Kotlin daemon writes outside the restricted workspace failed; its in-process fallback compiled successfully. Initial UI-test execution encountered a stale emulator debug-signature mismatch; replacement/retry passed and the final XML was inspected. An AGP invocation can print BUILD SUCCESSFUL despite a per-device installation failure, so that initial run was not counted as a pass. These are local harness/environment issues, not production changes.

The completion reply reports the one local commit SHA. No huge binary is staged, no Pass 3 work is started, and no push is claimed without separate authorization. The remote verification item remains explicitly unverified for the new local commit; all authorized executable research and local regression work is complete.
