# ADR-002: Offline-first translation architecture

Status: Accepted as a future architecture requirement; translation is absent in Pass 1.

## Context

The product must remain useful after required language/model packs are installed even when a phone has no connectivity. Speech/text privacy and predictable behavior must not depend on a backend.

## Decision

Core translation must eventually run fully offline without transmitting user speech/text off-device. Keep optional cloud functionality separate and require deliberate user selection. Never silently fall back to the cloud. Conversations are to be ephemeral by default; future persistence must sit behind abstractions.

## Consequences

The current foundation needs no network permission, backend, account, model downloader, or database. Later pack provisioning and any optional online features require separate scope and documentation. Offline behavior must eventually be tested on supported hardware. This ADR records constraints and does not imply an offline translation engine already exists.
