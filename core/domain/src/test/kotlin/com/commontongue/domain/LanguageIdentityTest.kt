package com.commontongue.domain

import org.junit.Assert.*
import org.junit.Test

class LanguageIdentityTest {
    @Test
    fun canonicalCaseAndWhitespaceHaveValueEquality() {
        val a = LanguageId.parse(" ES-mx ")
        val b = LanguageId.parse("es-MX")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals("es-MX", a.tag)
    }

    @Test
    fun languageAndRegionalIdentitiesAreDistinct() {
        val spanish = LanguageId.parse("es")
        val mexico = LanguageId.parse("es-MX")
        val regional = LanguageId.parse("es-419")
        assertNotEquals(spanish, mexico)
        assertNotEquals(mexico, regional)
        assertEquals("es", regional.baseLanguage)
        assertEquals("419", regional.region)
        assertNull(spanish.region)
    }

    @Test
    fun scriptAndPrivateExtensionsSurviveCanonicalization() {
        val id = LanguageId.parse("SR-latn-rs-x-local")
        assertEquals("sr-Latn-RS-x-local", id.tag)
        assertEquals("Latn", id.script)
        assertEquals("RS", id.region)
    }

    @Test
    fun deprecatedAliasesNormalize() {
        assertEquals(LanguageId.parse("he"), LanguageId.parse("iw"))
    }

    @Test
    fun otherLanguagesAndUnregisteredSyntaxAreNotHardCoded() {
        assertEquals("ja", LanguageId.parse("ja-JP").baseLanguage)
        assertEquals("qaa", LanguageId.parse("qaa").tag)
    }

    @Test
    fun invalidOrUnknownIdentitiesAreRejected() {
        for (invalid in
            listOf(
                "",
                " ",
                "en_US",
                "en--US",
                "en-!",
                "und",
                "x-only",
                "english",
                "spanish",
            )) assertThrows(invalid, IllegalArgumentException::class.java) {
            LanguageId.parse(invalid)
        }
    }

    @Test
    fun directionIsOrderedAndCanBeReversed() {
        val pair = TranslationDirection(LanguageId.parse("ja"), LanguageId.parse("fr"))
        assertNotEquals(pair, pair.reversed())
        assertEquals(pair, pair.reversed().reversed())
    }

    @Test
    fun sameBaseLanguageIsRejectedEvenAcrossRegions() {
        assertThrows(IllegalArgumentException::class.java) {
            TranslationDirection(LanguageId.parse("es"), LanguageId.parse("es-MX"))
        }
    }

    @Test
    fun qualityProfilesHaveNoImplementationMapping() {
        assertEquals(
            listOf("LITE", "STANDARD", "ENHANCED"),
            DeviceQualityProfile.entries.map { it.name },
        )
    }
}
