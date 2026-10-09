# Installed TTS: first-party licensing and provenance

Reviewed 2026-10-08 for the Pass 6 installed-offline-voice strategy. This is an engineering source review, **not legal clearance**. It deliberately separates API access from voice/model redistribution and commercial rights in generated output. No vendor model, voice binary, engine APK or paid service is downloaded, extracted, copied or bundled.

## Android framework API

The [SDK license, sections 3–4 and 7](https://developer.android.com/studio/terms) grants royalty-free SDK use to develop apps for compatible Android implementations, retains SDK ownership, restricts SDK redistribution, and leaves third-party content subject to its own rights/terms. The [Android legal notice](https://developer.android.com/legal) describes Android app development/distribution without registration or fees. These support the framework development/invocation strategy, not ownership of vendor voices or a blanket license for their audio.

The [TextToSpeech reference](https://developer.android.com/reference/android/speech/tts/TextToSpeech) documents installed-engine APIs, file synthesis and shutdown; [Voice](https://developer.android.com/reference/android/speech/tts/Voice) exposes network-required status. The API itself supplies no voice-asset redistribution grant or universal vendor-output attribution rule. Downloading a voice is not required for every invocation, but installed data is necessary for the selected embedded voice. The [audio-focus guidance](https://developer.android.com/media/optimize/audio-focus) restricts requests on target API 35+ to the top app or a foreground service; Common Tongue uses visible-session ownership.

| Question | Android framework finding |
| --- | --- |
| Commercial third-party invocation | Normal Android app/API integration is supported; vendor-specific permission remains separate. |
| Generated-output restrictions | No universal output-use grant established by the framework reference. Content/vendor rights still apply. |
| Voice/model redistribution | Not granted by invoking TextToSpeech. Do not redistribute proprietary vendor assets. |
| Attribution | Framework documentation/sample reuse has separate notice/license rules; no universal vendor speech-output attribution rule found. This implementation is original application code adapted from our own Pass 5 experiment. |
| Network/cloud | API can expose network voices. Common Tongue rejects them and requests embedded synthesis. |

## Google Speech Recognition & Synthesis

The [Google-published Play listing](https://play.google.com/store/apps/details?id=com.google.android.tts) explicitly describes speech output for translation/accessibility and other Play apps. This is positive evidence of intended third-party invocation, but it does not explicitly grant commercial speech-output or asset-redistribution rights.

The [Google terms](https://policies.google.com/terms?hl=en-US) distinguish business users, restrict reverse engineering/extraction and contain restrictions involving AI-generated content and training. Their precise application to installed offline TTS output is **unresolved**. The [service-specific index](https://policies.google.com/terms/service-specific?hl=en) did not provide a distinct installed-TTS output license in this review. The [Play terms, license-to-use-content section](https://play.google.com/intl/en_us/about/play-terms/) state personal/non-commercial content licensing and allow provider EULAs; these require legal interpretation for a commercial app invoking the user's installed engine. They must not be silently treated as a prohibition on every third-party app or as unrestricted commercial permission.

| Question | Google engine finding |
| --- | --- |
| Commercial third-party invocation | Third-party TTS use is documented; explicit commercial grant/applicable EULA requires pre-release review. |
| Generated-output restrictions | Replay inside Common Tongue is the narrow intended use. Monetized/distributed recordings, training and broader output use are not cleared. |
| Voice/model redistribution | No affirmative grant found. Prohibited in our strategy; user installs assets through vendor settings. |
| Attribution | No installed-TTS-specific output attribution requirement found; absence is not legal confirmation. |
| Network/cloud | The engine can expose local or network voices. Offline capability is checked per voice; vendor setup/updates may use networking. No Cloud TTS API, account, subscription or paid endpoint is integrated. |

## Samsung TTS

The [Samsung-published Galaxy Store listing](https://galaxystore.samsung.com/detail/com.samsung.SMT) describes use by applications including translation apps. The [Samsung support instructions](https://www.samsung.com/in/support/mobile-devices/how-to-use-the-text-to-speech-feature-on-your-galaxy-phone-or-tablet/) document engine selection and language voice-data installation; this establishes the setup path, not licensing.

The [Samsung mobile EULA](https://www.samsung.com/us/support/legal/LGL10000282/), restrictions section, limits copying/reverse engineering, sublicensing and commercial hosting of Samsung software. Its internet section says some features need connectivity. It is a general, region-dependent device/software agreement rather than an explicit Samsung-TTS commercial-output license. Applicability to the particular S25/region and any additional engine/voice-provider terms must be checked before release.

| Question | Samsung engine finding |
| --- | --- |
| Commercial third-party invocation | App/translation integration is documented; explicit commercial scope requires review of the applicable device/engine EULA. |
| Generated-output restrictions | No clear TTS-specific generated-audio commercial grant found. Local playback/replay is the intended implementation scope. |
| Voice/model redistribution | No grant established; do not extract/copy/distribute installed Samsung or supplier assets. |
| Attribution | No TTS-specific generated-output attribution rule established; review additional voice-supplier terms if applicable. |
| Network/cloud | Language installation may require networking; general terms allow network-dependent features. Select only installed voices declaring no network requirement, and verify on the actual phone. |

## Pre-release legal questions and provenance boundary

Legal review should identify the applicable Google/Samsung engine and voice-provider licenses on target devices/regions, confirm commercial third-party invocation and local audio playback/replay rights, assess whether output recording/export/monetization or AI-training limitations matter, and determine any notices/attribution. No output recording/export feature is added here. The Android API's right to invoke installed engines cannot be used as a redistribution license.

`com.google.android.tts` is the **observed Pass 5 configuration**, not an approved exclusive provider or a production legal clearance. Pass 6 accepts any installed provider satisfying the offline voice checks. Provider/voice identifiers in measurements identify selection for troubleshooting; they are not copied voice assets or brand endorsements. Voice setup remains the vendor's own mechanism. The lack of INTERNET permission in Common Tongue does not revoke another installed app's permission; we rely on verified offline voice selection and device offline proof, rather than claim an OS-wide network sandbox.
