# Model adoption policy

Status: foundational shipping policy with isolated Pass 2 research evidence. **No AI model or runtime is approved for production or bundled in the production app.** Pass 1 downloaded/integrated none; Pass 2 downloads local research artifacts into ignored directories and uses a separate spike. This policy creates no AI interfaces.

Every model considered for shipping must have a review record containing:

| Required evidence | What the record must explain |
| --- | --- |
| Source | Authoritative publisher/repository and artifact origin |
| Model/version | Exact model identity, immutable revision, and artifact checksum |
| License | Exact model license and applicable accompanying terms |
| Commercial-use status | Whether intended commercial use is permitted and on what terms |
| Redistribution rights | Whether bundling/distributing original and derived artifacts is permitted |
| Languages | Supported directions/languages and evidence for intended regional coverage |
| Expected storage | Installed artifact/pack size and download/installation overhead |
| Memory requirements | Measured loading, steady-state, and peak memory on representative devices |
| Runtime | Required runtime/version, supported device backends, and runtime licensing |
| Quantization | Exact precision/format, provenance, and measured quality implications |
| Benchmark results | Reproducible quality, latency, resource, and offline results with hardware/settings |
| Known limitations | Quality gaps, unsupported inputs, regional weaknesses, and operational constraints |

Freely downloadable does not mean commercially usable or redistributable. A model may not enter a commercial build without documented rights for the intended use and distribution. Runtime, tokenizer, conversion/quantization, derived artifact, and pack terms must also be accounted for where relevant. Unclear rights require resolution before acceptance.

Approval requires evidence appropriate to the intended device range, including low-end phones, and compliance with the offline/privacy rules. No model name may become a domain concept. Implementations must use the model-neutral [production capability contracts](TRANSLATION_CONTRACTS.md) introduced in Pass 3 and remain testable with lightweight substitutes. Optional cloud functionality must remain separate, with no silent fallback; OFFLINE_REQUIRED must be rejected before any network-capable work when unsupported.

Pass 2 records provisional A/B/C classifications, immutable artifacts and checksums, conversion/voice provenance gaps, and measured offline desktop/Android results in [candidate records](feasibility/MODEL_CANDIDATES.md) and the [feasibility report](feasibility/PASS_2_OFFLINE_FEASIBILITY.md). Whisper and OPUS-MT are provisional candidates, not shipping approvals. GPL eSpeak and archived Piper/individual voices remain research-only. A public-domain dataset declaration is not assumed to license every derived voice weight; model, engine, and individual voice rights require separate evidence.

Lite/Standard/Enhanced recommendations remain provisional. Physical low-end-phone latency, memory, regional speech quality, battery and thermals are unverified; no tier selection or production profiling feature is implemented.

Pass 3 adds scoped verification and meaning-preservation expectations without approving/integrating a model. Its integer guard does not establish semantic correctness or fix the Pass 2 translation failures. Future adoption requires quality evidence under the [documented rubric](QUALITY_ARCHITECTURE.md), including critical semantic/terminology/omission risks and honest unsupported/unchecked reporting. Quality profiles express preferences only; no concrete model mapping exists.

Pass 4 adds isolated translation-quality evidence for the preserved OPUS int8 pair, locally converted M2M100-418M int8, Qwen3.5 0.8B Q4_K_M/Q5_K_M and 2B Q4_K_M, official Qwen2.5-1.5B-Instruct Q4_K_M, and Google MADLAD400-3B-MT Q4_K. See the [bake-off](quality/PASS_4_QUALITY_BAKEOFF.md), [complete candidate records](../tools/translation-quality/candidates.json), and [immutable artifact lock](../tools/translation-quality/artifacts.lock.json). All seven tested configurations are **provisional A** under their declared grants: M2M100 MIT; the others Apache-2.0. ONNX Runtime and llama.cpp are MIT, CTranslate2 is MIT, and Candle is MIT OR Apache-2.0; tokenizer/converter terms are recorded separately. These classifications are not legal approval or permission to bundle any artifact.

M2M's local int8 conversion pins the source checkpoint, complete tokenizer/language-token map, converter/library versions and generated checksums. The Qwen3.5 third-party quantizations identify the publisher's tool/calibration information but do not disclose an exact source weight revision or complete calibration provenance. Official Qwen2.5 GGUF publication improves publisher traceability but still omits exact conversion tool/source-weight commits. Google's published MADLAD quantized archive uses the Candle tensor format; its precise quantizer command/commit is unstated. Upstream license snapshots are not falsely presented as the exact base weights used by an undisclosed converter. These gaps require resolution appropriate to a release, including preservation of licenses/notices and review of derived-artifact rights.

Pass 4 quality ratings are model-assisted and unblinded, with no qualified native/fluent-human approval. Desktop CPU RAM/latency and process-restricted offline proof establish research evidence, not physical-phone suitability, low-resource quality, safety or tier mappings. Any conditional Pass 5 recommendation remains a research shortlist. No model/runtime enters the production dependency graph or Android package, and no permission changes occur.

Pass 5 is accepted on the user-reviewed S25 v7 proof: locked MADLAD Q4_K / Candle CPU, Whisper Base Q5_1 and installed offline Android TTS complete both microphone directions and the prerecorded pipeline. The observed engine is com.google.android.tts with offline en-US/es-US voices; no vendor voice data is bundled or redistributed. Preserve the working v7 APK/pack/source and historical crash receipts. Broader device/human/sustained validation remains follow-up; model classifications/provenance gaps and commercial-production approval are unchanged. The research APK requests RECORD_AUDIO only with no INTERNET permission; production app/core permissions, code and dependencies remain unchanged. [Closeout](device/PASS_5_CLOSEOUT.md) records the exact baseline. Pass 6 is authorized to investigate and adapt installed offline TTS behind SpeechSynthesizer, with no Whisper/MADLAD change; its implementation and first-party licensing investigation follow the final Pass 5 source/CI handoff.
