# Pass 5 closeout: accepted S25 baseline

Pass 5 is accepted on the successful physical Galaxy S25 v7 proof, as explicitly requested by the user. The scope is a working research baseline, not a production release or broad device/translation-quality certification. Older-device testing, larger bilingual conversations, sustained sessions and further device characterization are follow-up validation, rather than blockers to progression.

The physical findings below come from the user's review of the v7 export supplied in the closeout instruction. They are not a new automated device run. APK, pack, native-library and source identities are independently checked against the published artifacts. The [accepted baseline evidence](../../tools/device-trial/evidence/s25-v7-acceptance.json) separates this reviewed summary from earlier raw exports and publication-time evidence.

## Successful physical evidence

| Check | Accepted v7 result |
| --- | --- |
| Installed pack | Intact, app-private and persistent |
| Whisper / MADLAD | Both load and execute successfully |
| Android TTS initialization | Successful |
| English direct speech / Spanish direct speech | PASS / PASS |
| English file synthesis and playback / Spanish file synthesis and playback | PASS / PASS |
| Real microphone EN→ES / ES→EN | Both reach TTS_COMPLETE |
| Prerecorded full pipeline | TTS_COMPLETE; FULL_PIPELINE = PASS |
| Playback-start evidence | Actual AudioTrack advancement on the file/playback path |
| Selected voices | Installed offline en-US and es-US; requires_network = false |
| Observed engine | com.google.android.tts; observed configuration, not a hard-coded requirement |
| Research permissions | RECORD_AUDIO only; no INTERNET permission |
| Current TTS failure | None reported in the accepted v7 result |

The first post-load human EN→ES turn is reported at approximately **1,758 ms ASR**, **1,920 ms translation** and **4,665 ms release to playback-start evidence**. This is one first-after-load observation, not a warm median/p95 or calibrated acoustic measurement. No further turn counts, exact voice IDs, engine version, radio-state flags or current inference peak are invented from the summary. The user finds the heard voice somewhat robotic but sufficiently natural for Common Tongue; that preference is not a blinded language-quality score.

## Preserve this configuration

Use the existing [v7 research artifact](https://github.com/Revty79/CommonTongue/releases/tag/pass5-s25-tts-diagnostic-2026-10-08), **CommonTongue-Pass5-S25-TTS-Diagnostic-Update.apk**. No new Pass 5 APK is built to close the pass. APK bytes: **45,272,440**. SHA-256: `3eb9bdd9329737666510047c8238007b9a44441424f0fd516aed34a01b2b72d0`.

Application ID: `com.commontongue.spike.device`; versionCode **7**, versionName **0.0.7-pass5-research**; ARM64, minimum API 26, target/compile API 36. The physical S25 is Android 16/API 36. Signing certificate SHA-256: `129bb8734d002798d508b78621270cf6fdb3b0c91e0f4759bf70ccf8798001fb`. Keep the installed app/data, pack and voices. No uninstall, reimport, model download, ADB pairing or radio gate is required.

The unchanged **CommonTongue-Pass5-Test-Pack.zip** is 1,997,338,704 bytes, SHA-256 `11965738b6441df79122d214f164f7f3dbe9c9a6680e8ac65a22a44cba7a6a23`; pack ID `common-tongue-pass5-v1`, manifest SHA-256 `8b4af0bbac8ac897dd8442ba6a4d99b3efe1c33b8460a4383906f7cb1c452318`. The [embedded contract](../../spikes/physical-trial/src/main/assets/test-pack-contract.json) supplies every exact filename, size, hash and publication revision. The APK alone requires that pack to have been provisioned.

The selected configuration remains MADLAD400-3B-MT Q4_K from Google revision `fa184c675da0b5c9e1c8694fccd4e12e2d422094`, using Candle 0.11.0 revision `31f35b147389700ed2a178ee66a91c3cc25cc80d`, CPU/four threads; Whisper Base Q5_1 using whisper.cpp v1.9.5 revision `d1be6fde11ac6e0407606b4e42fe72d34add8037`, CPU/four threads. Rust 1.91.0, NDK 27.1.12297006/API 26 and CMake 3.22.1 build the research runtime. ONNX Runtime Android 1.30.0 and SentencePiece 0.2.2 revision `e0cce7d37b065b5140349dbe12c6bcf6192fdd78` remain OPUS-control components, not the accepted translation brain.

The proven speech route is **installed offline Android TTS → synthesize-to-file → PCM16 WAV → controlled AudioTrack playback**. Direct speak remains a component comparison. All four native libraries and the pack contract/assets in v7 are byte-identical to v6; the closeout preserves v7 research source, model/runtime pins and the production app/core. Models, APKs, signing material and private/human exports remain outside Git. The 14 committed WAV fixtures are fixed synthetic controls only.

## Historical failures and export interpretation

The export is cumulative. Earlier native-link errors, worker exits, CRASH_NATIVE/SIGSEGV and v6 TTS failures belong to earlier research versions/sessions. Retain them as regression history; do not assign them to successful v7 turns or count an old last-failure/exit field as a new v7 failure. The current v7 component outcomes, pipeline completion, TTS diagnostics and user review establish this acceptance. No APK/export-schema cleanup is performed merely to remove history.

Earlier evidence JSON files retain their publication-time pending status and original hashes. This closeout and the new acceptance file record the later successful result instead of rewriting those receipts. The existing v7 release tag/source archive still identifies approved pre-Pass-5 baseline `c0ca3d8b013749556cade59037344b133f05b0f9`; the final Pass 5 source commit and its CI run are reported separately and linked in the repository-associated closeout receipt. The accepted APK is retained exactly, rather than replaced with a freshly signed CI build.

## Follow-up validation

Older hardware, larger/scripted fluent-human conversations, sustained thermal/battery/memory sessions, noisy environments, comprehensive cancellation/replay/lifecycle/audio-route testing and further device characterization remain unmeasured or incomplete. A separately observed airplane-mode/radios-off proof is not inferred from unspecified radio flags in the reviewed summary. The app operates with radios on or off; airplane mode is only an explicit proof condition. No accuracy percentage, lower-resource quality tier, general battery life or broad commercial approval follows from this S25 proof.

## Next pass boundary

Pass 6 is authorized for a provider-neutral production adapter behind SpeechSynthesizer, with installed offline Android TTS as the leading strategy. Preserve the working WAV/AudioTrack route initially; do not replace it with a neural TTS model without concrete evidence. Discover suitable engines/voices without hard-coding Google, reject network-required voices, prefer suitable Latin-American/Mexican Spanish when available, preserve English/replay/cancel and handle repeated requests, focus, audio routes and lifecycle with structured safe failures. Whisper and MADLAD remain unchanged.

Invoking an installed engine and redistributing its voice data are separate questions. Do not bundle, extract or copy Google/Samsung/vendor voices. Pass 6 must document first-party invocation, generated-output use, redistribution, attribution, network/paid-service terms and unresolved pre-release legal review separately. This closeout does not perform that investigation or claim those rights.

The requested sequence is: finish Pass 5 documentation and validation, make **one** source commit with theme `research: validate offline translation on physical device`, push main, verify ordinary model/device-free CI, then stop and report. Substantial Pass 6 implementation starts only after that handoff; Pass 7 remains outside scope.
