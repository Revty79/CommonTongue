package com.commontongue.translation

/**
 * Implementations must reject unmet offline requirements before work, report unsupported requests,
 * propagate coroutine cancellation, and describe actual execution honestly. No automatic routing or
 * online fallback is supplied by these contracts. Expected failures use CapabilityResult.Failure.
 */
interface Capability {
    val description: CapabilityDescription
}

interface SpeechRecognizer : Capability {
    /**
     * Locked input must not be auto-detected; only LanguageSelection.Automatic permits detection.
     */
    suspend fun recognize(request: RecognitionRequest): CapabilityResult<RecognizedSpeech>
}

interface Translator : Capability {
    suspend fun translate(request: TranslationRequest): TranslationResult
}

interface SpeechSynthesizer : Capability {
    /** Unsupported voice/rate/speaker preferences must be reported, never claimed as applied. */
    suspend fun synthesize(request: SynthesisRequest): CapabilityResult<SynthesizedSpeech>
}

interface LanguageDetector : Capability {
    /**
     * A lock is authoritative; uncertainty/absence of a detection is a Known/Unknown distinction.
     */
    suspend fun detect(request: LanguageDetectionRequest): CapabilityResult<DetectedLanguage>
}

interface CriticalContentAnalyzer : Capability {
    suspend fun identify(request: CriticalContentRequest): CapabilityResult<CriticalContentAnalysis>
}

interface TranslationVerifier : Capability {
    suspend fun verify(request: VerificationRequest): CapabilityResult<VerificationReport>
}

enum class TranslationNoteKind {
    SARCASM,
    IDIOM,
    SLANG,
    REGIONAL_EXPRESSION,
    ALTERNATE_INTERPRETATION,
    TECHNICAL_TERMINOLOGY,
    UNCERTAINTY,
}

data class TranslationNote(
    val kind: TranslationNoteKind,
    val explanation: String,
    val certainty: IssueCertainty = IssueCertainty.POSSIBLE,
) {
    init {
        require(explanation.isNotBlank())
    }
}

class TranslationNotes(notes: List<TranslationNote>) {
    val notes = snapshotList(notes)
}

interface TranslationNotesAnalyzer : Capability {
    /** Analysis is optional. POSSIBLE sarcasm is not an assertion about the speaker's intent. */
    suspend fun analyze(request: VerificationRequest): CapabilityResult<TranslationNotes>
}
