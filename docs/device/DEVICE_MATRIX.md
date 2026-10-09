# Physical device matrix

The user-reviewed v7 S25 result confirms successful model loading, local microphone translation/playback in both directions, all four direct/file TTS checks and the prerecorded full pipeline. The observed engine is com.google.android.tts with offline en-US/es-US voices. The user accepts this S25 proof as Pass 5 closeout; wider devices and sustained/human trials are follow-up. Hardware values remain from earlier allowlisted exports; no new inventory is invented. See [closeout](PASS_5_CLOSEOUT.md). ADB is not required.

| Anonymous planned label | Device | Android / execution ABI | RAM / SoC | MADLAD | OPUS | Timing / memory / thermal / battery | Result |
| --- | --- | --- | --- | --- | --- | --- | --- |
| device-high-01 | Galaxy S25 / samsung SM-S931U | Android 16 / API 36; actual arm64-v8a | 11,653,349,376 bytes reported RAM; QTI SM8750; 8 cores; 4 KiB pages (earlier inventory) | Accepted v7 microphone EN→ES/ES→EN and prerecorded pipeline through TTS_COMPLETE | Not attempted | First post-load human EN→ES: ASR ~1,758 ms, translation ~1,920 ms, release→playback evidence ~4,665 ms; sustained characterization pending | PASS_5_ACCEPTED_S25_PROOF; broad performance classification deferred |
| device-old-01 | Older phone/tablet pending availability | Not captured | Not captured | Not attempted | Not attempted | Not measured | NOT_TESTED |

The read-only inventory exports manufacturer/model, Android/API, supported and preferred ABI, actual execution ABI **only after worker evidence**, total/available RAM, CPU count, exposed SoC/board identifiers, free data storage, battery level/temperature, thermal status, screen size, device type pending confirmation, page size and reported acceleration features. Reported Vulkan features are not proof of an available NPU or an accelerated inference path. Missing properties remain unknown.

Private routing serials stay in memory. Android IDs, account names, phone numbers, SSIDs, location and raw system dumps are excluded. Labels are `device-high-01`, `device-old-01`, `device-tablet-01` or `device-test-01`.

Broader performance observations use RUNS_COMFORTABLY, RUNS_WITH_CONCERNS, TOO_SLOW, MEMORY_LIMITED, INCOMPATIBLE or NOT_TESTED with concrete reasons. PASS_5_ACCEPTED_S25_PROOF records the user's scoped acceptance; it does not invent a comfortable-performance rating. These are research observations, never final Lite/Standard/Enhanced mappings. A 32-bit-only or API-below-26 device is incompatible with this ARM64 research APK; do not report that as an observed model allocation failure.
