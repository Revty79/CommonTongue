# Architecture comparison and shortlist

The strongest relative result is **dedicated MADLAD400-3B-MT Q4_K**, with 1 critical and 18 major findings on the fixed 68-case model-assisted panel. OPUS has 6 critical and 22 major; the best contextual post-editor, OPUS + Qwen3.5-2B Q4_K_M, has 3 critical and 19 major. Neither the average score nor fluent wording overrides those failures. No native/fluent-human study has validated the ordering.

| Approach | Actual experiment | Finding | Disposition |
| --- | --- | --- | --- |
| A: dedicated baseline | Exact preserved OPUS int8 pair, 196 cases | Smallest installed pack and fastest measured replay; key domain/regional mistakes | Resource/control reference; fails useful low-resource quality minimum |
| B: stronger dedicated MT | M2M100 local int8 and MADLAD published Q4_K, 196 each | M2M worsens meaning/number failures; MADLAD improves the risk mix and some technical English, but misses important Mexican senses | MADLAD is conditional primary phone-research candidate and current quality reference |
| C: direct contextual LLM | Qwen3.5 0.8B/2B Q4_K_M and Qwen2.5 1.5B Q4_K_M, 196 each | Helpful context/hints on some cases; copying, clause loss, polarity/numeric changes, source obedience or hint override | Reject as primary brain under tested settings |
| D: OPUS + post-editor | Same three LLMs with original source and OPUS draft, 196 each | Frequent anchoring on the draft; 2B hints improve 16/28 pairs but overall gains are limited | Keep 2B hybrid as contextual research comparator; extra resident memory is material |
| E: verify then at most one repair | OPUS + local 2B verifier/repair on 68 risk cases | False PASS and FAIL verdicts, capped explanations, newly reversed selection/technical polarity; 27 repairs | Reject as trusted gate; not worth the measured compute at this reliability |

All approaches are isolated research. Production source, dependency graphs, permissions and adapters are unchanged. Greedy non-thinking LLM decoding and published quantization/runtime choices are part of each tested configuration; these results do not condemn every use or precision of a model family.

## Quality/resource frontier

The measured resource choices are not finished product tiers. MADLAD needs 1.67 GB of active inference assets and a sampled 2.72 GiB peak process working set. Its four-thread desktop warm median is 1.85 seconds; the controlled p95 is 2.75 seconds. Its quality gain is strongest on the reviewed risk mix, not every regression. It provides no context/terminology interface in this harness.

The direct 2B model uses 1.40 GB of assets and 1.91 GiB peak working set, with a 1.92-second median. It offers context/hints, but 10 critical errors versus MADLAD's 1 exclude it from the primary recommendation. A mathematically cheaper point is not a useful-quality winner. The direct 0.8B model uses 0.58 GB and 0.87 GiB peak, but has 18 critical errors. It cannot fill Lite merely because it fits.

The 2B hybrid combines approximately 1.63 GB of active model assets, 2.85 GiB peak and a 2.63-second median. It remains worth retaining as a **context/terminology comparator**, not an assumed upgrade to MADLAD. Its 3 critical/19 major results are better than OPUS's risk mix, but global draft errors, weak regional slang and conditional/technical mistakes remain. A future stronger MT seed has not been tested and is not claimed to work.

Sequential one-direction MT → unload → LLM reload reduces the measured hybrid peak to 1.95 GiB, at a median total of 4.84 seconds on two utterances repeated three times. It avoids simultaneous weights and loading the unused MT direction, but pays reload cost per utterance. It does not establish that an Android allocator returns memory in the same way. ASR/TTS/application residency will add memory to either approach.

The E pipeline's median is 8.05 seconds and peak 2.86 GiB; reliability does not justify that overhead. Its 10 PASS verdicts on major/critical drafts show why verifier confidence cannot be equated with semantic truth. A maximum-one retry prevents runaway work, not newly introduced errors.

## Finalists and decision

**Best quality candidate:** MADLAD400-3B-MT published Q4_K with the pinned Candle CPU T5 worker, conditional on the limited model-assisted evidence.

**Best mobile candidate to investigate:** the same dedicated MADLAD configuration. Its weight size and portable CPU research path justify a bounded Android feasibility probe, especially on devices with more memory. ARM kernels, Android build/lifecycle behavior, realistic RSS/PSS, latency and coexistence remain unproven. The x64 worker is not an Android adapter and is not packaged in the app.

**Best low-resource candidate meeting a useful minimum:** **none**. OPUS is the compact reference and 0.8B has low measured RAM, but both fail meaning requirements. There is no evidence to assign Lite, Standard or Enhanced models now.

Go/no-go answers:

- **A — Material improvement over OPUS: MIXED.** YES on the reviewed severe/major mix and several technical examples; NO on important unresolved Mexican/domain regressions, and no native or complete-corpus semantic validation.
- **B — Extra resources justified: DEPENDS ON DEVICE.** About four times OPUS's median latency and nearly three times its sampled peak RAM, plus much more storage, may be acceptable for a phone research candidate. No low-end claim is supported.
- **C — Credible architecture for physical Android research: YES, conditionally.** Dedicated quantized MADLAD is the primary probe, with OPUS as a control and the 2B hybrid as an optional contextual/resource comparator. This is not useful-quality or shipping approval.
- **D — Remaining problems:** model/domain/regional coverage, unreliable verification, limited contextual interfaces, unqualified linguistic assessment and unknown device resources. Licenses are provisionally permissive but conversion/calibration traceability and final legal review remain open.
- **E — Pass 5 should test:** whether the locked quantized T5 format/runtime can run correctly on Android ARM CPU; original regressions and held-out fluent-reviewed regional/domain utterances; cold/warm latency, cancellation and lifecycle cleanup, PSS/RSS, OOM behavior, storage, thermals and battery; then realistic ASR/TTS/app coexistence. If that physical port or quality review fails, stop adoption rather than silently switch to a server or claim a tier.

No Pass 5 port, production adapter, phone trial, UI, microphone flow, model pack, subscription feature or audio bake-off starts here.

## Pass 3 contracts

No hard representation mismatch was found. Source/direction, optional prior context, preferred terminology, region/domain, offline execution, completion/verification findings and support limitations can represent these experiments. No corrective production contract change was needed.

Future adapters still need meaningful performed-check coverage, uncertainty and bounded corrective behavior. Number words, mixed digit/word forms, decimal/unit association, meaningful negation and clause/agent preservation need broader scoped verification; the existing exact-integer guard proves none of these. Literal name mismatches can be harmless accent changes or harmful renaming and require semantic distinction. Model-generated PASS must remain scoped and cannot certify all meanings. Native blocking work needs cooperative cancellation and resource cleanup. These are adoption requirements for later review, not a quiet contract redesign in Pass 4.
