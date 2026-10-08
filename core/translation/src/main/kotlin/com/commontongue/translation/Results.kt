package com.commontongue.translation

enum class FailureCategory {
    ENGINE_NOT_AVAILABLE,
    LANGUAGE_NOT_SUPPORTED,
    MODEL_NOT_INSTALLED,
    INPUT_INVALID,
    INPUT_TOO_LONG,
    RESOURCE_LIMIT,
    OFFLINE_REQUIREMENT_NOT_MET,
    UNSUPPORTED_CAPABILITY,
    INFERENCE_FAILED,
    VERIFICATION_FAILED,
    CANCELLED,
}

data class CapabilityFailure(val category: FailureCategory, val explanation: String? = null)

enum class IssueSeverity {
    INFO,
    CAUTION,
    CRITICAL,
}

enum class IssueCertainty {
    POSSIBLE,
    OBSERVED,
}

enum class IssueCode {
    AMBIGUOUS_SOURCE,
    POSSIBLE_IDIOM,
    POSSIBLE_SLANG,
    POSSIBLE_SARCASM,
    TERMINOLOGY_UNCERTAINTY,
    NUMBER_MISMATCH,
    UNIT_MISMATCH,
    NEGATION_MISMATCH,
    NAME_UNCERTAINTY,
    LOW_RECOGNITION_CERTAINTY,
    UNSUPPORTED_POLICY,
    UNSUPPORTED_CAPABILITY,
    CONTEXT_UNAVAILABLE,
    VERIFICATION_FAILED,
    VERIFICATION_INCOMPLETE,
}

/** Severity is potential impact; certainty describes the evidence, not grammatical fluency. */
data class CapabilityIssue(
    val code: IssueCode,
    val severity: IssueSeverity,
    val certainty: IssueCertainty,
    val explanation: String? = null,
)

enum class ConfidenceCalibration {
    CALIBRATED,
    UNCALIBRATED,
}

sealed interface ConfidenceEvidence {
    data object Unavailable : ConfidenceEvidence

    /** Only a real underlying measure may supply this; it is not automatically a probability. */
    data class Reported(
        val normalizedScore: Double,
        val calibration: ConfidenceCalibration,
        val basis: String,
    ) : ConfidenceEvidence {
        init {
            require(normalizedScore.isFinite() && normalizedScore in 0.0..1.0)
            require(basis.isNotBlank())
        }
    }
}

enum class CompletionStatus {
    COMPLETE,
    PARTIAL,
}

sealed interface CapabilityResult<out T> {
    class Success<T>(
        val value: T,
        val executionMode: ExecutionMode,
        issues: List<CapabilityIssue> = emptyList(),
        val support: SupportReport = SupportReport(),
    ) : CapabilityResult<T> {
        val issues = snapshotList(issues)
    }

    class Failure(
        val failure: CapabilityFailure,
        issues: List<CapabilityIssue> = emptyList(),
    ) : CapabilityResult<Nothing> {
        val issues = snapshotList(issues)
    }
}
