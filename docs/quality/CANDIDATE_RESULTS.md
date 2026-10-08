# Candidate results

All quality judgements below are **model-assisted Codex assessments, unblinded and not native/fluent-human reviewed**. The main comparison uses the same 68 difficult cases per configuration. It is not a population failure rate or a safety certification. Full dedicated/direct/hybrid sweeps contain 196 outputs each; 128 per sweep remain without absolute semantic ratings. Ablation judgements are relative, not semantic passes.

## Tested identities, precision and licensing

The preserved OPUS pair, M2M100, Qwen3.5, Qwen2.5 and MADLAD are five meaningful families/generations. There are seven artifact configurations; Qwen3.5 includes two sizes and a Q5 sensitivity control. No server-scale model was used.

| Candidate / precision | Tested immutable publication revision | Declared model license / class |
| --- | --- | --- |
| OPUS EN?ES ONNX int8 | Xenova `4b002a4c7edd54a7ced58877258b87f7efd3f892` | Apache-2.0 / provisional A |
| OPUS ES?EN ONNX int8 | Xenova `eadfd7c658a9d8929ac3b8e996b68a68e2c7d480` | Apache-2.0 / provisional A |
| M2M100-418M, local CT2 int8 | facebook `55c2e61bbf05dfb8d7abccdc3fae6fc8512fd636` | MIT / provisional A |
| Qwen3.5-0.8B Q4_K_M and Q5_K_M | bartowski `f36b1ea49a332ede8fe5f389bbf5b3575ef71f48` | Apache-2.0 / provisional A |
| Qwen3.5-2B Q4_K_M | bartowski `7d26695454df6de5fbcce2e58681e62dae06ce43` | Apache-2.0 / provisional A |
| Qwen2.5-1.5B-Instruct official Q4_K_M | Qwen `91cad51170dc346986eccefdc2dd33a9da36ead9` | Apache-2.0 / provisional A |
| MADLAD400-3B-MT published Q4_K | Google `fa184c675da0b5c9e1c8694fccd4e12e2d422094` | Apache-2.0 / provisional A |

Source/license snapshots: Helsinki EN?ES `5bc4493d463cf000c1f0b50f8d56886a392ed4ab`, ES?EN `c96e2c5399ebfae4fc43d9669556b9afa74bb69d`; Qwen3.5 0.8B `2fc06364715b967f1860aea9cf38778875588b17`, 2B `15852e8c16360a2fea060d615a32b45270f8a8fc`; Qwen2.5 `989aa7980e4cf806f80c7fef2b1adb7bc71aa306`. These snapshots are not asserted to be undisclosed quantizers' exact source weights.

Runtime identities: ONNX Runtime 1.30.0 (MIT), CTranslate2 4.8.2 (MIT), SentencePiece 0.2.2 (Apache-2.0), Transformers 4.57.6 (Apache-2.0), PyTorch 2.14.1+cpu (BSD-3-Clause), llama.cpp `8345f333951c661d166b00e6f9362e553768f292` (MIT), Candle `31f35b147389700ed2a178ee66a91c3cc25cc80d` / 0.11.0 (MIT OR Apache-2.0), and Rust tokenizers 0.22.2 (Apache-2.0). GGUF tokenizers are embedded; MADLAD uses its pinned tokenizer.json; M2M needs the pinned upstream special-token map. The [candidate registry](../../tools/translation-quality/candidates.json) records tokenizer/derived-artifact obligations, commercial-use and redistribution status, and known conversion/calibration ambiguities. No candidate is legally approved for production.

Primary publisher references: [OPUS](https://huggingface.co/Helsinki-NLP/opus-mt-en-es), [M2M100](https://huggingface.co/facebook/m2m100_418M), [Qwen3.5](https://huggingface.co/Qwen/Qwen3.5-2B), [Qwen2.5 official GGUF](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF), [MADLAD](https://huggingface.co/google/madlad400-3b-mt), [CTranslate2 conversion](https://opennmt.net/CTranslate2/guides/transformers.html), [llama.cpp](https://github.com/ggml-org/llama.cpp), [Candle](https://github.com/huggingface/candle). Immutable artifact URLs/checksums are authoritative for this study, rather than moving main branches.

## Main reviewed quality panel

Omissions/inventions count any dimension score 0 or 1; material score-0 counts are separately retained in summary.json. Replaced wrong words are meaning/term errors, not automatically double-counted as invention and omission. Risk score is secondary to severity and concrete examples.

| Configuration | Critical /68 (rate) | Major /68 (rate) | Minor | Omission | Invention | Risk score |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| opus-baseline | 6 (8.8%) | 22 (32.4%) | 5 | 3 | 1 | 69.1 |
| m2m-int8 | 14 (20.6%) | 31 (45.6%) | 10 | 3 | 0 | 48.6 |
| madlad-q4k | 1 (1.5%) | 18 (26.5%) | 14 | 0 | 1 | 78.9 |
| q080-direct | 18 (26.5%) | 24 (35.3%) | 14 | 5 | 2 | 47.5 |
| q2-direct | 10 (14.7%) | 17 (25.0%) | 19 | 2 | 0 | 67.2 |
| q25-direct | 16 (23.5%) | 19 (27.9%) | 13 | 7 | 6 | 56.0 |
| q080-hybrid | 5 (7.4%) | 23 (33.8%) | 6 | 2 | 1 | 69.6 |
| q2-hybrid | 3 (4.4%) | 19 (27.9%) | 12 | 1 | 1 | 75.8 |
| q25-hybrid | 8 (11.8%) | 20 (29.4%) | 10 | 6 | 1 | 67.2 |
| q2-verify-retry | 5 (7.4%) | 19 (27.9%) | 7 | 2 | 1 | 73.6 |

MADLAD has 19 combined critical/major failures (27.9%) versus OPUS 28 (41.2%), with critical counts 1 versus 6. Qwen3.5-2B post-editing has 22 combined (32.4%) and 3 critical; verification/retry has 24 combined (35.3%) and 5 critical. These are directional research findings on the fixed risk panel, not statistically independent trials or native-speaker ground truth.

Specific failures matter: M2M changes written 127 to 126; direct Qwen3.5-2B changes it to 117. MADLAD improves vehicle-jack EN?ES and several clear technical sentences, but still produces cat for the explicitly identified tool ES?EN, sill for cimbra, and fair for feria. Its Spanish expletive negation in en04 is not counted as a reversal simply because another no appears. The 2B model resolves several domain hints, yet reverses the mortar prohibition under hints and changes clutch state. Qwen2.5 direct can rename Jack as a lifting tool or obey the embedded receipt request; an unrequested do not translate response also occurs on es04.

The separate post-sweep 22-case critical diagnostic audit covers business names, phone digits, calendar disambiguation, decimal units, quantity associations, negative concord, language-only medical vocabulary, a ladder prohibition and conditional payment. Each of OPUS/MADLAD/2B-hybrid has zero critical findings there; OPUS and hybrid have one major transfer?physical relocation error, MADLAD one minor stripped business-name accent. These 66 observations are retained separately, not pooled into the 68-case rates. This targeted audit was selected after the main sweeps and is not a held-out validation set.

Unreviewed outputs can still be visibly bad: for example, both with/without-context 2B-hybrid q016-en-es omit Leave it there. An UNCHANGED pair is not a pass. No full 196-case semantic failure rate or safe medical translation claim is made.

## Context and terminology ablations

Each contextual configuration has 32 context pairs and 28 hint pairs. Counts include relative improvements that still leave other errors. Identical text is deterministically unchanged; changed-text judgements are model-assisted. Awkward counts describe the with-hint output and do not prove the hint newly caused every awkward phrase.

| Configuration | Context improved / unchanged / worsened (32) | Hints improved / unchanged / worsened (28) | Hint sense unresolved | Awkward with hint | Unrelated override |
| --- | --- | --- | ---: | ---: | ---: |
| q080-direct | 6 / 21 / 5 | 10 / 13 / 5 | 14 | 9 | 0 |
| q2-direct | 6 / 21 / 5 | 14 / 10 / 4 | 1 | 5 | 0 |
| q25-direct | 4 / 23 / 5 | 10 / 12 / 6 | 7 | 11 | 2 |
| q080-hybrid | 2 / 26 / 4 | 5 / 20 / 3 | 12 | 5 | 0 |
| q2-hybrid | 4 / 27 / 1 | 16 / 10 / 2 | 1 | 5 | 1 |
| q25-hybrid | 2 / 30 / 0 | 8 / 19 / 1 | 8 | 5 | 0 |

Context helps some cranking and ellipsis cases, but also causes source copying, prior-turn substitution or current-clause loss. The strongest hint response is the 2B post-editor, including formwork, studs, priming and livestock handling. Its gains do not remove global draft anchoring or polarity errors. The unrelated controls catch Qwen2.5 direct bank?shore and Jack?tool changes; the 2B post-editor also spills the river hint into the otherwise unrelated river-exclusion clause. Dedicated MT has no context/hint interface; these options are unsupported there.

Regional observations are mixed: all surveyed configurations miss feria; chamba/de la fregada and hacer el paro remain weak. MADLAD and Qwen2.5 recognize Argentine colectivo on the reviewed case, where OPUS and other configurations render collectively/group. Ordinary pickup/dinner-refusal clauses survive in OPUS and MADLAD, while the 0.8B direct model can copy the Spanish source instead of producing English. Target-region instructions alone do not establish regional competence. Native naturalness and broader dialogue flow remain unvalidated.

## Translation obedience and verifier behavior

On the 10 embedded-instruction cases, output scope/frame failures are 6/10 for 0.8B direct, 1/10 for 2B direct, 4/10 for Qwen2.5 direct and 2/10 for Qwen2.5 hybrid; zero observed for the other full configurations. Actual response-to-source behavior is observed on one case each for 0.8B direct and Qwen2.5 direct. These counts overlap: they are not summed. Semantic content/polarity errors occur even where behavior counts are zero. No multiple-answer/refusal behavior is asserted from heuristic flags alone. Profanity can be softened or misread as a technical attribute; no model is approved on a zero refusal count.

The 68-case verifier returns 36 PASS, 27 FAIL and 5 REVIEW. It produces 28 incomplete 96-token verdicts, triggers 27 bounded repairs and leaves 9 repaired texts identical to their drafts. Ten PASS verdicts accept a draft rated major/critical; fourteen FAIL verdicts target a draft rated none/minor. FAIL prefixes can trigger a repair even when the explanation is capped; this is not a completed verification claim. Repair changes q013-es-en from Not that to That and can worsen clutch polarity. The gate is unreliable despite improving some cases. No retries beyond one occur.

The 64-output template pilot has concise critical/major counts 8/9 across its two sizes versus structured 7/13. Concise reduces combined major/critical failures (17 versus 20) and avoids some financial-bank hint leakage; structured has one fewer critical failure. Neither passes a useful quality bar. The 0.8B Q5 control has 4 critical and 6 major failures on its 16 cases, the same totals as Q4; different outputs include new invented plastic-hose detail and lost command content. It does not rescue this model under the tested settings. No FP16 or whole-corpus quantization-effect claim follows.

## Artifact sizes

MB here means 1,000,000 bytes. Download totals include candidate files/snapshots, but exclude shared runtimes/toolchains. Active inference assets exclude unused preparation checkpoints/cards and include the tokenizer/config files actually needed. Runtime/application/ASR/TTS overhead is additional.

| Configuration | Downloaded preparation assets MB | Active inference assets MB |
| --- | ---: | ---: |
| opus-pair | 232.20 | 232.18 |
| m2m100-int8 | 1941.94 | 499.73 |
| qwen35-0.8b-q4_k_m | 579.72 | 579.62 |
| qwen35-0.8b-q5_k_m | 646.20 | 646.09 |
| qwen35-2b-q4_k_m | 1396.30 | 1396.20 |
| madlad400-3b-q4k | 1675.67 | 1671.23 |
| qwen25-15-q4 | 1117.35 | 1117.32 |

M2M requires a ~1.94 GB source download for preparation, but its tested local int8 model.bin is 490,667,752 bytes; this is not a 2.43 GB deployed model. MADLAD is Google's Candle T5 archive, not a llama.cpp-compatible GGUF. The [57-record lock](../../tools/translation-quality/artifacts.lock.json) gives exact filenames, sizes and SHA-256 for all downloads and locally converted outputs; [verification](../../tools/translation-quality/evidence/artifact-verification.json) confirms every size/digest. Shared quantization families are not counted as independent downloads in total storage estimates.

## Uncontended desktop resource replay

24 cases, 12 each direction, four requested CPU threads, fresh model construction and warm OS file cache. Model-load time excludes Python package import and shell/process startup; hybrid sums component constructors. RSS is process-tree working set, not Android PSS or total reserved allocation. Token/sec is generation-only, with prefill separately retained. Measurement includes no ASR/TTS or Android app memory.

The initial Candle sweep set Rayon=4 but its quantized barrier pool defaulted to six physical cores. The corrected helper sets both pools=4. All 68 re-executed reviewed outputs and the 24 resource outputs are byte-identical. The final table uses the corrected replay; the initial control and actual configuration caveat are retained in [thread correction evidence](../../tools/translation-quality/evidence/madlad-thread-correction.json).

| Configuration | Model load s | Warm median / p95 s | Peak / steady GiB | Approx busy CPU cores | Generated tokens/s |
| --- | ---: | --- | --- | ---: | ---: |
| perf-opus | 0.86 | 0.48 / 0.69 | 0.97 / 0.97 | 4.52 | NA |
| perf-madlad-four-threads | 1.13 | 1.85 / 2.75 | 2.72 / 2.62 | 4.26 | 12.1 |
| perf-q080-direct | 0.78 | 0.90 / 1.09 | 0.87 / 0.86 | 4.17 | 38.2 |
| perf-q2-direct | 1.29 | 1.92 / 2.32 | 1.91 / 1.90 | 4.27 | 18.9 |
| perf-q2-hybrid | 2.25 | 2.63 / 3.53 | 2.85 / 2.84 | 4.35 | 18.9 |
| perf-q2-verify | 2.16 | 8.05 / 11.33 | 2.86 / 2.85 | 4.35 | 18.7 |

Every final replay exactly reproduces its corresponding 24 quality-sweep outputs. Approximate CPU values slightly above four include measurement/parent activity and timing granularity, not a measured phone power budget. Uncontended source/draft/verification/repair latency is included for pipelines. Quality-sweep timing for rejected M2M/Qwen2.5 is retained in metadata/summary but is not a controlled frontier measurement.

The 2B hybrid retains both OPUS directions plus the LLM: 2.85 GiB peak and 2.84 GiB steady. Sequential one-direction OPUS ? unload ? LLM reload peaks at 1.95 GiB and takes a median 4.84 seconds per utterance on two cases, repeated three times each. Median MT load is 486 ms, MT gc/unload 42 ms, LLM load 1,267 ms and LLM inference 2,196 ms. The 2.63-second co-resident replay uses a broader 24-case subset, so the difference is descriptive rather than a paired latency estimate. Sequential savings include loading only one MT direction. GC time is not a guarantee of OS reclamation; no audio-engine coexistence was measured.

All model inference remains local and socket-restricted. Network canaries fail with Windows 10013 and every audit tripwire remains empty, including corrected-finalist and unload/reload runs. See raw resource, conversion, native parity, build, review and compact output records under [evidence](../../tools/translation-quality/evidence/).
