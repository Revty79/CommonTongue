package com.commontongue.translation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OfflineExecutionTest {
    private val onlineOnly = CapabilityDescription(setOf(ExecutionMode.ONLINE))

    @Test
    fun offlineRequirementRejectsOnlineOnlyTranslatorBeforeInvocation() = runTest {
        val translator = FakeTranslator(onlineOnly)
        val verifier = FakeVerifier()
        val result =
            TranslateTextUseCase(translator, verifier)(TranslationRequest("source", direction))
                as TranslationOutcome.Failed
        assertEquals(FailureCategory.OFFLINE_REQUIREMENT_NOT_MET, result.failure.category)
        assertEquals(0, translator.calls)
        assertEquals(0, verifier.calls)
    }

    @Test
    fun onlineOnlyVerifierAlsoBlocksTranslationBeforeAnyInvocation() = runTest {
        val translator = FakeTranslator()
        val verifier = FakeVerifier(onlineOnly)
        val result =
            TranslateTextUseCase(translator, verifier)(TranslationRequest("source", direction))
                as TranslationOutcome.Failed
        assertEquals(FailureCategory.OFFLINE_REQUIREMENT_NOT_MET, result.failure.category)
        assertEquals(TranslationStage.VERIFICATION, result.stage)
        assertEquals(0, translator.calls)
        assertEquals(0, verifier.calls)
    }

    @Test
    fun directCapabilityGuardAlsoRejectsOfflineRequired() = runTest {
        val translator = FakeTranslator(onlineOnly)
        val result =
            translator.translate(TranslationRequest("source", direction))
                as CapabilityResult.Failure
        assertEquals(FailureCategory.OFFLINE_REQUIREMENT_NOT_MET, result.failure.category)
        assertEquals(0, translator.calls)
    }

    @Test
    fun explicitOnlinePermissionAllowsOnlyTheSelectedCapabilities() = runTest {
        val translator =
            FakeTranslator(
                onlineOnly,
                answer = {
                    CapabilityResult.Success(
                        TranslatedText("output", direction),
                        ExecutionMode.ONLINE,
                    )
                },
            )
        val result =
            TranslateTextUseCase(translator, FakeVerifier())(
                TranslationRequest(
                    "source",
                    direction,
                    execution = ExecutionRequirement.ONLINE_ALLOWED,
                )
            )
                as TranslationOutcome.Complete
        assertEquals(ExecutionMode.ONLINE, result.result.executionMode)
        assertEquals(1, translator.calls)
    }

    @Test
    fun onlinePermissionDoesNotForceAnOfflineEngineOnline() = runTest {
        val result =
            TranslateTextUseCase(FakeTranslator(), FakeVerifier())(
                TranslationRequest(
                    "source",
                    direction,
                    execution = ExecutionRequirement.ONLINE_ALLOWED,
                )
            )
                as TranslationOutcome.Complete
        assertEquals(ExecutionMode.OFFLINE, result.result.executionMode)
    }

    @Test
    fun onlineVerifierMakesOverallExecutionOnlineEvenWithOfflineTranslation() = runTest {
        val verifier =
            FakeVerifier(
                onlineOnly,
                answer = { CapabilityResult.Success(passing(), ExecutionMode.ONLINE) },
            )
        val result =
            TranslateTextUseCase(FakeTranslator(), verifier)(
                TranslationRequest(
                    "source",
                    direction,
                    execution = ExecutionRequirement.ONLINE_ALLOWED,
                )
            )
                as TranslationOutcome.Complete
        assertEquals(ExecutionMode.ONLINE, result.result.executionMode)
    }

    @Test
    fun forbiddenTranslatorExecutionMetadataCannotBeAccepted() = runTest {
        val translator =
            FakeTranslator(
                answer = {
                    CapabilityResult.Success(
                        TranslatedText("output", direction),
                        ExecutionMode.ONLINE,
                    )
                }
            )
        val verifier = FakeVerifier()
        val result =
            TranslateTextUseCase(translator, verifier)(TranslationRequest("source", direction))
                as TranslationOutcome.Failed
        assertEquals(FailureCategory.OFFLINE_REQUIREMENT_NOT_MET, result.failure.category)
        assertEquals(0, verifier.calls)
    }

    @Test
    fun forbiddenVerifierExecutionMetadataCannotBeAccepted() = runTest {
        val verifier =
            FakeVerifier(answer = { CapabilityResult.Success(passing(), ExecutionMode.ONLINE) })
        val result =
            TranslateTextUseCase(FakeTranslator(), verifier)(
                TranslationRequest("source", direction)
            )
                as TranslationOutcome.Failed
        assertEquals(FailureCategory.OFFLINE_REQUIREMENT_NOT_MET, result.failure.category)
        assertEquals(TranslationStage.VERIFICATION, result.stage)
    }

    @Test
    fun supportingBothModesDoesNotPermitSilentFallback() = runTest {
        val both =
            CapabilityDescription(
                setOf(ExecutionMode.OFFLINE, ExecutionMode.ONLINE),
                policies = TranslationRequirement.entries.toSet(),
            )
        val translator =
            FakeTranslator(
                both,
                answer = {
                    CapabilityResult.Success(
                        TranslatedText("output", direction),
                        ExecutionMode.ONLINE,
                    )
                },
            )
        val result =
            TranslateTextUseCase(translator, FakeVerifier())(
                TranslationRequest("source", direction)
            )
                as TranslationOutcome.Failed
        assertEquals(FailureCategory.OFFLINE_REQUIREMENT_NOT_MET, result.failure.category)
    }
}
