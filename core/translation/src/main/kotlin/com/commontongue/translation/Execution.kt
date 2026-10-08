package com.commontongue.translation

enum class ExecutionRequirement {
    OFFLINE_REQUIRED,
    ONLINE_ALLOWED,
}

enum class ExecutionMode {
    OFFLINE,
    ONLINE,
}

enum class CapabilityFeature {
    LANGUAGE_DETECTION,
    CONVERSATION_CONTEXT,
    TERMINOLOGY_HINTS,
    DOMAIN_CONTEXT,
    REGIONAL_PREFERENCES,
    VERIFICATION,
    STREAMING_RECOGNITION,
    TRANSLATION_NOTES,
    VOICE_PREFERENCE,
    SPEECH_RATE,
    SPEAKER_PRESERVATION,
}

/** Declared support is not evidence that a particular output preserves meaning. */
class CapabilityDescription(
    executionModes: Set<ExecutionMode>,
    features: Set<CapabilityFeature> = emptySet(),
    policies: Set<TranslationRequirement> = emptySet(),
) {
    val executionModes = snapshotSet(executionModes)
    val features = snapshotSet(features)
    val policies = snapshotSet(policies)

    init {
        require(executionModes.isNotEmpty())
    }
}

class SupportReport(
    unsupportedFeatures: Set<CapabilityFeature> = emptySet(),
    unsupportedPolicies: Set<TranslationRequirement> = emptySet(),
) {
    val unsupportedFeatures = snapshotSet(unsupportedFeatures)
    val unsupportedPolicies = snapshotSet(unsupportedPolicies)
    val hasUnsupported: Boolean
        get() = unsupportedFeatures.isNotEmpty() || unsupportedPolicies.isNotEmpty()

    internal fun merged(other: SupportReport) =
        SupportReport(
            unsupportedFeatures + other.unsupportedFeatures,
            unsupportedPolicies + other.unsupportedPolicies,
        )
}

/** All implementations must call this check before work that could access a network. */
fun executionFailure(
    description: CapabilityDescription,
    requirement: ExecutionRequirement,
): CapabilityFailure? =
    if (
        requirement == ExecutionRequirement.OFFLINE_REQUIRED &&
            ExecutionMode.OFFLINE !in description.executionModes
    )
        CapabilityFailure(FailureCategory.OFFLINE_REQUIREMENT_NOT_MET)
    else null
