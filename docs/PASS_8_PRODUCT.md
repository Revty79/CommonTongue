# Pass 8 product integration

The single Common Tongue launcher opens the actual one-device translator: hold **English** or **Español**, speak and release. Production capabilities display the real transcript and translation, automatically speak the target language, and retain the latest synthesized audio for replay. The accepted Pass 7 AI stack and Pass 6 voice implementation are unchanged. Pass 8 physical product acceptance is pending.

## Ownership and privacy

`ConversationTurnCoordinator` is a pure JVM application use case behind `ConversationInput`, `SpeechRecognizer`, `Translator`, `SpeechSynthesizer` and `TranslationDestination`. Pass 8 binds input to `MicrophoneInput` and output to `LocalSpeaker`. Different neutral synthesis/destination adapters can later route paired output without UI knowledge of providers, networking or audio paths. No paired connection is implemented.

Each press has a generation token. Replacement cancels and joins old capture/inference/playback cleanup before proceeding; old releases cannot finish a new press. Cancellation remains coroutine cancellation. All requests require OFFLINE_REQUIRED and successes must report OFFLINE. Partial recognition/translation is displayed with a warning and is not silently spoken as complete. Replay uses the production retained handle rather than resynthesizing.

The Activity requests RECORD_AUDIO only on a talk gesture. Granting permission does not start unattended recording: hold again. Denial/permanent denial has plain guidance and a settings path. `MicrophoneInput` captures mono 16 kHz PCM16 with AudioRecord, caps duration/bytes at 30 seconds, and writes a private temporary WAV compatible with the accepted recognizer. Nonblocking reads check cancellation. The recorder closes before recognition; files disappear after recognition, cancellation or failure. A new process clears orphan recordings only in the dedicated private cache. No VAD/background recording.

The ViewModel survives rotation with appearance and displayed text intact. Leaving the Activity cancels the turn, releases inference/voice engines and revokes replay; returning prepares the same resources. Closing/clearing erases text and replay. At most eight recent completed turns exist in memory; they are never serialized or supplied as translation context. No human audio/text enters diagnostics, logs, analytics, acquisition requests or exported results.

Release has RECORD_AUDIO and AndroidX's signature receiver permission, **no INTERNET**. Debug retains the accepted model-only downloader's INTERNET permission behind internal tools. User-content capabilities have no networking or cloud fallback. Radios on/off never gate a turn; airplane mode is a proof condition only.

## Pack reuse and identity

S25 update identity remains `com.commontongue.prototype.debug`, persistent debug signer, version code 9 / `0.8.0-pass8-dev`. Do not uninstall or clear data. `InstalledCoreResources` and debug setup share original `filesDir/local-core`, `debug-core-v1` token and recognizer/translator/tokenizer/configuration files. The same LockedCore identities are streamed/validated before loading. Nothing relocates, overwrites or redownloads a valid pack. Missing resources use Settings → Internal test tools / resource setup, the existing one-action setup, not a full manager.

Requirements remain ARM64/API 26+, `offline-core-en-es-v1`, pinned multilingual Whisper Base Q5_1 and MADLAD400-3B-MT Q4_K, four CPU threads and installed offline EN/ES system voices. [Exact model/runtime identities](LOCAL-AI-ADAPTERS.md) and [provenance](LOCAL-AI-PROVENANCE.md) still apply. Models/vendor voices are not bundled. No models/APKs/keys enter Git. Ordinary CI uses a different debug signer; the direct tester download uses the existing S25 key.

## Shared design

The supplied concepts inform four locally persisted token sets: Clean & Clear (default), Modern Dark, Conversation and Travel. Settings → Appearance → Theme offers previews. They share one screen/coordinator; Conversation changes bubble styling, Travel adds local vector landscape shapes. No network images, fake tabs, flag-only labels or required animation.

Both large PTT controls expose language/target and stay disabled until resources load. Translation is larger than source. State uses words as well as colour; primary text pairs meet 4.5:1 contrast. Text wraps and scrolls at large font scales. Controls expose button labels/state, keyboard press/release and TalkBack start/finish actions. Replay has a large target.

Connected rounded teal/coral Talking Bubbles form the real identity. Foreground vectors fit the adaptive safe area; Android scales to launcher densities. API 33 adds monochrome, API 26 foreground/background and API 31 startup uses the same icon. Exactly one exported launcher; engineering activities stay internal.

## Physical S25 checklist

Install the signed update **over Common Tongue**, retaining data. Wait for “Offline ready · Ready to speak.” First hold asks for microphone access; allow it and hold again.

1. Radios on: hold English, speak naturally, release. Verify real English source, Spanish translation, audible Spanish and replay on the phone speaker.
2. Hold Español, speak real Spanish, release. Verify Spanish source, English translation, audible English and replay.
3. Alternate several genuine turns. Cancel or replace a turn, then complete another. Check stale results and simultaneous recording/playback do not occur.
4. Try all themes, rotation, background during capture/inference/speech and return. Check cancellation/retry and appearance persistence. Restart: resources remain installed without download, one icon.
5. Explicitly turn on airplane mode and repeat both directions/replay.
6. Settings → Export internal diagnostics. Only stage/failure enums and bounded numeric metadata are exported. Separately report what was heard; diagnostics cannot prove human comprehension or quality.

Pass 8 stays open until real-product checks pass. Emulator UI and fixed component controls are not a substitute. Speaker only is available; headphones/Bluetooth remain untested. Accepted translation quality is unchanged. Context, meaning protection, notes, language expansion, pack manager, pairing and later features remain out of scope. Stop after this handoff; no Pass 9.
