package com.commontongue.translation

import com.commontongue.domain.DeviceQualityProfile
import com.commontongue.domain.TranslationDirection

enum class TranslationRequirement {
    MEANING_OVER_LITERAL_WORDING,
    PRESERVE_TONE,
    PRESERVE_SLANG,
    PRESERVE_IDIOMS,
    PRESERVE_PROFANITY,
    DO_NOT_INVENT_INFORMATION,
    PRESERVE_UNCERTAINTY,
    PRESERVE_NAMES,
    PRESERVE_NUMBERS,
    PRESERVE_UNITS,
    PRESERVE_NEGATION,
    HONOR_TERMINOLOGY,
    NATURAL_TARGET_LANGUAGE,
}

enum class UnsupportedHandling {
    REPORT,
    REJECT,
}

class TranslationPolicy(
    requirements: Set<TranslationRequirement> = TranslationRequirement.entries.toSet(),
    val unsupportedHandling: UnsupportedHandling = UnsupportedHandling.REPORT,
) {
    val requirements = snapshotSet(requirements)
}

/** User text validation is performed by the use case and returns a structured expected failure. */
class TranslationRequest(
    val sourceText: String,
    val direction: TranslationDirection,
    val context: ConversationContext = ConversationContext.None,
    terminology: List<TerminologyHint> = emptyList(),
    val domain: DomainContext = DomainContext.GENERAL,
    val regionalPreference: RegionalPreference? = null,
    val policy: TranslationPolicy = TranslationPolicy(),
    criticalContent: List<CriticalExpectation> = emptyList(),
    val execution: ExecutionRequirement = ExecutionRequirement.OFFLINE_REQUIRED,
    val qualityProfile: DeviceQualityProfile = DeviceQualityProfile.STANDARD,
) {
    val terminology = snapshotList(terminology)
    val criticalContent = snapshotList(criticalContent)

    fun validationFailure(): CapabilityFailure? =
        when {
            sourceText.isBlank() ->
                CapabilityFailure(FailureCategory.INPUT_INVALID, "Source text is blank")
            regionalPreference != null &&
                regionalPreference.locale.baseLanguage != direction.target.baseLanguage ->
                CapabilityFailure(
                    FailureCategory.INPUT_INVALID,
                    "Regional preference must match the target base language",
                )
            else -> null
        }
}

class Ambiguity(val sourceExcerpt: String, interpretations: List<String>) {
    val interpretations = snapshotList(interpretations)

    init {
        require(sourceExcerpt.isNotBlank())
        require(this.interpretations.size >= 2 && this.interpretations.all { it.isNotBlank() })
    }
}

sealed interface VerificationState {
    data object Unverified : VerificationState

    data class Evaluated(val report: VerificationReport) : VerificationState
}

class TranslatedText(
    val text: String,
    val directionUsed: TranslationDirection,
    val completion: CompletionStatus = CompletionStatus.COMPLETE,
    ambiguities: List<Ambiguity> = emptyList(),
    val confidence: ConfidenceEvidence = ConfidenceEvidence.Unavailable,
    val verification: VerificationState = VerificationState.Unverified,
) {
    val ambiguities = snapshotList(ambiguities)

    internal fun unverified() =
        TranslatedText(text, directionUsed, completion, ambiguities, confidence)

    internal fun verified(report: VerificationReport) =
        TranslatedText(
            text,
            directionUsed,
            completion,
            ambiguities,
            confidence,
            VerificationState.Evaluated(report),
        )
}

typealias TranslationResult = CapabilityResult<TranslatedText>
