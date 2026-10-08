# Future blinded human review

Pass 4 prepares the review mechanism. **No native/fluent-human review or blind study has been performed.** Current assessments are unblinded and model-assisted by Codex.

Generate sheets using the model-free `analyze.py` command in the [research README](../../tools/translation-quality/README.md). Supply separate public and private paths, preferably under ignored `.local/quality/`. The public JSON shows each source, direction, optional prior turns, required semantic facts, forbidden changes and translations A/B/C… in a reproducible per-case shuffled order. It omits model/architecture names, runtime configuration, performance, prior scores and verifier feedback. Missing configuration outputs are absent, not fabricated. The private key maps labels to configurations and must remain with the study coordinator.

For a future study, choose a new seed; do not use the committed result report to grade blind outputs. A reviewer receives only the public sheet. They record:

- Reviewer identifier, language proficiency, native region and relevant domain experience.
- For each labelled translation, the ten [rubric dimensions](BENCHMARK_SPEC.md), severity and a short concrete rationale.
- Omitted clauses, invented facts, polarity/agent/number changes and uncertain technical/regional senses explicitly.
- Acceptable alternate wording without imposing one gold phrase.
- An UNCERTAIN/NEEDS_REVIEW status when they cannot assess the domain or regional meaning; never invent a confident score.

For context/term ablations, generate separately shuffled pair sheets and include the prior/hint only as required by the study question. Do not reveal which variant was generated with it. The current public export groups main candidate translations, not a completed ablation study design. Reviewers must not receive both the private key and the public sheet before their ratings are frozen.

Use at least qualified bilingual assessment for meaning, with Mexican/Argentine/Chilean and automotive/construction/agricultural expertise where relevant. Qualifications are recorded, not presumed. Save original ratings before adjudication and report agreement, disagreements and sample size transparently. Only after freezing the reviews should the coordinator join the private key and compute per-configuration results. Keep both unreviewed and uncertain cases out of pass-rate numerators.

The supplied semantic expectations also need fluent-speaker review. They are authored research requirements, not human golds. No medical/legal safety claim may be derived from this language-only corpus.
