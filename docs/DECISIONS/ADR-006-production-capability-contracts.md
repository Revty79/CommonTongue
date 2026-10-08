# ADR-006: Model-neutral capabilities and structured translation results

Status: Accepted in Pass 3.

## Context

Pass 2 proves offline inference but exposes meaning errors. String-to-String hides language, support, completion, uncertainty and verification. Mirroring research runtimes would hinder replacement/model-free testing.

## Decision

Add one pure Kotlin/JVM core:translation module between app/domain, owning suspending capabilities and text orchestration. Domain owns language/direction/quality-profile values. Requests/results/context/policy/quality/failure values are neutral immutable snapshots. Results distinguish candidates, support/evidence, review and failure; confidence is unavailable unless legitimately measured.

## Consequences

Fakes test the whole text flow without weights. Concrete adapters remain future work. No full audio chain, provider registry, persistence, glossary store, translation UI or tier/model mapping is added. Core dependency/import allowlists run in CI. Research/evidence remain separate and intact.
