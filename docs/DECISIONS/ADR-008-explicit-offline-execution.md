# ADR-008: Offline-required execution rejects implicit network fallback

Status: Accepted in Pass 3.

## Context

A local translator paired with an online verifier could transmit content while appearing offline. Runtime failures must not silently change providers/execution modes.

## Decision

Default requests to OFFLINE_REQUIRED. Implementations must guarantee/reject it before work. Preflight both selected text capabilities before passing content, validate actual modes and provide no routing/fallback. ONLINE_ALLOWED explicitly permits chosen capabilities. Overall successful execution is online if either stage is online. Cancellation is not a fallback trigger or ordinary failure value.

## Consequences

Offline contract tests need no models/network. Future adapters need independent audits; declarations are not an OS sandbox. Production retains its no-Internet manifest. An online implementation/selector requires a later explicit scope/consent decision and offline regressions for local adapters.
