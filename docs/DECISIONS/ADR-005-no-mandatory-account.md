# ADR-005: No mandatory Common Tongue account

Status: Accepted for the initial product architecture.

## Context

An offline translation product should not require remote authentication to start or use its core capability. Accounts add backend, privacy, availability, and maintenance requirements that are unnecessary for the foundation.

## Decision

Do not require a Common Tongue account for the initial offline product architecture. Pass 1 contains no authentication, account SDK, user database, payment system, or cloud service. Any later optional online feature must remain separate from core offline operation.

## Consequences

Application startup and future offline translation remain independent of sign-in or backend availability. Optional future account features need their own scope and decisions. This does not implement accounts, purchases, or cross-device synchronization.
