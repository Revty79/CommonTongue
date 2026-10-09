# S25 offline speech diagnostics

Status: **ALL FOUR v7 TTS CHECKS AND THE FULL S25 PIPELINE PASS** in the user-reviewed closeout result. The installed engine is com.google.android.tts with offline en-US/es-US voices; both microphone directions and prerecorded checks reach TTS_COMPLETE. Keep the existing v7 APK and pack. The failure narrative below describes v6 history; the procedure remains useful for follow-up. See [accepted baseline](PASS_5_CLOSEOUT.md).

The v6 S25 export confirms `TRANSLATION_COMPLETE` after tokenizer, encoder, decoder and first-token checkpoints on prerecorded and real microphone EN→ES turns. The latest prerecorded check and two microphone turns then reach `TTS_START` and fail within approximately 65–152 ms. The old export redacts the error without retaining its code/type, so the engine, voice configuration, synthesized file and playback cannot yet be distinguished. Earlier component/exit records in the same export are historical; its old MADLAD-only WORKER_EXIT is not a new v6 result. Successful translation completion supports the v6 handle correction; it does not establish translation quality or audible full-pipeline acceptance.

## Install and check

Download **CommonTongue-Pass5-S25-TTS-Diagnostic-Update.apk** from the [v7 research update](https://github.com/Revty79/CommonTongue/releases/tag/pass5-s25-tts-diagnostic-2026-10-08). Open it and choose **Update**. Keep the installed app, pack and voices. No uninstall, pack download/import, microphone permission, computer or ADB is needed for the speech-only check.

1. Open Common Tongue and wait for offline speech preparation to finish. Set media volume to an audible level.
2. Tap **Check offline speech (English + Spanish)** once. Listen for four short fixed phrases: direct Android English, direct Android Spanish, app WAV English, app WAV Spanish. These checks do not load or call Whisper/MADLAD; an already resident worker may remain idle.
3. Wait for **Speech check complete**, then tap **Export Research Results**. Tell the coordinator which of the four phrases were audible. A recorded PASS means the corresponding completion/evidence checks succeeded; acoustic audibility still requires the tester.
4. Run **3. Check the full recorded-sample pipeline** once afterward and export again. Try a microphone turn only if the full check succeeds.

Wi-Fi/cellular can stay on. The explicit offline-proof trial uses airplane mode and radios off after setup; it is separate from ordinary operation. Cancellation or backgrounding stops an active speech check, marks it CANCELLED and prevents pending checks from resuming. Existing model cancellation/unload behavior is retained.

## What is measured

Normal translator output retains **synthesize-to-file → PCM WAV validation → AudioTrack playback**. It does not call Android `speak()`; its `speak_result` is null, meaning NOT_CALLED. The isolated `DIRECT_SPEAK` checks use Android `speak()` and record that method's return result separately. Neither route uses a network-required/uninstalled voice, retries with a network voice or changes the installed engine. No new engine/model is bundled.

Every operation records engine initialization/result, bounded engine name, requested language, selected/active voice and locale, both network flags, `isLanguageAvailable`, `setLanguage`, `setVoice`, active-voice match, listener-registration result and actual enqueue return code. Voice configuration reapplies the chosen offline voice after `setLanguage` and verifies it before enqueue. Android's [TextToSpeech API](https://developer.android.com/reference/android/speech/tts/TextToSpeech) documents the language/voice return codes and asynchronous enqueue contract.

The listener records START, DONE, ERROR and STOP, the integer Android error code, reported synthesis format, and audio callback byte/chunk totals. No callback audio bytes are retained or exported. `onAudioAvailable` is synthesis evidence, not playback evidence; Android documents these distinctions in [UtteranceProgressListener](https://developer.android.com/reference/android/speech/tts/UtteranceProgressListener). `onStart` for DIRECT_SPEAK is labeled `ENGINE_ON_START`: engine-reported playback only, without a calibrated acoustic observation.

For the normal file route, additional checkpoints identify WAV read/format, AudioTrack initialization/write, playback request, playback start and completion. WAV diagnostics contain size, encoding, sample rate, channels and bit depth. The existing supported contract remains PCM16 mono/stereo at 8–48 kHz. Playback begins only when an AudioTrack timestamp or playback head advances, labeled `AUDIOTRACK_TIMESTAMP` or `AUDIOTRACK_HEAD`; the existing leading-silence estimate remains separate. Even this evidence does not prove that the speaker was audible to a person.

Finite `TTS_*` failure codes distinguish preparing/missing voice, language/voice configuration rejection, enqueue/listener error, timeout, invalid/unsupported/silent WAV, playback failure and safe exception type. Raw exception text, utterance IDs, absolute paths, transcript/translation, audio and device identifiers are excluded. The export includes `tts_engine_init`, `last_tts_diagnostic`, `last_tts_failure`, `tts_checks` and timestamped `tts_diagnostic` events. Historical metrics remain historical rather than being assigned the current app version.

## Interpret the result

| Observation | Next boundary to inspect |
| --- | --- |
| Initialization fails or no installed offline voice | Installed default engine / offline voice setup |
| Language or voice configuration returns an error | Engine's locale/voice integration |
| Enqueue returns ERROR | Speech-engine service / request acceptance |
| Listener ERROR with Android code | Reported synthesis/output/voice-data failure; retain exact code |
| Direct speech succeeds but file synthesis fails | Engine's file-synthesis support or file output |
| File synthesis completes but WAV validation fails | Reported format/file size and parser code |
| Valid PCM but AudioTrack never advances | App playback integration / device audio output |
| Both isolated routes succeed but full pipeline fails | Compare target language/voice and TTS events for the actual turn |

Version 7 preserves all four v6 native libraries byte for byte, the model pack/contract, Whisper/MADLAD paths, dependencies and permissions. It requests RECORD_AUDIO only, with no INTERNET permission. APK SHA-256, signer, exact models/runtimes, tests and publication checks are recorded in [TTS update evidence](../../tools/device-trial/evidence/s25-tts-diagnostic.json) and the release's S25-TTS-Diagnostic-Details.json. The later user-reviewed v7 result passes the isolated checks and physical pipeline. Pass 5 closes on this scoped S25 proof; broader conversation/device/sustained/lifecycle work is follow-up. Publication-time evidence remains unchanged, and the final source commit/CI is reported separately. No new APK or Pass 6 implementation is made for the closeout.
