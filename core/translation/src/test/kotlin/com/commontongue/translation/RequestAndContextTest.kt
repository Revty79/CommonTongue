package com.commontongue.translation

import com.commontongue.domain.DeviceQualityProfile
import com.commontongue.domain.LanguageId
import org.junit.Assert.*
import org.junit.Test

class RequestAndContextTest {
    private fun turn() = ConversationTurn(ConversationSide.FIRST, "source", "target", direction)

    @Test
    fun basicRequestIsLightweightAndOfflineByDefault() {
        val request = TranslationRequest("Useful conversation", direction)
        assertSame(ConversationContext.None, request.context)
        assertTrue(request.terminology.isEmpty())
        assertEquals(DomainContext.GENERAL, request.domain)
        assertNull(request.regionalPreference)
        assertEquals(ExecutionRequirement.OFFLINE_REQUIRED, request.execution)
        assertEquals(DeviceQualityProfile.STANDARD, request.qualityProfile)
        assertNull(request.validationFailure())
    }

    @Test
    fun blankUserInputIsAnExpectedValidationFailure() {
        assertEquals(
            FailureCategory.INPUT_INVALID,
            TranslationRequest("  ", direction).validationFailure()!!.category,
        )
    }

    @Test
    fun contextCopiesItsInputAndRejectsMutationThroughAListCast() {
        val input = mutableListOf(turn())
        val context = ConversationContext.recent(input)
        input.clear()
        assertEquals(1, context.turns.size)
        assertThrows(UnsupportedOperationException::class.java) {
            (context.turns as MutableList).clear()
        }
    }

    @Test
    fun emptyContextHasNoPersistenceOrImplicitHistory() {
        assertTrue(ConversationContext.None.turns.isEmpty())
        assertTrue(ConversationContext.recent(emptyList()).turns.isEmpty())
    }

    @Test
    fun contextRejectsTurnOverflowInsteadOfDiscardingIt() {
        assertThrows(IllegalArgumentException::class.java) {
            ConversationContext.recent(listOf(turn(), turn()), ContextLimits(maxTurns = 1))
        }
    }

    @Test
    fun contextCountsSourceAndTranslationCharacters() {
        assertEquals(
            1,
            ConversationContext.recent(listOf(turn()), ContextLimits(maxCharacters = 12))
                .turns
                .size,
        )
        assertThrows(IllegalArgumentException::class.java) {
            ConversationContext.recent(listOf(turn()), ContextLimits(maxCharacters = 11))
        }
    }

    @Test
    fun invalidContextLimitsAndEmptyTurnsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ContextLimits(maxTurns = 0) }
        assertThrows(IllegalArgumentException::class.java) { ContextLimits(maxCharacters = -1) }
        assertThrows(IllegalArgumentException::class.java) {
            ConversationTurn(ConversationSide.SECOND, "", "target", direction)
        }
    }

    @Test
    fun terminologyCanDescribeATermOrMeaningAndAnExtensibleDomain() {
        val hint =
            TerminologyHint(
                "jack",
                targetMeaning = "vehicle lifting tool",
                domain = DomainContext.of("automotive"),
                strength = TerminologyStrength.REQUIRED,
            )
        assertEquals("vehicle lifting tool", hint.targetMeaning)
        assertEquals("automotive", hint.domain!!.id)
        assertEquals("gato", TerminologyHint("jack", targetTerm = "gato").targetTerm)
    }

    @Test
    fun emptyTerminologyGuidanceIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { TerminologyHint("jack") }
        assertThrows(IllegalArgumentException::class.java) {
            TerminologyHint("", targetTerm = "gato")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TerminologyHint("jack", targetMeaning = " ")
        }
    }

    @Test
    fun requestSnapshotsHintsAndCriticalExpectations() {
        val hints = mutableListOf(TerminologyHint("jack", targetTerm = "gato"))
        val content =
            mutableListOf<CriticalExpectation>(
                CriticalExpectation.NumberLiteral(NumericValue.parse("12"))
            )
        val request =
            TranslationRequest(
                "12 jacks",
                direction,
                terminology = hints,
                criticalContent = content,
            )
        hints.clear()
        content.clear()
        assertEquals(1, request.terminology.size)
        assertEquals(1, request.criticalContent.size)
        assertThrows(UnsupportedOperationException::class.java) {
            (request.terminology as MutableList).clear()
        }
    }

    @Test
    fun regionalPreferencesDistinguishCountryAndMacroregion() {
        val mexican = RegionalPreference(LanguageId.parse("es-MX"))
        val latin = RegionalPreference(LanguageId.parse("es-419"))
        assertNotEquals(mexican, latin)
        assertNull(
            TranslationRequest("source", direction, regionalPreference = latin).validationFailure()
        )
    }

    @Test
    fun aDifferentTargetLanguagePreferenceIsInvalid() {
        val request =
            TranslationRequest(
                "source",
                direction,
                regionalPreference = RegionalPreference(LanguageId.parse("fr-FR")),
            )
        assertEquals(FailureCategory.INPUT_INVALID, request.validationFailure()!!.category)
    }

    @Test
    fun contextIdentifiersAreExtensibleAndValidated() {
        assertEquals("general", DomainContext.GENERAL.id)
        assertNotEquals(DomainContext.of("construction"), DomainContext.of("agriculture"))
        for (id in listOf("", "UPPER", "../path", "a b")) assertThrows(
            IllegalArgumentException::class.java
        ) {
            DomainContext.of(id)
        }
    }

    @Test
    fun defaultPolicyExpressesMeaningAndSensitiveContentPreservation() {
        assertEquals(TranslationRequirement.entries.toSet(), TranslationPolicy().requirements)
        assertTrue(TranslationRequirement.PRESERVE_PROFANITY in TranslationPolicy().requirements)
        assertTrue(
            TranslationRequirement.DO_NOT_INVENT_INFORMATION in TranslationPolicy().requirements
        )
        assertTrue(TranslationRequirement.PRESERVE_NEGATION in TranslationPolicy().requirements)
    }

    @Test
    fun policyCanBeRestrictedWithoutMutableInputLeakage() {
        val options = mutableSetOf(TranslationRequirement.PRESERVE_NUMBERS)
        val policy = TranslationPolicy(options, UnsupportedHandling.REJECT)
        options.clear()
        assertEquals(setOf(TranslationRequirement.PRESERVE_NUMBERS), policy.requirements)
        assertThrows(UnsupportedOperationException::class.java) {
            (policy.requirements as MutableSet).clear()
        }
    }
}
