# Pass 4 benchmark specification

## Scope and sources

The fixed corpus contains **196 cases: 98 English → Spanish and 98 Spanish → English**. It preserves all 32 Pass 2 fixtures and adds 82 authored bilingual scenario pairs. Every source is translated independently; its counterpart is not a gold translation. The corpus, scenarios, context, terminology hints, required facts and forbidden changes are Codex-authored research fixtures. There are **no qualified native/fluent-human golds or ratings**. No external grading service or online competitor received these texts.

The paired cases deliberately exercise different wording and regional senses, rather than repeating easy greetings. Mexican usage is explicit, with Argentine `colectivo` and Chilean `pega`/`micro` cases. An `es-MX` preference is not a claim to represent every Spanish speaker. The 32 original objects, sources, IDs, directions and categories remain unchanged and are verified against the original file digest.

`tools/translation-quality/corpus.json` is authoritative. Each record has source/direction/category, region/domain, semantic facts, forbidden changes, important-term annotations, critical structured/polarity information, optional prior turns and optional terminology. An empty important-term list means no separate term was annotated. References are facts to preserve, not exact-output targets. Medical and emergency vocabulary tests concern **language preservation only**, establishing no medical/legal/safety approval.

During the study, descriptive critical/important-term fields were enriched. No sources, expected facts, regions, context, hints or inference prompts changed. `evidence/corpus-enrichment.json` retains both corpus digests and records the all-case prompt-equivalence check. Original execution metadata retains its historical digest rather than claiming that every sweep executed the final annotated JSON bytes.

## Sampling and architecture comparisons

All dedicated MT and main direct/post-editor configurations translate the full 196 cases. The **68-case risk panel** contains the original 32 regressions plus 18 scenario pairs covering context, terminology, unrelated hints, profanity, Mexican/Argentine expressions, multi-clause content and embedded instructions. It was frozen before the comparative assessment, after the template pilot/initial baseline execution. It is **not an unseen holdout**, nor a random population sample. Failure rates apply to those reviewed cases only; other outputs remain explicitly unreviewed.

The verification/retry pipeline uses those same 68 cases, each checked against the OPUS draft and original source. The checker sees contextual information and hints, not authored expected facts. A FAIL verdict or scoped integer mismatch permits **one** constrained repair. REVIEW is not a pass and does not trigger repair. Verdict parsing is deliberately conservative; malformed replies are retained rather than treated as PASS. This is a research experiment, not a trusted semantic gate.

The 32 context cases contain prior speaker/source/translation/direction data. All six contextual direct/hybrid configurations run both with and without context. The 28 terminology cases comprise 24 domain examples and four unrelated-sense controls. All six run with and without the supplied hints. Dedicated MT does not support these fields; unsupported options are recorded, not simulated by prepending conversation text to an MT sentence.

Pair assessments classify IMPROVED, UNCHANGED or WORSENED. Identical text is deterministically UNCHANGED, not necessarily correct. Non-identical pairs receive model-assisted comparison; changes in meaning, term sense or grammar may count. Better output can still contain a severe error. Hint-use annotations separately identify ignored terminology, awkward phrasing and unrelated-sense override. These judgements are unblinded, non-human research assessments, not independent linguistic evidence.

The small controlled prompt pilot is two templates × two Qwen3.5 sizes × 16 difficult cases = 64 outputs. `prompts.py` records both templates exactly. Concise was selected on the overall risk mix, including fewer combined critical/major pilot failures; it did not dominate every criterion. Qwen2.5 uses the same policy and content envelope, with its own official ChatML assistant prefix. No case-specific output substitutions, forbidden-word corrections or benchmark-derived vocabulary are applied.

Qwen3.5 0.8B Q4 versus Q5 uses the same fixed 16-case pilot subset. It is a precision sensitivity check, not a full precision quality study. No FP16 inference was compared; the study cannot establish a quantization-only effect. Greedy, non-thinking generation differs from publisher stochastic recommendations, so conclusions apply to these pinned artifacts and settings.

## Rubric

Each reviewed output has ten explicit dimensions scored 0 (material failure), 1 (partial/awkward), 2 (preserved), or NA. Unreviewed dimensions do not acquire scores. Weighting is:

| Dimension | Weight |
| --- | ---: |
| Meaning | 4 |
| Omission | 3 |
| Invention | 3 |
| Terminology | 2 |
| Context / word sense | 2 |
| Numbers / names / units / dates | 4 |
| Negation / prohibition | 4 |
| Idiom / slang intent | 2 |
| Regional naturalness | 1 |
| General fluency | 1 |

Weighted quality is the applicable-dimension mean expressed as 0–100. The secondary risk score subtracts 5/25/100 for MINOR/MAJOR/CRITICAL and bottoms at zero. **A critical failure scores zero on risk regardless of fluency.** Severity counts and specific failures outrank either mean.

CRITICAL means reversed polarity/prohibition, consequential changed numbers, materially swapped agents/objects, missing major clauses, changed safety instructions or invented instructions. MAJOR covers domain senses, idioms and clearly wrong ambiguity resolution. MINOR covers awkward grammar/register and harmless phrasing changes. Multi-error examples use the highest applicable severity. Omission records missing clauses/ideas; invention records added propositions/attributes. A substituted wrong word is recorded as a meaning/term failure and is not automatically counted in both omission and invention. Small partial omissions/additions receive 1; report counts show both any detected loss/addition and material score-0 counts.

The assessor is Codex, with no qualified-speaker status. Assessments, dimensions and rationales are stored in `evidence/reviews.jsonl`. High fluency scores reflect that model-assisted reading, not established native naturalness. Source-authored facts and outputs are visible together; anchoring and correlated judgement errors remain possible. A later blinded fluent-speaker review is necessary before a shipping quality claim.

## Deterministic and heuristic checks

The integer check mirrors the narrow intent of Pass 3: exact arbitrary-precision standalone ASCII signed integers, including multiplicity and a 128-digit bound. It rejects ambiguous decimals, grouping, dates/times, embedded identifiers, non-ASCII digits and written-out counterparts into NEEDS_REVIEW. No-number sources are NOT_APPLICABLE, not vacuous semantic passes. Equivalent `3:30 PM` → `15:30` and word → digit amounts require semantic review. Matching integers do not certify negation, unit association, number words or meaning.

Heuristics flag commentary/wrappers, reasoning tags, suspicious length and missing name literals. They are candidate review signals, not confirmed omission/invention/obedience rates. Confirmed instruction failures come from reading the embedded-command cases. Native worker failures and token-limit output remain incomplete, not successful translations. No production guard was changed to improve benchmark results.

## Resource and offline protocol

All inference is local CPU on this Windows x64 host with 12 logical processors and 33,687,609,344 bytes of physical RAM. Most runs use four threads. The initial MADLAD run set Rayon to four but its separate quantized barrier pool defaulted to six physical cores; both pools were corrected to four and all 68 reviewed outputs were reproduced byte for byte. Final resource comparisons use the corrected four-thread replay. No GPU backend is selected. OPUS retains its historical no-KV-cache greedy decoder; this pass explicitly allows 128 output tokens (the legacy helper's hardcoded result label still says 96). M2M uses int8/beam 5; quantized T5 and LLMs use greedy decoding, max 128 tokens. LLM context is 2,048 tokens, reset between requests; T5 source cap is 512 tokens, no silent truncation.

Quality sweeps overlap with other preparation/inference work. Their latency is recorded but not used as an uncontended resource frontier. A separate balanced 24-case resource replay runs each selected configuration sequentially, with no other inference/build job active. Load measurement creates fresh models/processes with a warmed filesystem cache. It is not a reboot, cleared-cache storage measurement, or phone cold start.

RSS is sampled at 10 ms for the Python process plus child worker. Shared pages may be counted twice and unsampled transient allocation may be missed. Steady retained working set, sampled peak, approximate CPU seconds/busy cores, prefill/generation timing and LLM generated tokens/sec are retained. Output tokenization differs across models, so tokens/sec is descriptive rather than a common work unit. Downloaded preparation artifacts and installed inference assets are counted separately. ASR/TTS/app memory and Android PSS, thermals, battery, accelerator compatibility and physical device latency are unmeasured.

Downloads occur explicitly before inference. Inference processes run under an inherited OS socket restriction: both public-host canaries fail with Windows error 10013, and the Python audit tripwire reports zero subsequent socket attempts. Native workers have no networking/downloader interface. HF offline flags and local-files-only tokenizers provide additional controls. Python instrumentation alone cannot prove native networking is blocked; the OS process sandbox supplies that boundary. No network-enabled inference escalation or cloud fallback is used.

## Primary category breakdown

Context and terminology are also overlapping tagged subsets; this primary-category table sums to 196.

| Category | Cases |
| --- | ---: |
| address | 2 |
| agriculture | 8 |
| ambiguity | 4 |
| ambiguous | 2 |
| automotive | 12 |
| business | 2 |
| code-switching | 2 |
| command | 2 |
| conditional | 2 |
| construction | 12 |
| context | 32 |
| conversation | 6 |
| date-time | 6 |
| difficult | 2 |
| directness | 2 |
| emergency | 2 |
| false-friend | 4 |
| hospitality | 2 |
| humor | 2 |
| idiom | 4 |
| incomplete | 6 |
| measurement | 4 |
| medical-language | 4 |
| multi-clause | 2 |
| names | 4 |
| negation | 6 |
| numbers-money | 4 |
| obedience | 10 |
| ordinary | 2 |
| phone | 2 |
| politeness | 2 |
| profanity | 2 |
| prohibition | 2 |
| quantity | 4 |
| question | 2 |
| ranch | 4 |
| regional | 14 |
| regional-slang | 1 |
| request | 2 |
| sarcasm | 2 |
| slang | 3 |
| travel | 2 |
| warning | 2 |

There are 43 primary categories. The regional primary category contains 14 cases; explicit regional preferences also appear in other categories and the four tagged original regressions.
