# Pass 6 closeout: production offline voice on the S25

The Pass 6 implementation and requested S25 phone-speaker proof are complete. The corrected production adapter passes English, Spanish and replay, and the tester explicitly confirmed hearing all three. This closeout records the engineering evidence for user review; Pass 7 has not begun and is not authorized.

## Reviewed physical evidence

The reviewed schema-2 export identifies `0.6.1-pass6-dev` at `5af514dd4f1d19aa0bfceda3ffbd07d1f2586f08`, Android 16 / API 36. Its SHA-256 is `090aa16f27f2eea5ff4352193162281795b68b93215d433d4554d0c4a085407b`. The raw export stays outside Git. The [bounded evidence summary](../../tools/voice-layer/evidence/s25-v7-playback.json) records numeric checkpoints and selected engine/voice identities without private paths, exception messages, speech text, audio or device identifiers.

The export records normal operation with airplane mode **off**. Both Google and Samsung engines initialize successfully. Google supplies the selected `en-US-language` / `en-US` and `es-US-language` / `es-US` voices; all five voice-selection events report `requires_network = false`. The adapter remains provider-neutral. This proves the selected installed voices on this device, not physical playback through every discovered engine.

| Required case | Result |
| --- | --- |
| Manual English playback | PASS / OFFLINE |
| Manual Spanish playback | PASS / OFFLINE |
| Automated English playback | PASS / OFFLINE |
| Automated Spanish playback | PASS / OFFLINE |
| Spanish replay | PASS / OFFLINE |

All six playback starts receive audio focus (`1`), register the noisy receiver, construct a static track, write the entire PCM buffer, validate the initialized state and successfully call `play()` (`playState = 3`). The PCM is mono PCM16 at 24 kHz; English buffers contain 186,480 bytes and Spanish buffers 201,544 bytes. Routing reports built-in speaker type `2`. Each required completed turn advances its playback head to the full buffer length: 93,240 English frames or 100,772 Spanish/replay frames. There are **five `PLAYBACK_COMPLETE` events and zero playback failures, timeouts or Android exceptions**.

Every construction reports state `2` (`STATE_NO_STATIC_DATA`); every full write reports state `1` (`STATE_INITIALIZED`). This directly supports the [write-before-readiness correction](PLAYBACK_CORRECTION.md) against the physically proven Pass 5 reference. No focus, routing, attribute, synthesis or model change was necessary.

A sixth, extra manual English turn starts successfully and then reports `CANCELLED`. Its cancellation reason is not exported. The lifecycle code cancels speech when the app loses its visible session, so exporting/backgrounding is a compatible explanation, not an established cause. This extra cancellation does not invalidate the five completed cases and is not classified as a playback failure.

The export's `human_audibility` field is a static request for tester confirmation. The tester's earlier explicit response, **“I heard all three,”** supplies that confirmation separately. Playback-head/request-to-audio estimates are uncalibrated; the clamped zero replay estimate is not evidence of zero acoustic latency. No timing claim is needed to establish successful playback.

## Preserved implementation and validation

The proven path remains `SpeechSynthesizer -> installed offline Android TTS -> temporary PCM16 WAV -> controlled static AudioTrack`. Replay uses the retained local PCM handle; cancellation/replacement, focus, routing and visible-session ownership remain in the production adapter. There is no PTT dependency, bundled neural TTS, vendor voice redistribution or INTERNET permission. The earlier production export established the expected missing-language/unsupported-speaker failures and rapid cancellation/replacement behavior; its failed playback is retained as regression history rather than attributed to this corrected run.

Correction commit `5af514dd4f1d19aa0bfceda3ffbd07d1f2586f08` passes [CI run 37871628399](https://github.com/Revty79/CommonTongue/actions/runs/37871628399): clean, test, lint, spotlessCheck, verifyCoreBoundaries, :app:verifyFoundationManifest, assembleDebug and :app:assembleDebugAndroidTest, plus all three model-free research suites, baseline verification and artifact upload. Local validation passed 138 JVM tests, including eight new initialization/privacy regression checks, and 113 research-tooling tests. The installed-voice instrumentation test APK compiled; it was not run by the coordinator. The phone-only harness supplies this physical result through the actual production adapter.

This closeout changes documentation and a bounded evidence JSON only. It preserves every app/core/platform source, test, permission, dependency, model asset and research/native runtime from the tested correction commit. The accepted Pass 5 baseline remains `5ee45413e3c72493ae3f13ec6f913728828a3e0f`; the existing guard verifies 285 pinned research/native/model inputs, and the local frozen snapshot covers 427 unchanged research/tooling files. Whisper and MADLAD are untouched.

## Keep the tested APK

Keep the current [tested update APK](https://github.com/Revty79/CommonTongue/releases/download/pass6-playback-correction-v7/CommonTongue-Pass6-Offline-Voice-Check.apk). No new APK, uninstall, voice installation or pack import is needed for this documentation closeout. A newly generated CI APK must not replace the physically tested local-signer artifact merely to include the documentation commit.

| Artifact identity | Value |
| --- | --- |
| Application | `com.commontongue.prototype.debug` |
| Version | code 7 / `0.6.1-pass6-dev` |
| Tested source | `5af514dd4f1d19aa0bfceda3ffbd07d1f2586f08` |
| APK SHA-256 | `7d991d09f569c59bd3c79b318a3fe18bd09cb78883289dc2ff7156ac38d8af1d` |
| Signing certificate SHA-256 | `129bb8734d002798d508b78621270cf6fdb3b0c91e0f4759bf70ccf8798001fb` |

Requirements remain Android API 26+, an installed Android TTS engine with installed offline English and Spanish voices, a foreground session and available audio focus. No translation models are required by this voice-only check. The repository-associated prerelease remains an authorized-device debug artifact, not a consumer production release. The documentation commit and its CI result are recorded separately from the unchanged tested APK/source.

## Remaining release concerns and stop boundary

Physical coverage is limited to the S25 phone speaker. Headphones/Bluetooth, older devices, long sessions and explicit new-adapter airplane-mode proof remain follow-up validation; this export is not an OS-wide network-traffic audit. Production signing/application identity, complete production translation wiring, iOS and touchless mode remain outside Pass 6.

The [first-party licensing/provenance review](LICENSING_PROVENANCE.md) keeps commercial invocation, generated-output restrictions, asset redistribution, attribution and network dependencies separate. Provider/device/region-specific commercial invocation and output rights require pre-release legal review. This successful playback result supplies no new redistribution or commercial-output grant.

Stop after this closeout. Review Pass 6 before authorizing Pass 7.
