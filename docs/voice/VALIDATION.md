# Pass 6 validation checkpoint

Implementation checkpoint; **physical S25 acceptance is pending**. Pass 5's accepted v7 proof is not reused as a physical pass for this new production adapter. The user can test the phone speaker; physical headphones/Bluetooth coverage is unavailable and remains a documented concern. Pass 7 has not begun.

The first production S25 trial at `af383eaa1548fc677327f3163f72a0e2f11de94a` subsequently confirmed offline synthesis/voice selection but failed every playback attempt. See [the narrow playback correction and required rerun](PLAYBACK_CORRECTION.md). The following counts describe that original implementation checkpoint; later correction results are recorded separately in the correction build receipt. Pass 6 is not accepted yet.

## Executed locally

All 130 JVM tests passed, without failures/errors/skips: app 2, domain 10, translation 89, Android speech 29. The 29 voice-layer tests cover voice/provider/region policy, network/missing-voice rejection, PCM16 validation, replay/revocation, caller cancellation, replacement with delayed cleanup, stale replay release, cancellation-safe close, lifecycle, privacy, structured focus/route failures and latency measurement boundaries. These fakes test orchestration/policy; they do not prove hardware routing or Android service behavior.

All 113 existing model-free Python tests passed: feasibility 6, translation quality 42, physical-trial tooling 65. Both workflow files pass actionlint 1.7.12; ShellCheck is unavailable and disabled for that check. The packaging helper validates the actual merged manifest and APK ZIP, copies the APK under the tester name and records its SHA-256/requirements. Signature verification passes Android APK Signature Scheme v2.

The full Gradle sequence passes: clean, test, lint, spotlessCheck, verifyCoreBoundaries, :app:verifyFoundationManifest, assembleDebug and :app:assembleDebugAndroidTest. A final incremental sequence after the explicit API-31 BLE constant guard also passes. Debug and release merged manifests contain no network/microphone permission; the only permission is AndroidX's app-scoped internal signature permission. TTS service discovery is verified in both manifests.

Production speech lint reports only the existing deliberate SDK-version advisory. The app also reports existing pinned dependency/target advisories and English-literal warnings in the debug-only test screen. SDK/dependency pins are retained. An initial sandboxed javac attempt could not resolve installed SDK archives; normal SDK access resolves this and the complete build passes. No product workaround or tooling upgrade was needed.

The guard verifies 285 recorded model/research/native inputs against the accepted, SHA-pinned Pass 5 source inventory. A separate local comparison confirms all 427 frozen research/tooling files are byte-identical. Whisper, MADLAD, model pack assets, native runtime and research implementation are unchanged. The debug APK contains no translation model/runtime or vendor voice assets; it retains the inherited AndroidX graphics-path library for its existing Compose UI and the coroutines DebugProbes class resource.

## Device integration and evidence still required

The actual installed-TTS integration test APK compiles. It has **not** been executed on the S25 during this checkpoint. The debug-only Voice Check Activity exercises the same production adapter through `SpeechSynthesizer`, not the old research TTS class. New EN/ES synthesis/playback, no-network voice flags, frame evidence, replay, cancellation/replacement, background return and human audibility results must be reviewed before closing Pass 6. Airplane mode is optional offline proof, not an operating gate.

## Remote validation

At source-commit time the exact-commit Actions run is pending push. The workflow runs all production Gradle checks, model-free research tests, unchanged-baseline verification and APK packaging, then uploads the named Pass 6 test artifact and speech test/lint reports. The exact commit, run URL/result and final APK checksum are reported after push in the repository-associated artifact receipt. CI success does not replace S25 acceptance.

See [implementation and remaining concerns](PASS_6_OFFLINE_VOICE_LAYER.md), [first-party legal review](LICENSING_PROVENANCE.md), and [plain-English phone instructions](../../tools/voice-layer/START-HERE.txt).
