package com.commontongue.speech.android

import com.commontongue.domain.LanguageId
import org.junit.Assert.*
import org.junit.Test

class VoicePolicyTest {
    private fun voice(
        engine: String,
        locale: String,
        network: Boolean = false,
        installed: Boolean = true,
    ) = InstalledVoice(engine, locale, LanguageId.parse(locale), network, installed)

    @Test
    fun prefersLatinAmericanSpanishAcrossProviders() {
        val spain = voice("preferred.provider", "es-ES")
        val mexico = voice("other.provider", "es-MX")
        assertEquals(
            mexico,
            VoicePolicy.select(listOf(spain, mexico), LanguageId.parse("es"), null, spain.engine),
        )
        assertEquals(
            spain,
            VoicePolicy.select(listOf(spain, mexico), LanguageId.parse("es-ES"), null, null),
        )
    }

    @Test
    fun spanishUsIsSuitableRegionalFallback() {
        val us = voice("provider", "es-US")
        assertEquals(
            us,
            VoicePolicy.select(
                listOf(voice("provider", "es-ES"), us),
                LanguageId.parse("es-419"),
                null,
                null,
            ),
        )
    }

    @Test
    fun rejectsNetworkAndNotInstalledEvenForExactPreference() {
        val network = voice("provider", "es-MX", network = true)
        val missing = voice("provider", "es-US", installed = false)
        assertNull(VoicePolicy.select(listOf(network, missing), LanguageId.parse("es"), null, null))
        assertNull(VoicePolicy.select(listOf(network), LanguageId.parse("es-MX"), network.id, null))
    }

    @Test
    fun preferenceCannotChangeLockedLanguage() {
        val english = voice("provider", "en-US")
        assertNull(VoicePolicy.select(listOf(english), LanguageId.parse("es"), english.id, null))
    }

    @Test
    fun englishPrefersUsAndReportsActualRegion() {
        val us = voice("provider", "en-US")
        assertEquals(
            us,
            VoicePolicy.select(
                listOf(voice("provider", "en-GB"), us),
                LanguageId.parse("en"),
                null,
                null,
            ),
        )
    }

    @Test
    fun voiceHandlesIncludeProviderAndNeverExposeName() {
        val a = voice("provider.a", "en-US")
        val b = voice("provider.b", "en-US")
        assertNotEquals(a.id, b.id)
        assertFalse(a.id.token.contains(a.name))
        assertEquals(a.id, a.copy().id)
    }

    @Test
    fun sameRegionPrefersUserEngineAndStableQualityOrder() {
        val a = voice("a", "en-US").copy(quality = 100)
        val b = voice("b", "en-US").copy(quality = 500)
        assertEquals(a, VoicePolicy.select(listOf(b, a), LanguageId.parse("en"), null, "a"))
        assertEquals(b, VoicePolicy.select(listOf(a, b), LanguageId.parse("en"), null, null))
    }

    @Test
    fun malformedDiagnosticIdentifiersAreHashed() {
        val identifier = safeIdentifier("private words /sdcard/private\nsecret")
        assertTrue(identifier.startsWith("id_"))
        assertFalse(identifier.contains("secret"))
    }
}
