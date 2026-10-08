# ADR-003: AI models are replaceable implementation details

Status: Accepted. Model-neutral interfaces/text orchestration are implemented in Pass 3; concrete production adapters remain deferred. The original Pass 1 context/consequences below remain historical.

## Context

Model quality, licensing, hardware support, and resource costs can change. UI/domain code coupled to a model or runtime would make replacements expensive and ordinary tests dependent on large artifacts.

## Decision

Future dependency direction is UI to application/domain capability to AI capability interface to replaceable implementation. Platform/model-specific dependencies belong in adapters. Provider/model names must not become core domain concepts. Apply the model adoption policy before accepting a shipping model.

## Consequences

Future capabilities can be tested with small fakes and can support different device implementations. Domain remains independent of CPU/GPU/NPU and model brands. No model, runtime, capability interface, fake translator, or implementation-selection system is created in Pass 1.

See [ADR-006](ADR-006-production-capability-contracts.md), [ADR-007](ADR-007-independent-scoped-meaning-verification.md) and [ADR-008](ADR-008-explicit-offline-execution.md) for the Pass 3 contract/verification/execution decisions. Pass 2's research stack remains isolated.
