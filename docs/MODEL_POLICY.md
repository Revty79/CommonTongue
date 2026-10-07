# Model adoption policy

Status: foundational policy only. **No AI model or runtime is approved, downloaded, integrated, or bundled in Pass 1.** This document does not select a provider or create AI interfaces.

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

Approval requires evidence appropriate to the intended device range, including low-end phones, and compliance with the offline/privacy rules. No model name may become a domain concept. Implementations must be replaceable behind future capability interfaces and testable with lightweight substitutes. Optional cloud functionality must remain separate, with no silent fallback.

Lite/Standard/Enhanced may inform later device-quality choices. No tier, model benchmark, profiling feature, or runtime investigation is implemented in this pass.
