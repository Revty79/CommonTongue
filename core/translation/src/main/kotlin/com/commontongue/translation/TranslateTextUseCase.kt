package com.commontongue.translation

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class TranslationStage {
    VALIDATION,
    TRANSLATION,
    VERIFICATION,
}

sealed interface TranslationOutcome {
    class Complete(val result: CapabilityResult.Success<TranslatedText>) : TranslationOutcome

    class NeedsReview(val result: CapabilityResult.Success<TranslatedText>) : TranslationOutcome

    class Failed(
        val failure: CapabilityFailure,
        val stage: TranslationStage,
        val candidate: TranslatedText? = null,
        issues: List<CapabilityIssue> = emptyList(),
        val support: SupportReport = SupportReport(),
    ) : TranslationOutcome {
        val issues = snapshotList(issues)
    }
}

/**
 * Text only. One selected translator and verifier; no fallback, persistence, notes, or audio chain.
 */
class TranslateTextUseCase(
    private val translator: Translator,
    private val verifier: TranslationVerifier,
    private val maxInputCharacters: Int = 16000,
) {
    init {
        require(maxInputCharacters > 0)
    }

    private fun support(request: TranslationRequest): SupportReport {
        val requested = buildSet {
            if (request.context.turns.isNotEmpty()) add(CapabilityFeature.CONVERSATION_CONTEXT)
            if (request.terminology.isNotEmpty()) add(CapabilityFeature.TERMINOLOGY_HINTS)
            if (request.domain != DomainContext.GENERAL) add(CapabilityFeature.DOMAIN_CONTEXT)
            if (request.regionalPreference != null) add(CapabilityFeature.REGIONAL_PREFERENCES)
        }
        return SupportReport(
            requested - translator.description.features,
            request.policy.requirements - translator.description.policies,
        )
    }

    private fun supportIssues(report: SupportReport): List<CapabilityIssue> =
        report.unsupportedFeatures.map {
            CapabilityIssue(
                if (it == CapabilityFeature.CONVERSATION_CONTEXT) IssueCode.CONTEXT_UNAVAILABLE
                else IssueCode.UNSUPPORTED_CAPABILITY,
                IssueSeverity.CAUTION,
                IssueCertainty.OBSERVED,
                "Requested feature is unsupported: $it",
            )
        } +
            report.unsupportedPolicies.map {
                CapabilityIssue(
                    IssueCode.UNSUPPORTED_POLICY,
                    IssueSeverity.CAUTION,
                    IssueCertainty.OBSERVED,
                    "Requested policy is unsupported: $it",
                )
            }

    private fun executionIsValid(
        description: CapabilityDescription,
        mode: ExecutionMode,
        request: TranslationRequest,
    ) =
        mode in description.executionModes &&
            (request.execution != ExecutionRequirement.OFFLINE_REQUIRED ||
                mode == ExecutionMode.OFFLINE)

    private fun mustReject(request: TranslationRequest, support: SupportReport) =
        support.hasUnsupported &&
            (request.policy.unsupportedHandling == UnsupportedHandling.REJECT ||
                (CapabilityFeature.TERMINOLOGY_HINTS in support.unsupportedFeatures &&
                    request.terminology.any { it.strength == TerminologyStrength.REQUIRED }))

    suspend operator fun invoke(request: TranslationRequest): TranslationOutcome {
        currentCoroutineContext().ensureActive()
        request.validationFailure()?.let {
            return TranslationOutcome.Failed(it, TranslationStage.VALIDATION)
        }
        if (request.sourceText.length > maxInputCharacters)
            return TranslationOutcome.Failed(
                CapabilityFailure(FailureCategory.INPUT_TOO_LONG),
                TranslationStage.VALIDATION,
            )
        // Check both capabilities before any text is passed to either one.
        executionFailure(translator.description, request.execution)?.let {
            return TranslationOutcome.Failed(it, TranslationStage.TRANSLATION)
        }
        executionFailure(verifier.description, request.execution)?.let {
            return TranslationOutcome.Failed(it, TranslationStage.VERIFICATION)
        }
        var support = support(request)
        if (mustReject(request, support))
            return TranslationOutcome.Failed(
                CapabilityFailure(FailureCategory.UNSUPPORTED_CAPABILITY),
                TranslationStage.VALIDATION,
                issues = supportIssues(support),
                support = support,
            )
        val result = translator.translate(request)
        currentCoroutineContext().ensureActive()
        if (result is CapabilityResult.Failure)
            return TranslationOutcome.Failed(
                result.failure,
                TranslationStage.TRANSLATION,
                issues = supportIssues(support) + result.issues,
                support = support,
            )
        result as CapabilityResult.Success<TranslatedText>
        support = support.merged(result.support)
        val issues = supportIssues(support) + result.issues
        val candidate = result.value.unverified()
        if (!executionIsValid(translator.description, result.executionMode, request))
            return TranslationOutcome.Failed(
                CapabilityFailure(
                    FailureCategory.OFFLINE_REQUIREMENT_NOT_MET,
                    "Translator returned an undeclared or forbidden execution mode",
                ),
                TranslationStage.TRANSLATION,
                candidate,
                issues,
                support,
            )
        if (
            candidate.text.isBlank() ||
                candidate.completion != CompletionStatus.COMPLETE ||
                candidate.directionUsed != request.direction
        )
            return TranslationOutcome.Failed(
                CapabilityFailure(
                    FailureCategory.INFERENCE_FAILED,
                    "Incomplete or inconsistent translation result",
                ),
                TranslationStage.TRANSLATION,
                candidate,
                issues,
                support,
            )
        if (mustReject(request, support))
            return TranslationOutcome.Failed(
                CapabilityFailure(FailureCategory.UNSUPPORTED_CAPABILITY),
                TranslationStage.TRANSLATION,
                candidate,
                issues,
                support,
            )
        val requiredTerms =
            request.terminology
                .filter { it.strength == TerminologyStrength.REQUIRED }
                .map { CriticalExpectation.Terminology(it) }
                .distinct()
                .filterNot { it in request.criticalContent }
        val expectations = request.criticalContent + requiredTerms
        val verified =
            verifier.verify(
                VerificationRequest(
                    request.sourceText,
                    candidate.text,
                    candidate.directionUsed,
                    expectations,
                    request.context,
                    request.execution,
                )
            )
        currentCoroutineContext().ensureActive()
        if (verified is CapabilityResult.Failure)
            return TranslationOutcome.Failed(
                verified.failure,
                TranslationStage.VERIFICATION,
                candidate,
                issues + verified.issues,
                support,
            )
        verified as CapabilityResult.Success<VerificationReport>
        support = support.merged(verified.support)
        val enrichedIssues =
            supportIssues(support) + result.issues + verified.issues + verified.value.findings
        if (!executionIsValid(verifier.description, verified.executionMode, request))
            return TranslationOutcome.Failed(
                CapabilityFailure(
                    FailureCategory.OFFLINE_REQUIREMENT_NOT_MET,
                    "Verifier returned an undeclared or forbidden execution mode",
                ),
                TranslationStage.VERIFICATION,
                candidate,
                enrichedIssues,
                support,
            )
        fun covered(expectation: CriticalExpectation): Boolean =
            VerificationCheck.SEMANTIC_CONTENT in verified.value.checksPerformed ||
                (VerificationCheck.EXACT_INTEGER_LITERALS in verified.value.checksPerformed &&
                    expectation is CriticalExpectation.NumberLiteral &&
                    expectation.value.isInteger)
        val unchecked =
            verified.value.uncheckedKinds +
                expectations.filterNot { covered(it) }.map { it.kind }.toSet()
        val report =
            if (
                unchecked.isNotEmpty() &&
                    verified.value.status in
                        setOf(VerificationStatus.PASS, VerificationStatus.PASS_WITH_WARNINGS)
            )
                VerificationReport(
                    VerificationStatus.NEEDS_REVIEW,
                    verified.value.findings +
                        CapabilityIssue(
                            IssueCode.VERIFICATION_INCOMPLETE,
                            IssueSeverity.CAUTION,
                            IssueCertainty.OBSERVED,
                            "Requested critical content is outside the verifier's performed checks",
                        ),
                    verified.value.checksPerformed,
                    unchecked,
                )
            else verified.value
        val allIssues = enrichedIssues + report.findings.filterNot { it in verified.value.findings }
        val enriched = candidate.verified(report)
        if (mustReject(request, support))
            return TranslationOutcome.Failed(
                CapabilityFailure(FailureCategory.UNSUPPORTED_CAPABILITY),
                TranslationStage.VERIFICATION,
                enriched,
                enrichedIssues,
                support,
            )
        val actualExecution =
            if (
                result.executionMode == ExecutionMode.ONLINE ||
                    verified.executionMode == ExecutionMode.ONLINE
            )
                ExecutionMode.ONLINE
            else ExecutionMode.OFFLINE
        val completed = CapabilityResult.Success(enriched, actualExecution, allIssues, support)
        return when (report.status) {
            VerificationStatus.PASS,
            VerificationStatus.PASS_WITH_WARNINGS ->
                if (allIssues.any { it.severity == IssueSeverity.CRITICAL })
                    TranslationOutcome.NeedsReview(completed)
                else TranslationOutcome.Complete(completed)
            VerificationStatus.NEEDS_REVIEW -> TranslationOutcome.NeedsReview(completed)
            VerificationStatus.FAIL ->
                TranslationOutcome.Failed(
                    CapabilityFailure(FailureCategory.VERIFICATION_FAILED),
                    TranslationStage.VERIFICATION,
                    enriched,
                    allIssues,
                    support,
                )
        }
    }
}
