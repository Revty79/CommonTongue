# Pass 8 implementation validation and S25 handoff

Status: implementation/test handoff; **physical S25 product acceptance pending**. Pass 7 is separately COMPLETE at `d22b8fa513b79da29857e620affc6485993b4742`, [CI 37879242025](https://github.com/Revty79/CommonTongue/actions/runs/37879242025), with the user-confirmed physical closeout in [PASS_7_CLOSEOUT.md](PASS_7_CLOSEOUT.md). No Pass 9 work.

## Automated checks

| Check | Result |
| --- | --- |
| JVM tests | 208 passed, 0 failures/errors/skips |
| New coordinator coverage | 22 tests: press/release, both directions, actual result mapping, stage order, replay, stale/noncooperative result rejection, replacement, cancellation/retry, foreground/background, missing resources, OFFLINE_REQUIRED, partial source, missing voice, permission states, bounded history, clear/close, content-free diagnostics |
| New microphone unit coverage | 7 tests: permission, start/release, format, cancellation cleanup, maximum length, invalid read, immediate release, orphan cleanup |
| New theme unit coverage | 2 tests: four stable IDs/restoration/default and 4.5:1 primary text contrast |
| Existing regression JVM tests | All retained: domain 10, contracts 89, speech 37, local adapters/installer 32, Android PCM 7, foundation ViewModel 2 |
| API 36 emulator product instrumentation | 6 passed: 2 startup/appearance persistence, 2 PTT gesture/cancel across all skins, 1 resource binding reuse, 1 actual AudioRecord/WAV cleanup |
| Existing installed-voice instrumentation | Preserved/compiled; not counted as executed in this pass. Accepted S25 voice evidence remains unchanged |
| Python regression harnesses | 113 passed (6 feasibility, 42 quality, 65 physical-trial tooling) |
| Formatting/lint/manifest/core boundaries | PASS; approved RECORD_AUDIO added, release has no INTERNET |
| Debug/test/unsigned minified release APK | PASS |
| Frozen inputs | 285 accepted research inputs, all accepted production voice files, 274 accepted local-AI/native/dependency/pack inputs unchanged |
| Workflow syntax | Local actionlint PASS; exact-commit Actions result accompanies the published tester artifact |

JVM totals: domain 10; translation 111; local AI 32; Android local AI 7; speech 37; app 11. The obsolete foundation-only launcher assertion is updated to the authorized product home and accessible EN/ES controls; its ViewModel regression tests remain. Emulator instrumentation does not load model weights or establish physical ARM64 inference. The recorder check automatically captures only on a dedicated emulator, never on an unattended physical device.

Tests use fake capability outputs only as deterministic test inputs; the installed product uses real production adapters and microphone capture. No fixtures or placeholder outputs enter its conversation flow. The Gradle comma-delimited instrumentation filter selected only the startup class on this Windows harness, so the UI package, resource class and microphone class were explicitly executed with AndroidJUnitRunner and their actual counts verified.

Known lint warnings concern deliberate SDK/dependency/ARM64 pins, UI resource qualifiers, optional KTX conveniences and inherited warnings. Errors are not suppressed or baselined. Existing research/toolchain limitations are preserved.

## Delivery and use

Version `0.8.0-pass8-dev`, code 9, same `com.commontongue.prototype.debug` identity and persistent S25 debug signer. Publication is a tester prerelease/artifact, not a public production launch. The artifact includes APK SHA-256, source SHA, CI receipt, exact identity/version/native dependencies and update-signing information. Ordinary Actions debug signing differs; use the direct signed tester download for an in-place update.

**Install over the existing app. Keep its data.** The installed `offline-core-en-es-v1` pack, same private role files and locked hashes are reused. No new download for a valid pack; no APK/model binaries in Git. Requirements and exact hashes remain in [LOCAL-AI-ADAPTERS.md](LOCAL-AI-ADAPTERS.md) and [LOCAL-AI-PROVENANCE.md](LOCAL-AI-PROVENANCE.md). Whisper/MADLAD/native translation/TTS source and decoding are unchanged.

Wait for “Offline ready · Ready to speak.” First hold requests microphone access; allow and hold again. English translates/speaks Spanish, Español translates/speaks English. Replay repeats the latest audio. Settings → Appearance → Theme chooses any of the four styles. Settings retains internal checks/resource setup and content-free diagnostic export.

Follow the [real S25 product checklist](PASS_8_PRODUCT.md): both human directions and replay, alternating turns, radios enabled and explicit airplane-mode proof, cancel/retry, rotation/background, appearance/restart/resource reuse, one icon. Phone speaker is the available physical output. Pass 8 cannot be marked complete until this proof passes.

New Pass 8 TURN events record the Android airplane-mode setting at each checkpoint, without radio identifiers or connectivity gating. This field is **new**; it is not backfilled into Pass 7 evidence. Local/speech events contain bounded timing/memory/frame/network-required/exit metadata; export contains no raw content, path, exception message or device identifier. User confirmation is still needed for audibility and human quality.

Remaining limits: accepted baseline quality/latency/memory, provisional quantized conversion and vendor generated-output legal questions, speaker-only physical scope, no saved conversations/context/meaning protection/notes, no pairing/hands-free/accounts/cloud/pack manager. Release remains unsigned and has no provisioning UI; the narrow debug setup is the authorized tester path until Pass 12.
