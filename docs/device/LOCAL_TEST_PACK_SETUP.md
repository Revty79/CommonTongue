# Phone-only Pass 5 research setup

Status: **PASS 5 ACCEPTED ON PHYSICAL S25 v7 PROOF**. See [closeout](PASS_5_CLOSEOUT.md) for the exact preserved APK/configuration and follow-up limits. ADB is optional developer tooling and is no longer an acceptance requirement. No more USB/wireless pairing work is required from the tester.

Download **CommonTongue-Pass5-S25-TTS-Diagnostic-Update.apk** from the [S25 TTS diagnostic research update](https://github.com/Revty79/CommonTongue/releases/tag/pass5-s25-tts-diagnostic-2026-10-08). New testers obtain the unchanged single test pack from the [original research preview](https://github.com/Revty79/CommonTongue/releases/tag/pass5-research-preview-2026-10-08). These remain research builds; the user accepts the v7 S25 proof, with broader validation documented as follow-up. Existing S25 testers need only the approximately 45 MB APK update; their installed pack and voices are reused.

The APK and single pack are published and anonymously downloadable. SHA-256 hashes and exact runtime requirements are supplied with the download assets; the app checks the pack automatically. Testers do not need to compare checksums. The [publication evidence](../../tools/device-trial/evidence/preview-artifacts.json) records the verified upload sizes and hashes.

## On the authorized phone

1. Download **CommonTongue-Pass5-S25-TTS-Diagnostic-Update.apk** from the S25 update's download assets and open it. Follow Android's installation prompts. If Common Tongue is already installed, choose **Update**; keep the app and its data, and skip steps 2–4 when its pack is already installed.
2. Download **CommonTongue-Pass5-Test-Pack.zip** to the phone. Wait for the download to finish. Leave the ZIP as one file; do not extract it in the file manager.
3. Open **Common Tongue Device Trial** and tap **Install Test Pack**.
4. In Android's file picker, choose the downloaded **CommonTongue-Pass5-Test-Pack.zip**. Keep the app open until checking finishes.
5. If the app reports a missing offline voice, tap **Set Up Offline Voices**, install English and Spanish in the phone's TTS setup, then return to Common Tongue. The app accepts only installed voices reporting no network requirement. The separate pinned eSpeak research app remains an optional alternative; system voices are not redistributed or selected for production.
6. At **Pack Installed**, tap **Start Testing** and allow the microphone if prompted. Keep the app open through **Loading Models**; speech buttons unlock only at **Ready to Speak**. Hold a language button and release to hear the translation. Wi-Fi/cellular may be on or off. For the explicit offline-proof test only, turn mobile data OFF, Airplane Mode ON and Wi-Fi OFF; the app displays that test condition without blocking ordinary use.

Allow at least **5 GB free phone storage before downloading/installing**. The source ZIP is 1,997,338,704 bytes; extraction needs another roughly 2 GB plus a 512 MiB margin. A replacement keeps the previous installation until commit and therefore needs additional free space. The app checks the actual remaining space before extraction. ARM64 Android API 26+ is required; v7 local inference/playback is accepted on the S25, while broader device/sustained performance remains unmeasured.

After installation, the downloaded ZIP can be removed. The installed private models, fixtures and receipt are sufficient; no computer, development connection, source package or network is required to run the translator.

Existing S25 testers already running accepted v7 need no new APK, pack import or voice change. Direct/file English and Spanish speech, both microphone directions and the prerecorded full pipeline pass. Keep that installation. Future testers can follow [TTS checks](TTS_DIAGNOSTICS.md); [closeout](PASS_5_CLOSEOUT.md) distinguishes this current success from historical export failures.

## Integrity and interruption behavior

The APK embeds a small immutable contract, not weights. It pins the exact whole ZIP size/SHA-256, canonical manifest hash, version/package and each allowed payload file's size/hash/revision. The producer uses fixed ZIP metadata and unchanged Pass 4/2 assets. The single pack fits GitHub's less-than-2-GiB per-asset limit, so splitting is unnecessary. See the [APK contract](../../spikes/physical-trial/src/main/assets/test-pack-contract.json).

The file picker requests a local document and only a temporary read grant. No Internet, storage, media, install-package or other permission is added. Streaming extraction does not make another private copy of the source ZIP. Unknown/duplicate/path-traversal entries, wrong versions, oversized payloads, missing files, CRC errors and whole-archive corruption are rejected. Each copied file is synced and SHA-256 checked from the destination before a complete receipt/marker is written.

Files first enter a private staging directory. Publication is a directory rename with the previous directory retained for rollback. Interrupted/backgrounded imports never publish incomplete models; restart removes stale staging and restores the previous directory if commit was interrupted. A complete pack is checked again before model loading. **Pack Installed** means provisioned files are present; **Loading Models** checks file hashes, the ARM64 native engine, translation and ASR in order; **Ready to Speak** requires successful model loading and both installed offline speech languages. An app restart keeps the pack but requires a fresh model load.

The S25 correction is an APK update with the same application ID and signing certificate. It preserves the existing pack and voices, with the exact unchanged pack contract/hash. Existing testers should install only the corrected APK, without uninstalling Common Tongue or importing/downloading the pack again. If loading fails, use **Export Research Results**: it reports private-file presence, runtime verification and the last loading stage, without human text/recordings or absolute paths.

## Results without ADB

After a test session, **Export Research Results** uses Android's Save dialog to write anonymized hardware/ABI, metrics and known synthetic-fixture results. It excludes human recordings and explicitly saved human test text, and redacts raw error diagnostics. The coordinator must review the exported evidence before Git. Physical presence, audible operation, human ratings and live conversation still require the tester; an export is not automatic acceptance.

ADB deployment and collection remain available for developers. Production app/core and their permissions/dependencies remain unchanged. Ordinary CI stays model/device-free. The model pack and all APKs remain ignored local artifacts or repository-associated download assets, never Git history. This is a research preview, not a public production release or Pass 6.
