package com.commontongue.translation

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Opt-in, scoped digit-value/multiplicity check. No word-number, decimal punctuation, unit,
 * currency, date, negation, name, or safety interpretation. Ambiguous formats require review.
 */
class ExactIntegerVerifier(private val maxCharacters: Int = 16000) : TranslationVerifier {
    init {
        require(maxCharacters > 0)
    }

    override val description =
        CapabilityDescription(
            setOf(ExecutionMode.OFFLINE),
            setOf(CapabilityFeature.VERIFICATION),
        )
    private val runs = Regex("[+-]?[0-9]+(?:[.,:/-][0-9]+)*")
    private val integer = Regex("[+-]?[0-9]+")

    private fun values(text: String): Map<NumericValue, Int>? {
        if (
            text.any { it.isDigit() && it !in '0'..'9' } ||
                Regex("[0-9]\\p{Zs}+[0-9]").containsMatchIn(text)
        )
            return null
        val matches = runs.findAll(text).toList()
        val values = mutableListOf<NumericValue>()
        for (match in matches) {
            if (match.value.length > 128 || !integer.matches(match.value)) return null
            val before = text.getOrNull(match.range.first - 1)
            val after = text.getOrNull(match.range.last + 1)
            if (before != null && (before.isLetterOrDigit() || before in "_+-.,:/")) return null
            if (after != null && (after.isLetterOrDigit() || after == '_')) return null
            values += NumericValue.parse(match.value)
        }
        return values.groupingBy { it }.eachCount()
    }

    override suspend fun verify(
        request: VerificationRequest
    ): CapabilityResult<VerificationReport> {
        currentCoroutineContext().ensureActive()
        executionFailure(description, request.execution)?.let {
            return CapabilityResult.Failure(it)
        }
        if (request.sourceText.isBlank() || request.translatedText.isBlank())
            return CapabilityResult.Failure(CapabilityFailure(FailureCategory.INPUT_INVALID))
        if (
            request.sourceText.length > maxCharacters ||
                request.translatedText.length > maxCharacters
        )
            return CapabilityResult.Failure(CapabilityFailure(FailureCategory.INPUT_TOO_LONG))
        val numeric = request.expectations.filterIsInstance<CriticalExpectation.NumberLiteral>()
        val unchecked =
            request.expectations
                .filterNot { it is CriticalExpectation.NumberLiteral }
                .map { it.kind }
                .toSet()
        fun review(
            reason: String,
            checks: Set<VerificationCheck> = emptySet(),
        ): CapabilityResult<VerificationReport> =
            CapabilityResult.Success(
                VerificationReport(
                    VerificationStatus.NEEDS_REVIEW,
                    listOf(
                        CapabilityIssue(
                            IssueCode.VERIFICATION_INCOMPLETE,
                            IssueSeverity.CAUTION,
                            IssueCertainty.OBSERVED,
                            reason,
                        )
                    ),
                    checks,
                    unchecked +
                        if (checks.isEmpty() && numeric.isNotEmpty())
                            setOf(CriticalContentKind.NUMBER)
                        else emptySet(),
                ),
                ExecutionMode.OFFLINE,
            )
        if (numeric.isEmpty())
            return review("No explicit digit-value expectations supplied; meaning is unverified")
        if (numeric.any { !it.value.isInteger })
            return review("Decimal values require another verifier")
        val source =
            values(request.sourceText)
                ?: return review("Source numeric format is outside the integer scope")
        val target =
            values(request.translatedText)
                ?: return review("Target numeric format is outside the integer scope")
        val expected = numeric.map { it.value }.groupingBy { it }.eachCount()
        if (source != expected)
            return review("Expectations do not cover the source integer multiset")
        if (target.isEmpty())
            return review("Target may use written-out numbers; this verifier cannot interpret them")
        val checks = setOf(VerificationCheck.EXACT_INTEGER_LITERALS)
        currentCoroutineContext().ensureActive()
        if (target != expected)
            return CapabilityResult.Success(
                VerificationReport(
                    VerificationStatus.FAIL,
                    listOf(
                        CapabilityIssue(
                            IssueCode.NUMBER_MISMATCH,
                            IssueSeverity.CRITICAL,
                            IssueCertainty.OBSERVED,
                            "Digit-written integer values or their multiplicity differ; semantic interpretation is outside this check",
                        )
                    ),
                    checks,
                    unchecked,
                ),
                ExecutionMode.OFFLINE,
            )
        if (unchecked.isNotEmpty())
            return review("Other requested critical content remains unchecked", checks)
        return CapabilityResult.Success(
            VerificationReport(VerificationStatus.PASS, checksPerformed = checks),
            ExecutionMode.OFFLINE,
        )
    }
}
