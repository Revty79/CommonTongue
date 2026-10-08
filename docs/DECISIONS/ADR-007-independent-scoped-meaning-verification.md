# ADR-007: Independent, scoped meaning verification

Status: Accepted in Pass 3.

## Context

Grammatical output/supported policy flags do not prove preserved meaning. Numbers, negation, terminology and omitted clauses need different evidence. A simple lexical guard cannot prove multilingual correctness.

## Decision

Keep TranslationVerifier independent of Translator with scoped statuses/findings/unchecked content. Define critical-content identification and semantic expectations separately. Implement only an opt-in exact integer multiset baseline; ambiguous forms and unsupported semantics require review. Required terms become expectations. The text use case cross-checks scope and preserves failed/review candidates.

## Consequences

Integer-only PASS cannot certify decimals/negation/currency/dates/names/safety. No back-translation, LLM, sarcasm detector or automatic semantic scorer exists. Future UI must distinguish narrow checks, unreviewed meaning and critical uncertainty. Verification may be replaced without changing translation contracts.
