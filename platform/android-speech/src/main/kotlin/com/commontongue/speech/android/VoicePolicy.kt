package com.commontongue.speech.android

import com.commontongue.domain.LanguageId
import com.commontongue.translation.VoiceId
import java.security.MessageDigest

data class InstalledVoice(
    val engine: String,
    val name: String,
    val locale: LanguageId,
    val requiresNetwork: Boolean,
    val installed: Boolean,
    val quality: Int = 0,
    val latency: Int = 0,
) {
    val id: VoiceId
        get() = VoiceId.of("v_" + digest("$engine\u0000$name"))
}

internal fun digest(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString(
        ""
    ) {
        "%02x".format(it)
    }

/** Region takes precedence over the preferred engine; there is no vendor allowlist. */
internal object VoicePolicy {
    private val latinAmerica =
        listOf(
            "419",
            "MX",
            "US",
            "AR",
            "CO",
            "CL",
            "PE",
            "VE",
            "EC",
            "GT",
            "CU",
            "BO",
            "DO",
            "HN",
            "PY",
            "SV",
            "NI",
            "CR",
            "PA",
            "UY",
            "PR",
        )

    fun select(
        voices: List<InstalledVoice>,
        language: LanguageId,
        preference: VoiceId?,
        preferredEngine: String?,
    ): InstalledVoice? =
        voices
            .filter {
                it.installed &&
                    !it.requiresNetwork &&
                    it.locale.baseLanguage == language.baseLanguage &&
                    (preference == null || preference == it.id)
            }
            .minWithOrNull(
                compareBy<InstalledVoice> { regionRank(it.locale, language) }
                    .thenBy { if (it.engine == preferredEngine) 0 else 1 }
                    .thenByDescending { it.quality }
                    .thenBy { it.latency }
                    .thenBy { it.engine }
                    .thenBy { it.name }
            )

    private fun regionRank(voice: LanguageId, requested: LanguageId): Int {
        if (requested.region != null && voice.tag == requested.tag) return 0
        if (requested.region != null && requested.region == voice.region) return 1
        if (requested.baseLanguage == "es") {
            val index = latinAmerica.indexOf(voice.region)
            return if (index >= 0) 2 + index else if (voice.region == null) 30 else 31
        }
        return if (voice.region == "US") 2 else if (voice.region == null) 3 else 4
    }
}
