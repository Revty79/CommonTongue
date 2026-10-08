package com.commontongue.translation

import com.commontongue.domain.LanguageId
import com.commontongue.domain.TranslationDirection

internal val direction = TranslationDirection(LanguageId.parse("en"), LanguageId.parse("es"))
internal val fullDescription =
    CapabilityDescription(
        setOf(ExecutionMode.OFFLINE),
        CapabilityFeature.entries.toSet(),
        TranslationRequirement.entries.toSet(),
    )

internal fun passing() =
    VerificationReport(
        VerificationStatus.PASS,
        checksPerformed = setOf(VerificationCheck.SEMANTIC_CONTENT),
    )

internal fun caution() =
    CapabilityIssue(
        IssueCode.TERMINOLOGY_UNCERTAINTY,
        IssueSeverity.CAUTION,
        IssueCertainty.POSSIBLE,
    )

internal class FakeTranslator(
    override val description: CapabilityDescription = fullDescription,
    val answer: suspend (TranslationRequest) -> TranslationResult = {
        CapabilityResult.Success(
            TranslatedText("Salida de prueba", it.direction),
            ExecutionMode.OFFLINE,
        )
    },
) : Translator {
    var calls = 0
    var received: TranslationRequest? = null

    override suspend fun translate(request: TranslationRequest): TranslationResult {
        executionFailure(description, request.execution)?.let {
            return CapabilityResult.Failure(it)
        }
        calls++
        received = request
        return answer(request)
    }
}

internal class FakeVerifier(
    override val description: CapabilityDescription = fullDescription,
    val answer: suspend (VerificationRequest) -> CapabilityResult<VerificationReport> = {
        CapabilityResult.Success(passing(), ExecutionMode.OFFLINE)
    },
) : TranslationVerifier {
    var calls = 0
    var received: VerificationRequest? = null

    override suspend fun verify(
        request: VerificationRequest
    ): CapabilityResult<VerificationReport> {
        executionFailure(description, request.execution)?.let {
            return CapabilityResult.Failure(it)
        }
        calls++
        received = request
        return answer(request)
    }
}
