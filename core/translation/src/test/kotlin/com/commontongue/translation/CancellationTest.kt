package com.commontongue.translation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CancellationTest {
    @Test
    fun cancellingLongTranslatorWorkCleansUpAndSkipsVerification() = runTest {
        val entered = CompletableDeferred<Unit>()
        var cleaned = false
        val translator =
            FakeTranslator(
                answer = {
                    entered.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        cleaned = true
                    }
                }
            )
        val verifier = FakeVerifier()
        var outcome: TranslationOutcome? = null
        val job = launch {
            outcome =
                TranslateTextUseCase(translator, verifier)(TranslationRequest("source", direction))
        }
        entered.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(cleaned)
        assertNull(outcome)
        assertEquals(0, verifier.calls)
    }

    @Test
    fun cancellationAlsoPropagatesDuringVerification() = runTest {
        val entered = CompletableDeferred<Unit>()
        var cleaned = false
        val verifier =
            FakeVerifier(
                answer = {
                    entered.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        cleaned = true
                    }
                }
            )
        var outcome: TranslationOutcome? = null
        val job = launch {
            outcome =
                TranslateTextUseCase(FakeTranslator(), verifier)(
                    TranslationRequest("source", direction)
                )
        }
        entered.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(cleaned)
        assertNull(outcome)
    }

    @Test
    fun aTranslatorThatSwallowsCancellationCannotStartVerification() = runTest {
        val entered = CompletableDeferred<Unit>()
        val translator =
            FakeTranslator(
                answer = {
                    entered.complete(Unit)
                    try {
                        awaitCancellation()
                    } catch (_: CancellationException) {
                        CapabilityResult.Success(
                            TranslatedText("late output", direction),
                            ExecutionMode.OFFLINE,
                        )
                    }
                }
            )
        val verifier = FakeVerifier()
        var outcome: TranslationOutcome? = null
        val job = launch {
            outcome =
                TranslateTextUseCase(translator, verifier)(TranslationRequest("source", direction))
        }
        entered.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(0, verifier.calls)
        assertNull(outcome)
    }

    @Test
    fun cancellationIsNotConvertedToACancelledFailureValue() = runTest {
        val translator =
            FakeTranslator(answer = { throw CancellationException("fixture cancellation") })
        try {
            TranslateTextUseCase(translator, FakeVerifier())(
                TranslationRequest("source", direction)
            )
            fail("Cancellation must propagate")
        } catch (cancelled: CancellationException) {
            assertEquals("fixture cancellation", cancelled.message)
        }
    }
}
