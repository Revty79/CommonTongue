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
