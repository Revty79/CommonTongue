package com.commontongue.translation

import com.commontongue.domain.LanguageId
import com.commontongue.domain.TranslationDirection
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/** Explicit decimal value, never locale-dependent parsing or binary floating-point arithmetic. */
@JvmInline
value class NumericValue private constructor(val decimal: String) {
    val isInteger: Boolean
        get() = '.' !in decimal

    companion object {
        fun parse(value: String): NumericValue {
            require(value.length <= 128 && value.matches(Regex("[+-]?[0-9]+(?:\\.[0-9]+)?")))
            return NumericValue(BigDecimal(value).stripTrailingZeros().toPlainString())
        }
    }
}

enum class CriticalContentKind {
    NUMBER,
    CURRENCY,
    MEASUREMENT,
    DATE,
    TIME,
    NEGATION,
    NAME,
    PROHIBITION,
    TERMINOLOGY,
}

@JvmInline
value class CurrencyCode private constructor(val code: String) {
    companion object {
        fun of(code: String): CurrencyCode {
            require(code.matches(Regex("[A-Z]{3}")))
            return CurrencyCode(code)
        }
    }
}

@JvmInline
value class UnitId private constructor(val id: String) {
    companion object {
        fun of(id: String): UnitId {
            require(id.matches(Regex("[a-z][a-z0-9._-]{0,63}")))
            return UnitId(id)
        }
    }
}

sealed interface CriticalExpectation {
    val kind: CriticalContentKind

    /**
     * Requires digit-written values with multiplicity. Written-out numbers need another verifier.
     */
    data class NumberLiteral(val value: NumericValue) : CriticalExpectation {
        override val kind = CriticalContentKind.NUMBER
    }

    data class Currency(val amount: NumericValue, val currencyCode: CurrencyCode) :
        CriticalExpectation {
        override val kind = CriticalContentKind.CURRENCY
    }

    data class Measurement(val amount: NumericValue, val unit: UnitId) : CriticalExpectation {
        override val kind = CriticalContentKind.MEASUREMENT
    }

    data class Date(val value: LocalDate) : CriticalExpectation {
        override val kind = CriticalContentKind.DATE
    }

    data class Time(val value: LocalTime) : CriticalExpectation {
        override val kind = CriticalContentKind.TIME
    }

    data class Negation(val sourceExcerpt: String) : CriticalExpectation {
        override val kind = CriticalContentKind.NEGATION

        init {
            require(sourceExcerpt.isNotBlank())
        }
    }

    data class Name(val sourceName: String) : CriticalExpectation {
        override val kind = CriticalContentKind.NAME

        init {
            require(sourceName.isNotBlank())
        }
    }

    data class Prohibition(val sourceExcerpt: String) : CriticalExpectation {
        override val kind = CriticalContentKind.PROHIBITION

        init {
            require(sourceExcerpt.isNotBlank())
        }
    }

    data class Terminology(val hint: TerminologyHint) : CriticalExpectation {
        override val kind = CriticalContentKind.TERMINOLOGY
    }
}

class CriticalContentRequest(
    val text: String,
    val language: LanguageId,
    val context: ConversationContext = ConversationContext.None,
    val domain: DomainContext = DomainContext.GENERAL,
    terminology: List<TerminologyHint> = emptyList(),
    val execution: ExecutionRequirement = ExecutionRequirement.OFFLINE_REQUIRED,
) {
    val terminology = snapshotList(terminology)
}

class CriticalContentAnalysis(
    expectations: List<CriticalExpectation>,
    uncheckedKinds: Set<CriticalContentKind>,
) {
    val expectations = snapshotList(expectations)
    val uncheckedKinds = snapshotSet(uncheckedKinds)
}

class VerificationRequest(
    val sourceText: String,
    val translatedText: String,
    val direction: TranslationDirection,
    expectations: List<CriticalExpectation> = emptyList(),
    val context: ConversationContext = ConversationContext.None,
    val execution: ExecutionRequirement = ExecutionRequirement.OFFLINE_REQUIRED,
) {
    val expectations = snapshotList(expectations)
}

enum class VerificationStatus {
    PASS,
    PASS_WITH_WARNINGS,
    NEEDS_REVIEW,
    FAIL,
}

enum class VerificationCheck {
    EXACT_INTEGER_LITERALS,
    SEMANTIC_CONTENT,
}

/** PASS is restricted to checksPerformed. It never means universal translation correctness. */
class VerificationReport(
    val status: VerificationStatus,
    findings: List<CapabilityIssue> = emptyList(),
    checksPerformed: Set<VerificationCheck> = emptySet(),
    uncheckedKinds: Set<CriticalContentKind> = emptySet(),
) {
    val findings = snapshotList(findings)
    val checksPerformed = snapshotSet(checksPerformed)
    val uncheckedKinds = snapshotSet(uncheckedKinds)

    init {
        if (status == VerificationStatus.PASS || status == VerificationStatus.PASS_WITH_WARNINGS) {
            require(this.checksPerformed.isNotEmpty() && this.uncheckedKinds.isEmpty())
            require(this.findings.none { it.severity == IssueSeverity.CRITICAL })
        }
        if (status == VerificationStatus.PASS) require(this.findings.isEmpty())
        if (status == VerificationStatus.PASS_WITH_WARNINGS) require(this.findings.isNotEmpty())
        if (status == VerificationStatus.FAIL) require(this.findings.isNotEmpty())
    }
}
