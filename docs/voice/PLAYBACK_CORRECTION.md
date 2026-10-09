# Pass 6 S25 playback correction

Pass 6 remains open for physical playback confirmation. Pass 7 has not begun.

## Reported regression

The S25 export for `af383eaa1548fc677327f3163f72a0e2f11de94a` reports successful offline voice selection and synthesis, but 16 failed playback attempts, including replay and rapid-latest playback. It contains no `PLAYBACK_START` or `PLAYBACK_COMPLETE` events. All 25 selected-voice diagnostic events report `requires_network = false`. Android API 36, radios enabled. The original export is retained locally, outside Git; its SHA-256 is `06747f9020994699d4397ad1c5fb465337f3204ffebb22271783247464e4f426`.

## Comparison with accepted Pass 5

Both paths use installed offline TTS, a PCM16 WAV and `AudioTrack.MODE_STATIC`. Pass 5's `OfflineSpeech.kt` writes PCM first, then requires an initialized track and a complete write. The new production player checked `STATE_INITIALIZED` **before** writing, using a short-circuit `||` expression. A correctly created empty static track instead reports `STATE_NO_STATIC_DATA`, so this guard prevented the write and failed before `play()`.

[Android's AudioTrack reference](https://developer.android.com/reference/android/media/AudioTrack#STATE_NO_STATIC_DATA) documents that state. The correction restores write-then-validate ordering, including full-byte-count validation. It retains static playback, media/speech audio attributes, audio focus, noisy receiver, route protection, completion thresholds and cleanup. Voice selection, TTS synthesis, WAV parsing, Whisper, MADLAD, model assets, native translation and Pass 5 code are unchanged.

This is a source-identified defect reproduced by a regression test, not yet a claim that the corrected APK has played on the S25. The original export did not record the track's initial state; the new export does.

## Privacy-safe playback evidence

Export schema 2 retains the existing synthesis/start/complete events and adds `PLAYBACK_DETAIL`. `playback_step` identifies PCM format, focus request, noisy receiver registration, track construction, routing listener registration, PCM write, post-write state validation, `play()` invocation, initial head, subsequent advancement and completion. Started/succeeded/rejected outcomes distinguish a call that never returned from a successful call.

Numeric metadata records PCM byte count/channels/sample rate, focus result, track state/play state, write return count, elapsed milliseconds, playback-head frames and routed device **type**. A timeout explicitly records `TIMED_OUT`; a thrown Android exception records `ANDROID_EXCEPTION` and a fixed exception-type enum. Messages, causes, traces, paths, text, audio and device addresses are never exported. Cancellation remains cancellation. Head samples are bounded to the first advancement, once per second and completion; the debug screen retains at most 512 diagnostic events.

## Validation and rerun

Eight new JVM tests exercise static state promotion after write, full/partial/negative/zero writes, a track that remains unready, privacy-safe exception classification and distinct typed-failure/timeout diagnostics. Before the ordering correction, the empty-static-track test failed and the other seven passed. After the correction all eight pass. Existing orchestration tests alone had mocked the audio player and did not exercise this Android initialization boundary.

The local full Gradle sequence passes clean, test, lint, spotlessCheck, verifyCoreBoundaries, :app:verifyFoundationManifest, assembleDebug and :app:assembleDebugAndroidTest. All 138 JVM tests pass (app 2, domain 10, translation 89, Android speech 37). The installed-TTS instrumentation APK compiles; it was not executed on hardware. All 113 unchanged model-free research-tooling tests pass. Actionlint passes for both workflows. The baseline guard verifies 285 protected inputs, and a separate local snapshot confirms 427 research/tooling files are byte-identical. Exact-commit CI remains pending push at source-commit time.

The correction debug APK uses `com.commontongue.prototype.debug`, version code 7 / `0.6.1-pass6-dev`, and the existing local debug signer. APK v2 verification passes; certificate SHA-256 `129bb8734d002798d508b78621270cf6fdb3b0c91e0f4759bf70ccf8798001fb` matches the previously published APK. Install as an update; do not uninstall, reinstall voices or reimport models. It retains no INTERNET permission and includes no model pack or vendor voice assets.

On the phone speaker, tap **Run English + Spanish checks**. This correction's batch runs only English playback, Spanish playback and Spanish replay. Then export results and report audibility for those three attempts. Wi-Fi/cellular may remain enabled. Successful focus/write/play/head advancement/completion events plus human audibility are required before closing the playback regression. Headphones/Bluetooth physical coverage remains unavailable.

The exact source commit, CI result, APK checksum, signing certificate and direct prerelease download are recorded in the repository-associated correction artifact receipt. The original failed test release remains available as historical evidence.
