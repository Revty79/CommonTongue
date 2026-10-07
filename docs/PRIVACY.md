# Privacy

## Current foundation build

Pass 1 displays a static foundation screen. It has:

- No accounts or sign-in.
- No analytics, advertisements, telemetry, or crash-reporting service.
- No translation, speech recognition, microphone recording, or camera feature.
- No conversation input, transcripts, saved conversations, or database.
- No Internet, microphone, or camera permission.
- No network client or cloud service.

The application collects no user speech/text and sends no user content off-device. These statements describe the current foundation, not a completed translation product. AndroidX may declare an app-scoped signature permission used to protect internal broadcast receivers; it is not a network or sensor permission. The exact merged-manifest permission list is recorded in [Pass 1 validation](PASS_1_VALIDATION.md).

Android backup is disabled for the application. Normal Android package/runtime metadata, build-generated files, and test artifacts are not conversation storage. Developer tools fetch build dependencies, and an emulator's operating system may have its own services; neither is application translation traffic.

## Long-term requirements

After required packs are installed, offline translation must work without connectivity and must not transmit user speech/text off-device. Conversation content is to be ephemeral by default. Future persistence must have a deliberate product decision, explicit user choice where appropriate, and an abstraction separating storage from domain/application code.

Optional future cloud functionality must remain separate from the offline engine. Silent cloud fallback is prohibited. An account must not be required to use the initial offline product. Model downloads, optional cloud features, or any new permissions need their own documented scope and privacy review when implemented. None exists in Pass 1.
