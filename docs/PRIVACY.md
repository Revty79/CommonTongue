# Privacy

## Historical Pass 1 foundation

Pass 1 displays a static foundation screen. It has:

- No accounts or sign-in.
- No analytics, advertisements, telemetry, or crash-reporting service.
- No translation, speech recognition, microphone recording, or camera feature.
- No conversation input, transcripts, saved conversations, or database.
- No Internet, microphone, or camera permission.
- No network client or cloud service.

The application collects no user speech/text and sends no user content off-device. These historical statements describe Pass 1; current Pass 8 behavior is documented below. AndroidX may declare an app-scoped signature permission used to protect internal broadcast receivers; it is not a network or sensor permission. The exact merged-manifest permission list is recorded in [Pass 1 validation](PASS_1_VALIDATION.md).

Android backup is disabled for the application. Normal Android package/runtime metadata, build-generated files, and test artifacts are not conversation storage. Developer tools fetch build dependencies, and an emulator's operating system may have its own services; neither is application translation traffic.

## Pass 6 installed offline speech

The production speech module invokes the user's installed offline TTS voices, with explicit network-required rejection and no cloud fallback. That Pass 6 build retained no INTERNET or microphone permission; Pass 8 adds scoped microphone capture below. Wi-Fi/cellular state does not gate speech. Android's engine process belongs to a separate installed application; voice flags and offline proof are evidence of its local synthesis, not a revocation of vendor/system network permissions.

Speech text is supplied only to the selected installed engine; it never enters diagnostics or app logs. Temporary private PCM WAVs are removed after synthesis and the latest replay audio is retained in memory until replacement/release/close. Abrupt termination may leave a temporary cache file, cleaned on subsequent synthesis when older than an hour. No audio/transcript database or conversation export is added. Debug result exports contain fixed-check outcomes and safe engine/voice/timing/output-type metadata, with no raw speech, paths, Bluetooth addresses or device identifiers. See [voice implementation](voice/PASS_6_OFFLINE_VOICE_LAYER.md) and [vendor provenance](voice/LICENSING_PROVENANCE.md).

## Pass 7 local inference and debug acquisition

Production recognition and translation execute in a private application worker with no network clients, cloud SDKs or fallback. Release retains no Internet permission. The debug APK has Internet permission solely for its temporary one-action acquisition of one hash-pinned test resource pack; that helper accepts no speech/text and is not called by inference. The network separation is enforced by source/dependency guards, not an OS network sandbox, because the debug application's worker shares its UID.

Resources are installed in private storage, stream-verified before model loading and committed atomically after every extracted hash matches. Interrupted/corrupt setup does not mark a partial state ready. The source archive and staging files are removed. No model assets are in the APK or Git. The component screen uses fixed synthetic controls, no microphone or conversation persistence. Normal local execution is independent of radio state.

Worker messages carry content privately within the same installed application; exported diagnostics carry only finite stages/failure codes, bounded timings/memory/exit reason values and static test outcomes. Native logging and panic output are suppressed. No raw transcript, translation, prompt, path, exception message, native/model/request ID or device identifier is exported. Background, close, failure and cancellation reclaim the model owner by worker termination. See [ownership and boundaries](LOCAL-AI-ADAPTERS.md).

## Long-term requirements

After required packs are installed, offline translation must work without connectivity and must not transmit user speech/text off-device. Conversation content is to be ephemeral by default. Future persistence must have a deliberate product decision, explicit user choice where appropriate, and an abstraction separating storage from domain/application code.

Optional future cloud functionality must remain separate from the offline engine. Silent cloud fallback is prohibited. An account must not be required to use the initial offline product. Model downloads, optional cloud features, or any new permissions need their own documented scope and privacy review when implemented. None exists in Pass 1.

## Pass 8 real PTT product

User-authorized foreground PTT captures private mono PCM16 audio for at most 30 seconds. RECORD_AUDIO is requested only when needed; grant alone never starts recording. Capture stops on release/cancel/background. Recordings are deleted after recognition and in failure/cancellation cleanup; orphan recordings are removed from the dedicated cache on a new session. Latest synthesized replay audio is volatile and revoked on replacement/clear/background/close.

Source, translation and up to eight recent completed turns exist only in ViewModel/coordinator memory. Rotation preserves display; session close/clear erases it. This history is not supplied to MADLAD as context. No transcript database, analytics or content export exists. Internal debug exports contain typed stages/failures and numeric capability metadata only. Release retains no INTERNET; debug acquisition remains the fixed model-only helper and never receives user content. Radio state is not a gate. See [product behavior](PASS_8_PRODUCT.md).
