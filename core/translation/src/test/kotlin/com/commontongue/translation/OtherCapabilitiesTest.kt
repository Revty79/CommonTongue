package com.commontongue.translation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OtherCapabilitiesTest {
    @Test
    fun speechRecognizerFakeUsesOpaqueAudioAndLockedLanguage() = runTest {
        val recognizer =
            object : SpeechRecognizer {
                override val description = fullDescription

                override suspend fun recognize(
                    request: RecognitionRequest
                ): CapabilityResult<RecognizedSpeech> {
                    executionFailure(description, request.execution)?.let {
                        return CapabilityResult.Failure(it)
                    }
                    val locked = request.language as LanguageSelection.Locked
                    assertEquals("wav-fixture", request.audio.token)
                    return CapabilityResult.Success(
                        RecognizedSpeech(
                            "fixture transcription",
                            locked.language,
                            CompletionStatus.COMPLETE,
                        ),
                        ExecutionMode.OFFLINE,
                    )
                }
            }
        val result =
            recognizer.recognize(
                RecognitionRequest(
                    AudioReference.of("wav-fixture"),
                    LanguageSelection.Locked(direction.source),
                )
            ) as CapabilityResult.Success
        assertEquals(direction.source, result.value.languageUsed)
        assertSame(ConfidenceEvidence.Unavailable, result.value.confidence)
    }

    @Test
    fun languageDetectionFakePreservesLockAndCanReportUnknown() = runTest {
        val detector =
            object : LanguageDetector {
                override val description = fullDescription

                override suspend fun detect(
                    request: LanguageDetectionRequest
                ): CapabilityResult<DetectedLanguage> {
                    val selected = request.selection
                    return CapabilityResult.Success(
                        if (selected is LanguageSelection.Locked)
                            DetectedLanguage.Known(selected.language)
                        else DetectedLanguage.Unknown(),
                        ExecutionMode.OFFLINE,
                    )
                }
            }
        val input = DetectionInput.Text("ambiguous fixture")
        val locked =
            detector.detect(
                LanguageDetectionRequest(input, LanguageSelection.Locked(direction.source))
            ) as CapabilityResult.Success
        assertEquals(direction.source, (locked.value as DetectedLanguage.Known).language)
        val automatic =
            detector.detect(LanguageDetectionRequest(input, LanguageSelection.Automatic()))
                as CapabilityResult.Success
        assertTrue(automatic.value is DetectedLanguage.Unknown)
    }

    @Test
    fun synthesizerReportsActualVoiceAndUnsupportedSpeakerPreference() = runTest {
        val synthesizer =
            object : SpeechSynthesizer {
                override val description = fullDescription

                override suspend fun synthesize(request: SynthesisRequest) =
                    CapabilityResult.Success(
                        SynthesizedSpeech(
                            AudioReference.of("generated-fixture"),
                            request.language,
                            VoiceId.of("actual-voice"),
                        ),
                        ExecutionMode.OFFLINE,
                        support = SupportReport(setOf(CapabilityFeature.SPEAKER_PRESERVATION)),
                    )
            }
        val result =
            synthesizer.synthesize(
                SynthesisRequest(
                    "fixture",
                    direction.target,
                    VoiceId.of("preferred-voice"),
                    speakerPreservation = SpeakerPreservation.REQUESTED,
                )
            )
        assertEquals("actual-voice", result.value.voiceUsed!!.token)
        assertTrue(CapabilityFeature.SPEAKER_PRESERVATION in result.support.unsupportedFeatures)
    }

    @Test
    fun criticalIdentificationIsReplaceableWithoutAConcreteDetector() = runTest {
        val analyzer =
            object : CriticalContentAnalyzer {
                override val description = fullDescription

                override suspend fun identify(request: CriticalContentRequest) =
                    CapabilityResult.Success(
                        CriticalContentAnalysis(
                            listOf(CriticalExpectation.NumberLiteral(NumericValue.parse("12"))),
                            setOf(CriticalContentKind.NEGATION),
                        ),
                        ExecutionMode.OFFLINE,
                    )
            }
        val result = analyzer.identify(CriticalContentRequest("fixture 12", direction.source))
        assertEquals(CriticalContentKind.NUMBER, result.value.expectations.single().kind)
        assertTrue(CriticalContentKind.NEGATION in result.value.uncheckedKinds)
    }

    @Test
    fun notesFakeReturnsPossibleInterpretationWithoutPerformingDetection() = runTest {
        val notes =
            object : TranslationNotesAnalyzer {
                override val description = fullDescription

                override suspend fun analyze(request: VerificationRequest) =
                    CapabilityResult.Success(
                        TranslationNotes(
                            listOf(
                                TranslationNote(
                                    TranslationNoteKind.SARCASM,
                                    "Test fixture only: possible interpretation",
                                )
                            )
                        ),
                        ExecutionMode.OFFLINE,
                    )
            }
        val result = notes.analyze(VerificationRequest("source", "target", direction))
        assertEquals(IssueCertainty.POSSIBLE, result.value.notes.single().certainty)
    }
}
