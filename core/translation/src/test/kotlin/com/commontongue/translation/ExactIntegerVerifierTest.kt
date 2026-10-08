package com.commontongue.translation

import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ExactIntegerVerifierTest {
    private fun request(source: String, target: String, vararg values: String) =
        VerificationRequest(
            source,
            target,
            direction,
            values.map { CriticalExpectation.NumberLiteral(NumericValue.parse(it)) },
        )

    private suspend fun report(request: VerificationRequest) =
        (ExactIntegerVerifier().verify(request) as CapabilityResult.Success).value

    @Test
    fun explicitIntegerValuesPassWithALimitedScope() = runTest {
        val result =
            report(request("Take 12 bolts and 3 nuts.", "Toma 3 tuercas y 12 pernos.", "12", "3"))
        assertEquals(VerificationStatus.PASS, result.status)
        assertEquals(setOf(VerificationCheck.EXACT_INTEGER_LITERALS), result.checksPerformed)
        assertTrue(result.uncheckedKinds.isEmpty())
    }

    @Test
    fun valueChangesFailTheScopedCheck() = runTest {
        val result = report(request("Take 12 bolts", "Toma 13 pernos", "12"))
        assertEquals(VerificationStatus.FAIL, result.status)
        assertEquals(IssueCode.NUMBER_MISMATCH, result.findings.single().code)
        assertEquals(IssueCertainty.OBSERVED, result.findings.single().certainty)
        assertEquals(IssueSeverity.CRITICAL, result.findings.single().severity)
    }

    @Test
    fun repeatedValuesCannotBeSilentlyDropped() = runTest {
        assertEquals(
            VerificationStatus.FAIL,
            report(request("12 bolts and 12 nuts", "12 pernos", "12", "12")).status,
        )
        assertEquals(
            VerificationStatus.PASS,
            report(request("12 bolts and 12 nuts", "12 pernos y 12 tuercas", "12", "12")).status,
        )
    }

    @Test
    fun extraDigitWrittenValuesFailTheScopedCheck() = runTest {
        assertEquals(
            VerificationStatus.FAIL,
            report(request("12 bolts", "12 pernos y 3 tuercas", "12")).status,
        )
    }

    @Test
    fun signsLeadingZerosAndIntegerScaleNormalizeExactly() = runTest {
        assertEquals(
            VerificationStatus.PASS,
            report(request("Use -12 and +003", "Usa 3 y -012", "-12", "3.0")).status,
        )
    }

    @Test
    fun noExpectationsCannotProduceAVacuousPass() = runTest {
        assertEquals(
            VerificationStatus.NEEDS_REVIEW,
            report(request("conversation", "conversación")).status,
        )
    }

    @Test
    fun expectationsMissingFromSourceRequireReview() = runTest {
        assertEquals(
            VerificationStatus.NEEDS_REVIEW,
            report(request("12 bolts", "12 pernos", "13")).status,
        )
    }

    @Test
    fun partialSourceCoverageRequiresReview() = runTest {
        assertEquals(
            VerificationStatus.NEEDS_REVIEW,
            report(request("12 bolts and 3 nuts", "12 pernos y 3 tuercas", "12")).status,
        )
    }

    @Test
    fun writtenOutTargetNumbersAreUnverifiedNotDeclaredWrong() = runTest {
        assertEquals(
            VerificationStatus.NEEDS_REVIEW,
            report(request("12 bolts", "doce pernos", "12")).status,
        )
    }

    @Test
    fun decimalsGroupedValuesDatesAndTimesAreOutsideTheScope() = runTest {
        for (text in
            listOf("12.5", "1,000", "1 000", "2026-10-12", "15:30", "12/10", "12.5cm", "١٢")) {
            assertEquals(
                text,
                VerificationStatus.NEEDS_REVIEW,
                report(request("Value $text", "Valor $text", "12")).status,
            )
        }
    }

    @Test
    fun decimalExpectationsDoNotPretendToPass() = runTest {
        assertEquals(
            VerificationStatus.NEEDS_REVIEW,
            report(request("12.5 litres", "12.5 litros", "12.5")).status,
        )
    }

    @Test
    fun semanticExpectationsRemainUncheckedEvenWhenNumbersMatch() = runTest {
        val expectations =
            listOf(
                CriticalExpectation.NumberLiteral(NumericValue.parse("12")),
                CriticalExpectation.Negation("do not"),
                CriticalExpectation.Prohibition("do not start"),
                CriticalExpectation.Date(LocalDate.of(2026, 10, 12)),
            )
        val result =
            report(
                VerificationRequest(
                    "Do not start 12 engines",
                    "Enciende 12 motores",
                    direction,
                    expectations,
                )
            )
        assertEquals(VerificationStatus.NEEDS_REVIEW, result.status)
        assertTrue(CriticalContentKind.NEGATION in result.uncheckedKinds)
        assertTrue(CriticalContentKind.PROHIBITION in result.uncheckedKinds)
        assertTrue(VerificationCheck.EXACT_INTEGER_LITERALS in result.checksPerformed)
    }

    @Test
    fun invalidAndExcessiveInputsUseStructuredFailures() = runTest {
        val verifier = ExactIntegerVerifier(maxCharacters = 10)
        assertEquals(
            FailureCategory.INPUT_INVALID,
            (verifier.verify(request("", "target", "12")) as CapabilityResult.Failure)
                .failure
                .category,
        )
        assertEquals(
            FailureCategory.INPUT_TOO_LONG,
            (verifier.verify(request("very long source", "12", "12")) as CapabilityResult.Failure)
                .failure
                .category,
        )
    }

    @Test
    fun boundaryPunctuationAndNegativeZeroAreHandled() = runTest {
        assertEquals(
            VerificationStatus.PASS,
            report(request("Use (12), and 0.", "Usa 12, y -0.", "12", "0")).status,
        )
    }

    @Test
    fun embeddedIdentifiersAreNotExtractedAsOrdinaryNumbers() = runTest {
        assertEquals(
            VerificationStatus.NEEDS_REVIEW,
            report(request("Use M12", "Usa M12", "12")).status,
        )
    }
}
