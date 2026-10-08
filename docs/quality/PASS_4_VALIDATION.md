# Pass 4 validation

Baseline: approved main `a6d5a1089e3f1727757feddc6bc31d34ca883d80`. This pass adds isolated research, evidence, documentation and a model-free Python CI step. It does not alter production source, permissions, SDK requirements, app/core dependencies, existing Pass 2 research files or the Pass 3 contracts.

## Local build and checks

The full offline Gradle command completed successfully in 2m 7s with 118 actionable tasks:

```powershell
.\gradlew.bat --offline --no-daemon --console=plain --warning-mode=all '-Pkotlin.compiler.execution.strategy=in-process' clean test lint spotlessCheck verifyCoreBoundaries :app:verifyFoundationManifest assembleDebug :app:assembleDebugAndroidTest
```

JDK 17.0.20.1, the pinned Gradle/AGP/Kotlin toolchain, SDK 36/build-tools 36.0.0 and the process-local Windows PATH cleanup documented in README were used. No toolchain or production dependency update was made. Existing version advisories remain informational. Final documentation receives another spotlessCheck after authoring; no broader app tests are rerun without a product change.

| Check | Result |
| --- | --- |
| JVM tests | 101 passed, 0 failures/errors/skips, ten JUnit XML reports |
| Existing Pass 2 model-free research tests | 6 passed |
| New corpus/scoring/metadata/blinding/artifact/evidence tests | 42 passed, no models or third-party imports required |
| Android/core lint and Kotlin/text formatting | Passed |
| Core dependency/import boundaries | Passed |
| Debug/release merged manifest rules | Passed; no production Internet permission |
| Debug APK assembly | Passed |
| Android UI-test APK compilation | Passed; no instrumentation/device execution claimed |
| Workflow syntax | actionlint 1.7.12 passed; ShellCheck unavailable and explicitly disabled |
| Diff whitespace/scope | git diff --check passed; app/core/Gradle/Pass 2 source diff empty |
| Research assets/package audit | No weights, model binaries, native binaries, wheels, archives or caches in added research files; no research model/runtime entries in debug APK |

Total automated local tests: **149**. The extra evidence tests verify actual run completeness, comparable review-panel coverage, failure-count recomputation and that identical ablation pairs cannot be marked improved. They caught and corrected one mistaken identical-pair label during analysis.

## Executed research

All nine full dedicated/direct/hybrid configurations completed 196 cases; the verifier/one-repair configuration completed the fixed 68-case panel. Six configurations completed all 32 context and 28 terminology ablations. The two-template pilot completed 64 outputs; the Q4/Q5 sensitivity control 16. Compact actual outputs, configuration metadata, scores/rationales and summary checks are committed. The model-free analysis command reproduces the committed counts.

57 downloaded/converted artifact size/digest checks passed. The native llama.cpp stdio helper and official simple example built and exercised the pinned artifacts, with byte-identical output on the recorded parity case. The CPU Candle T5 helper built with Rust 1.91.0/Cargo.lock and performed local inference. Toolchain/model caches remain ignored.

The resource study runs six configurations sequentially over 24 balanced cases after all other benchmark/build jobs end. MADLAD's initial quantized barrier pool defaulted to six physical cores despite Rayon=4; the helper now sets both pools=4. Its reviewed 68-case rerun and corrected 24-case replay reproduce every corresponding output byte for byte. The final performance table uses corrected measurements, and retains the earlier control/configuration caveat. The sequential hybrid unload/reload study completed three fresh loads per direction on two utterances. These are desktop observations, not Android memory/latency or real-phone evidence.

Network canaries fail with Windows 10013 under the OS process sandbox, including final replays and the corrected MADLAD run. All post-tripwire socket events are empty. Native workers read prepared local files with no downloader/server/cloud-fallback interface. No external inference/grading API, paid tooling, account/trial creation, online competitor benchmark or production cloud SDK was used. Semantic ratings are Codex-assisted within this coding session and explicitly not native/fluent-human review.

Public blinded preview and the export mechanism are committed; the full sheet and separate mapping key were generated under ignored `.local/quality/`. No fake completed human review is asserted. No new ASR/TTS selection, Android model adapter, physical-phone trial or later-pass functionality is implemented.

## Remote CI

The workflow retains all existing Gradle/foundation checks and the six Pass 2 stdlib tests, adding only the 42-test benchmark suite. Normal CI remains model-free, with no research-model download, credentials or native research build requirement. The single authorized Pass 4 commit is pushed to main after local validation; its actual commit SHA, Actions run URL and outcome are reported in the completion response. This file records local evidence and does not predict a remote success.
