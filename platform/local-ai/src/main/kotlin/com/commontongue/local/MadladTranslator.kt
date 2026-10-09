package com.commontongue.local

import com.commontongue.translation.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class MadladTranslator(private val session: LocalAiSession) : Translator {
    override val description = CapabilityDescription(setOf(ExecutionMode.OFFLINE))

    override suspend fun translate(request: TranslationRequest): TranslationResult {
        currentCoroutineContext().ensureActive()
        request.validationFailure()?.let {
            return CapabilityResult.Failure(it)
        }
        executionFailure(description, request.execution)?.let {
            return CapabilityResult.Failure(it)
        }
        val pair = request.direction.source.baseLanguage to request.direction.target.baseLanguage
        if (pair !in setOf("en" to "es", "es" to "en"))
            return failure(LocalFailure.LANGUAGE_UNSUPPORTED)
        if (request.sourceText.length > 16000) return failure(LocalFailure.INPUT_TOO_LONG)
        val unsupported = buildSet {
            if (request.context.turns.isNotEmpty()) add(CapabilityFeature.CONVERSATION_CONTEXT)
            if (request.terminology.isNotEmpty()) add(CapabilityFeature.TERMINOLOGY_HINTS)
            if (request.domain != DomainContext.GENERAL) add(CapabilityFeature.DOMAIN_CONTEXT)
            if (request.regionalPreference != null) add(CapabilityFeature.REGIONAL_PREFERENCES)
            if (request.criticalContent.isNotEmpty()) add(CapabilityFeature.VERIFICATION)
        }
        val support = SupportReport(unsupported, request.policy.requirements - description.policies)
        val issues =
            unsupported.map {
                CapabilityIssue(
                    IssueCode.UNSUPPORTED_CAPABILITY,
                    IssueSeverity.CAUTION,
                    IssueCertainty.OBSERVED,
                    "Requested local translation option is unsupported: ${it.name}",
                )
            } +
                support.unsupportedPolicies.map {
                    CapabilityIssue(
                        IssueCode.UNSUPPORTED_POLICY,
                        IssueSeverity.CAUTION,
                        IssueCertainty.OBSERVED,
                        "Requested preservation policy is not verified: ${it.name}",
                    )
                }
        if (
            (support.hasUnsupported &&
                request.policy.unsupportedHandling == UnsupportedHandling.REJECT) ||
                request.terminology.any { it.strength == TerminologyStrength.REQUIRED }
        )
            return CapabilityResult.Failure(
                CapabilityFailure(
                    LocalFailure.UNSUPPORTED.category,
                    LocalFailure.UNSUPPORTED.explanation,
                ),
                issues,
            )
        return try {
            session.execute { runtime ->
                val result = runtime.translate(request.sourceText, request.direction)
                if (result.directionUsed != request.direction || result.text.isBlank())
                    throw LocalFault(LocalFailure.INVALID_RESULT)
                CapabilityResult.Success(
                    TranslatedText(
                        result.text,
                        result.directionUsed,
                        if (result.complete) CompletionStatus.COMPLETE
                        else CompletionStatus.PARTIAL,
                    ),
                    ExecutionMode.OFFLINE,
                    issues,
                    support,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            failure((error as? LocalFault)?.reason ?: LocalFailure.NATIVE_FAILED)
        }
    }
}
