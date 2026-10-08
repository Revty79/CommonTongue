# Pass 3 validation

Validated October 7, 2026, in `D:\CommonTongue` on Windows, starting from approved main `626693e3b844665676b2508b1497c0d0ccd0625f`. Its [Pass 2 GitHub Actions run](https://github.com/Revty79/CommonTongue/actions/runs/37701581189) was independently confirmed successful before this work.

This is a local validation record. The authorized single Pass 3 commit is published after these checks; its SHA and actual new GitHub Actions result are reported in the completion message. This document does not predeclare a remote result. The [normal workflow](https://github.com/Revty79/CommonTongue/actions/workflows/android.yml) runs the same Gradle checks plus the six lightweight Python tests.

## Scope and dependencies

One module was added: pure Kotlin/JVM `:core:translation`. Domain gained language identity, direction and quality-profile values. The app's only implementation change is its module dependency; the foundation UI, Android permissions, SDK requirements and product behavior are unchanged.

```text
:app
  -> :core:translation
       -> :core:domain (public API)
```

The resolved core production/test graphs are verified by `verifyCoreBoundaries`. Domain production uses Kotlin standard library and JetBrains annotations only. Translation adds the existing version-catalog coroutines core/JVM dependency and its BOM. Test-only libraries are JUnit, Hamcrest and coroutines test/JVM. No dependency version/catalog change, Android library in either core runtime graph, network client, cloud SDK, model/runtime or model asset was added. The Android lint plugin is build-time analysis, not a core runtime dependency. Both core modules target JVM 17; the new Java-library module explicitly pins Java source/target 17.

Production capability boundaries are SpeechRecognizer, Translator, SpeechSynthesizer, LanguageDetector, TranslationVerifier, TranslationNotesAnalyzer and CriticalContentAnalyzer. Values include immutable language/direction, region, bounded recent context, terminology, domain, policies, support reports, honest confidence, critical expectations and structured issues/failures. TranslateTextUseCase performs text-only orchestration with injected capabilities. ExactIntegerVerifier is the sole new concrete verification implementation; it has no inference dependency.

No production ASR/translation/TTS/detection/notes adapter, audio pipeline, UI, persistence, engine selection or quality-tier mapping was implemented. No AI model was downloaded for Pass 3. Pass 2 research and evidence under `spikes/`, `tools/` and `docs/feasibility/` have no diff from the approved baseline.

## Executed checks

The full local validation ran with cached free tooling, JDK 17, the existing SDK 36 and `--offline`. Kotlin compilation used the in-process strategy to keep local compilation inside the workspace rather than the Windows user's external daemon directories.

```powershell
.\gradlew.bat --offline --no-daemon --console=plain '-Pkotlin.compiler.execution.strategy=in-process' clean test lint spotlessCheck verifyCoreBoundaries :app:verifyFoundationManifest assembleDebug :app:assembleDebugAndroidTest
python -m unittest discover -s tools/offline-feasibility/tests -v
```

| Check | Result |
| --- | --- |
| `clean` | PASS; full rebuild |
| `test` | PASS; 101 JVM tests, zero failures/errors/skips |
| `lint` | PASS; zero errors; zero issues in either core module; six app version/SDK advisories |
| `spotlessApply` / `spotlessCheck` | PASS; Kotlin, Kotlin DSL and configured text |
| `verifyCoreBoundaries` | PASS; both core production/test dependency graphs and source imports |
| `:app:verifyFoundationManifest` | PASS; debug/release permissions, minSdk 26 / targetSdk 36 |
| `assembleDebug` | PASS; development APK compiled/packaged |
| `:app:assembleDebugAndroidTest` | PASS; instrumentation APK compiled/packaged |
| Python research harness | PASS; six stdlib tests, no model dependencies |
| Workflow syntax | PASS; actionlint 1.7.12, exit 0 |
| `git diff --check` | PASS |

The app's six lint advisories remain visible, with no suppression/baseline and no SDK/dependency upgrade. The deliberate toolchain pins were documented in [Pass 1 validation](PASS_1_VALIDATION.md); advisory counts can change with cached update metadata. Debug native-library symbol stripping also produces the existing AndroidX packaging advisory, not a build error. UI instrumentation was **not executed** in Pass 3; its APK was compiled. No connected-device, release-build or expensive Pass 2 model benchmark rerun is claimed.

## Unit-test counts and evidence

| Suite | Tests |
| --- | ---: |
| Domain: 9 new identity/direction/profile tests plus 1 existing availability test | 10 |
| Translation: contract types | 17 |
| Translation: request/context/terminology/policy | 15 |
| Translation: deterministic integer verifier | 15 |
| Translation: offline execution | 9 |
| Translation: other capability fake contracts | 5 |
| Translation: cancellation | 4 |
| Translation: text use case | 24 |
| Existing app ViewModel | 2 |
| **JVM total** | **101** |
| Existing Python harness | 6 |
| **Executed unit-test total** | **107** |

There are **98 new JVM tests** (9 domain + 89 translation). Tests exercise canonical equality/invalid identifiers and direction, immutable collection snapshots, bounded context/overflow, regional preferences, opaque audio/voice tokens, all profile values, confidence unavailable versus legitimate evidence, independent severity/certainty, required terminology and unsupported policies/features, all expected failure categories, and fake recognition/detection/synthesis/notes contracts.

Use-case tests cover no context, independent verification states, partial/wrong-direction candidates, operational versus semantic failure, candidate/findings preservation, counterfeit translator verification, scoped integer versus decimal/semantic coverage, required terminology expectations and unexpected-fault propagation. Offline tests prove preflight of both selected capabilities before text delivery, rejection of forbidden/undeclared actual execution, explicit online permission and accurate overall execution when either stage is online. No fallback capability exists.

Cancellation tests cancel fake long-running translation and verification, verify cleanup and prevent subsequent stages. A swallowed cancellation is still detected at the orchestration boundary. Coroutine cancellation propagates; it is not converted into a normal failure or successful result. This does not claim arbitrary blocking/native engines can be interrupted without future adapter work.

Integer tests verify explicit standalone ASCII integer values and multiplicity, including reordering, changed/extra/missing repeated values and normalized signs/zeros. Written-out numbers, localized decimals/grouping, dates/times/ranges, embedded/non-ASCII digits, missing/incomplete expectations and unsupported semantic content require review. A scoped PASS cannot certify meaning or negate the need for semantic verification.

## Architecture guard rejection probes

Two temporary local probes deliberately caused the guard task to fail:

1. An otherwise absent core source importing `androidx.compose.runtime.State` was rejected as provider/platform leakage. The exact temporary file was removed in cleanup.
2. A local ignored Gradle init script injecting cached JUnit into the translation **production** graph was rejected as an unapproved core dependency. JUnit remains permitted only in test graphs; the normal build uses no such init script.

Both are successful negative tests, followed by the clean full normal validation. The guard checks approved resolved dependencies, project edges, unsupported dependency declarations, source imports and known platform/provider/I/O references. It is a practical architecture check, not a security sandbox or proof against a deliberately dishonest adapter.

## Decisions and remaining evidence

Updated README, ARCHITECTURE, MODEL_POLICY and ADR-003; added [TRANSLATION_CONTRACTS](TRANSLATION_CONTRACTS.md), [QUALITY_ARCHITECTURE](QUALITY_ARCHITECTURE.md), this record and ADRs [006](DECISIONS/ADR-006-production-capability-contracts.md), [007](DECISIONS/ADR-007-independent-scoped-meaning-verification.md), [008](DECISIONS/ADR-008-explicit-offline-execution.md). ADR-006 combines capability contracts and structured results; the other decisions isolate verification and forbid silent online fallback.

The chosen language syntax is a documented two/three-letter-base BCP-47 subset, not a registry or engine-support claim. Same-language regional rewriting is outside TranslationDirection. The deterministic check is deliberately narrow; unimplemented semantic checks retain review/unchecked states. The future quality rubric separates ASR, translation, verifier and TTS errors and does not invent human review or automatic semantic scores.

Pass 2's measured translation quality remains insufficient for release, and low-end physical-phone viability remains unproven. These contracts do not change either finding. No Pass 4 experimentation was started.
