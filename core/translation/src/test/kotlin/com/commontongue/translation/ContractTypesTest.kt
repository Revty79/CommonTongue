package com.commontongue.translation

import com.commontongue.domain.LanguageId
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ContractTypesTest {
    @Test
    fun severityAndCertaintyAreIndependent() {
        val warning =
            CapabilityIssue(
                IssueCode.NEGATION_MISMATCH,
                IssueSeverity.CRITICAL,
                IssueCertainty.POSSIBLE,
            )
        assertEquals(IssueCertainty.POSSIBLE, warning.certainty)
        assertEquals(IssueSeverity.CRITICAL, warning.severity)
    }

    @Test
    fun unavailableConfidenceIsTheDefault() {
        assertSame(ConfidenceEvidence.Unavailable, TranslatedText("output", direction).confidence)
        assertSame(
            ConfidenceEvidence.Unavailable,
            RecognizedSpeech("input", direction.source, CompletionStatus.COMPLETE).confidence,
        )
    }

    @Test
    fun legitimateScoresRequireFiniteRangeAndEvidence() {
        val reported =
            ConfidenceEvidence.Reported(
                0.7,
                ConfidenceCalibration.UNCALIBRATED,
                "adapter-native normalized measure",
            )
        assertEquals(ConfidenceCalibration.UNCALIBRATED, reported.calibration)
        for (score in listOf(-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY)) assertThrows(
            IllegalArgumentException::class.java
        ) {
            ConfidenceEvidence.Reported(score, ConfidenceCalibration.CALIBRATED, "basis")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ConfidenceEvidence.Reported(0.5, ConfidenceCalibration.CALIBRATED, " ")
        }
    }

    @Test
    fun translationCarriesUsedLanguagesPartialStatusAndAmbiguity() {
        val alternatives = mutableListOf("vehicle tool", "animal")
        val ambiguity = Ambiguity("gato", alternatives)
        alternatives.clear()
        val result =
            TranslatedText("candidate", direction, CompletionStatus.PARTIAL, listOf(ambiguity))
        assertEquals(direction, result.directionUsed)
        assertEquals(2, result.ambiguities.single().interpretations.size)
        assertEquals(CompletionStatus.PARTIAL, result.completion)
        assertSame(VerificationState.Unverified, result.verification)
    }

    @Test
    fun ambiguityRequiresRealAlternatives() {
        assertThrows(IllegalArgumentException::class.java) { Ambiguity("gato", listOf("one")) }
    }

    @Test
    fun resultWarningsAreSnapshots() {
        val warnings = mutableListOf(caution())
        val result =
            CapabilityResult.Success(
                TranslatedText("output", direction),
                ExecutionMode.OFFLINE,
                warnings,
            )
        warnings.clear()
        assertEquals(1, result.issues.size)
        assertThrows(UnsupportedOperationException::class.java) {
            (result.issues as MutableList).clear()
        }
    }

    @Test
    fun failureCategoriesAreStructuredNotThrownExceptions() {
        for (category in FailureCategory.entries) {
            val result = CapabilityResult.Failure(CapabilityFailure(category))
            assertEquals(category, result.failure.category)
        }
    }

    @Test
    fun opaqueAudioAndVoiceReferencesDoNotAcceptPathsOrUrls() {
        assertEquals(AudioReference.of("fixture-001"), AudioReference.of("fixture-001"))
        assertEquals("voice-001", VoiceId.of("voice-001").token)
        for (token in
            listOf("", "/tmp/audio.wav", "C:\\audio.wav", "https://audio", "a b")) assertThrows(
            IllegalArgumentException::class.java
        ) {
            AudioReference.of(token)
        }
    }

    @Test
    fun recognitionDistinguishesLockedFromPermittedDetection() {
        val locked =
            RecognitionRequest(
                AudioReference.of("mic-1"),
                LanguageSelection.Locked(direction.source),
            )
        assertTrue(locked.language is LanguageSelection.Locked)
        val candidates = mutableSetOf(direction.source, direction.target)
        val automatic = LanguageSelection.Automatic(direction.source, candidates)
        candidates.clear()
        assertEquals(2, automatic.candidates.size)
        assertEquals(direction.source, automatic.expectedLanguage)
    }

    @Test
    fun recognitionVocabularyIsImmutableAndNonempty() {
        val vocabulary = mutableListOf("fixture term")
        val request =
            RecognitionRequest(
                AudioReference.of("wav-1"),
                LanguageSelection.Locked(direction.source),
                vocabulary,
            )
        vocabulary.clear()
        assertEquals(listOf("fixture term"), request.vocabulary)
        assertThrows(IllegalArgumentException::class.java) {
            RecognitionRequest(
                AudioReference.of("wav-1"),
                LanguageSelection.Locked(direction.source),
                listOf(" "),
            )
        }
    }

    @Test
    fun unknownLanguageIsSeparateFromALanguageIdentity() {
        val result = DetectedLanguage.Unknown("insufficient evidence")
        assertEquals("insufficient evidence", result.explanation)
        assertEquals(direction.source, DetectedLanguage.Known(direction.source).language)
    }

    @Test
    fun synthesisExpressesLocaleRateVoiceAndFutureSpeakerPreference() {
        val request =
            SynthesisRequest(
                "output",
                LanguageId.parse("es-MX"),
                VoiceId.of("preferred"),
                0.9,
                SpeakerPreservation.REQUESTED,
            )
        assertEquals("es-MX", request.language.tag)
        assertEquals(SpeakerPreservation.REQUESTED, request.speakerPreservation)
        assertEquals(0.9, request.rate, 0.0)
        assertEquals(
            "generated-1",
            SynthesizedSpeech(AudioReference.of("generated-1"), request.language, null).audio.token,
        )
    }

    @Test
    fun synthesisRejectsInvalidRates() {
        for (rate in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) assertThrows(
            IllegalArgumentException::class.java
        ) {
            SynthesisRequest("text", direction.target, rate = rate)
        }
    }

    @Test
    fun possibleSarcasmDoesNotAssertSpeakerIntent() {
        val note = TranslationNote(TranslationNoteKind.SARCASM, "Possible alternate interpretation")
        assertEquals(IssueCertainty.POSSIBLE, note.certainty)
        assertNotEquals(note, note.copy(certainty = IssueCertainty.OBSERVED))
    }

    @Test
    fun capabilityAndSupportDescriptionsAreImmutable() {
        val modes = mutableSetOf(ExecutionMode.OFFLINE)
        val features = mutableSetOf(CapabilityFeature.CONVERSATION_CONTEXT)
        val description = CapabilityDescription(modes, features)
        val report = SupportReport(features)
        modes.clear()
        features.clear()
        assertEquals(setOf(ExecutionMode.OFFLINE), description.executionModes)
        assertEquals(setOf(CapabilityFeature.CONVERSATION_CONTEXT), report.unsupportedFeatures)
        assertTrue(report.hasUnsupported)
        assertThrows(IllegalArgumentException::class.java) { CapabilityDescription(emptySet()) }
    }

    @Test
    fun criticalExpectationsCoverValuesAndSemanticIntentSeparately() {
        val value = NumericValue.parse("12.50")
        val expectations =
            listOf(
                CriticalExpectation.NumberLiteral(value),
                CriticalExpectation.Currency(value, CurrencyCode.of("USD")),
                CriticalExpectation.Measurement(value, UnitId.of("metre")),
                CriticalExpectation.Date(LocalDate.of(2026, 10, 12)),
                CriticalExpectation.Time(LocalTime.of(15, 30)),
                CriticalExpectation.Negation("do not"),
                CriticalExpectation.Name("Alex"),
                CriticalExpectation.Prohibition("do not start"),
                CriticalExpectation.Terminology(
                    TerminologyHint("source", targetMeaning = "meaning")
                ),
            )
        assertEquals(CriticalContentKind.entries.toSet(), expectations.map { it.kind }.toSet())
        assertEquals(NumericValue.parse("+012.500"), value)
        assertNotEquals(NumericValue.parse("12.51"), value)
        for (invalid in listOf("NaN", "1,50", "1e3", "", ".5")) assertThrows(
            IllegalArgumentException::class.java
        ) {
            NumericValue.parse(invalid)
        }
    }

    @Test
    fun aPassCannotHideUncheckedContentOrCriticalFindings() {
        assertThrows(IllegalArgumentException::class.java) {
            VerificationReport(VerificationStatus.PASS)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VerificationReport(
                VerificationStatus.PASS,
                checksPerformed = setOf(VerificationCheck.EXACT_INTEGER_LITERALS),
                uncheckedKinds = setOf(CriticalContentKind.NEGATION),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            VerificationReport(
                VerificationStatus.PASS_WITH_WARNINGS,
                listOf(
                    CapabilityIssue(
                        IssueCode.NUMBER_MISMATCH,
                        IssueSeverity.CRITICAL,
                        IssueCertainty.POSSIBLE,
                    )
                ),
                setOf(VerificationCheck.SEMANTIC_CONTENT),
            )
        }
    }
}
