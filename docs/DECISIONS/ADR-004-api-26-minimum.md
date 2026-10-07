# ADR-004: Android 8.0 / API 26 minimum for initial compatibility

Status: Accepted for initial compatibility work.

## Context

Low-end and older Android devices are a first-class requirement. The eventual inference engine may impose practical constraints, but there is no evidence from an engine in this foundation pass.

## Decision

Set minSdk to 26, targetSdk to 36, and compileSdk to 36. Do not raise minSdk without explicit approval. Use stable compatible tooling. Document Lite, Standard, and Enhanced as eventual device-quality concepts without implementing profiling or tier execution.

## Consequences

The foundation must compile for and remain runnable on API 26+. API 26 device validation is distinct from compilation and must be reported honestly. Later model/device evidence may motivate a proposed change but cannot silently change compatibility. No benchmark or assumption that every phone runs one model is introduced now.
