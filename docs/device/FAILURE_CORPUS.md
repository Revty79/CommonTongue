# Physical-device failure corpus

Current closeout status: **S25 v7 ACCEPTED**. The user-reviewed export reports all four TTS checks, both microphone directions and the prerecorded full pipeline passing, with no current TTS failure. The narratives below retain v3–v6 engineering history. Cumulative CRASH_NATIVE/SIGSEGV/worker-exit entries from those sessions are not new v7 failures. See [accepted baseline](PASS_5_CLOSEOUT.md); no cleanup APK is created.

The S25 tester first reported “Error: Load models and wait for the current turn” after successful APK/pack/offline-voice setup. That **UX** failure was corrected in version 3. The subsequent on-device diagnostic confirms installed-pack persistence, readable expected-size files and verified hashes, then records **RESOURCE / NATIVE_RUNTIME / NATIVE_LINK_FAILED / UnsatisfiedLinkError** before inference. Static inspection of the installed version 3 APK identifies the cause: `libdevice_trial.so` has a build-host path for its Rust dependency, whose ELF lacks a SONAME. Version 4 rebuilds Rust with SONAME `libtrial_t5.so`, verifies the JNI dependency uses that basename, and audits dependency closure, required exports and strong imports against API 26 NDK stubs. The raw host path is not committed. The subsequent version 4 export confirms native/model loading; no human translation/quality failure is inferred from the linker report. See [native correction evidence](../../tools/device-trial/evidence/s25-native-link-correction.json).

Version 4 confirms that native linking and model loading succeed, then records three **RESOURCE / unexpected_process_exit_or_disconnect** observations after microphone release. The last available stage is READY; no ASR/translation boundary or Android reason exists in that export. The cause is **UNCONFIRMED**, with no OOM/native-crash attribution or human translation-quality result. Version 5 [crash isolation](CRASH_ISOLATION.md) adds the requested evidence while preserving the model pack and native libraries.

Version 5 passes standalone Whisper in 1,475.845 ms and reaches ASR_COMPLETE in the full pipeline, then fails at TRANSLATION_START. The matched full-pipeline worker reports **CRASH_NATIVE / SIGSEGV / status 11**. MADLAD-only also exits at that boundary, with a matched CRASH/status 0 and live-at-disconnect observation retained separately. Version 6 corrects the concrete raw-pointer JSON transport defect described in [crash isolation](CRASH_ISOLATION.md), with local AOSP conversion proof and opaque ownership/stale-handle regression checks. Exact S25 crash attribution and corrected physical translation remain pending. No failed translation output or human quality result is invented; the pack and Whisper path are unchanged.

The subsequent v6 S25 export reaches all translation substages and TRANSLATION_COMPLETE on prerecorded and real microphone EN→ES turns. It then fails at **TTS**, with blank diagnostic_code/error_type after raw-message redaction. Engine initialization, offline voice configuration, synthesis format and playback remain **UNCONFIRMED** causes. Version 7 [TTS diagnostics](TTS_DIAGNOSTICS.md) records these independently and tests direct Android speech versus app WAV playback in both languages. This result supports the translation correction; it is not a linguistic quality or full audible-pipeline pass. Earlier MADLAD-only WORKER_EXIT entries in the cumulative export are historical.

For each meaningful failure record an anonymous device label, case ID, direction, anonymized source text and relevant conversation context, observed ASR text, produced translation, intended meaning, stage and short engineering notes. Stages are ASR, TRANSLATION, TTS, PERFORMANCE, RESOURCE or UX. Correct recognition plus wrong translation belongs to TRANSLATION; misheard microphone input belongs to ASR even if translation faithfully renders the mistaken transcript.

Optional human corrections require explicit permission to retain the corrected text. No participant names, identifying addresses/phone numbers, accounts, private messages, location, ADB identifiers, notification screenshots or human recordings may enter committed evidence. Keep unsanitized notes in ignored local storage, review/redact text before export, then validate with `schemas.failure`. No model replacement, fine-tuning or case-specific output fix is part of this pass.

```json
{
  "device_label": "device-high-01",
  "case_id": "actual-trial-case-id",
  "direction": "es-en",
  "source_text": "<anonymized actual source>",
  "context": [],
  "observed_asr": "<actual recognized text>",
  "translation": "<actual output>",
  "intended_meaning": "<participant-confirmed meaning>",
  "failure_stage": "TRANSLATION",
  "notes": "<nonidentifying engineering observation>"
}
```

This is an unfilled schema example, not an observed failure.
