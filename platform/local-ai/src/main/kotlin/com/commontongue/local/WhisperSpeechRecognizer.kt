package com.commontongue.local

import com.commontongue.domain.LanguageId
import com.commontongue.translation.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class WhisperSpeechRecognizer(private val session: LocalAiSession) : SpeechRecognizer {
    override val description = CapabilityDescription(setOf(ExecutionMode.OFFLINE))

    override suspend fun recognize(
        request: RecognitionRequest
    ): CapabilityResult<RecognizedSpeech> {
        currentCoroutineContext().ensureActive()
        executionFailure(description, request.execution)?.let {
            return CapabilityResult.Failure(it)
        }
        val selected =
            (request.language as? LanguageSelection.Locked)?.language
                ?: return failure(LocalFailure.UNSUPPORTED)
        if (selected.baseLanguage !in setOf("en", "es"))
            return failure(LocalFailure.LANGUAGE_UNSUPPORTED)
        if (request.vocabulary.isNotEmpty()) return failure(LocalFailure.UNSUPPORTED)
        val actual = LanguageId.parse(selected.baseLanguage)
        return try {
            session.execute { runtime ->
                val result = runtime.recognize(request.audio, actual)
                if (result.languageUsed != actual || result.text.isBlank())
                    throw LocalFault(LocalFailure.INVALID_RESULT)
                CapabilityResult.Success(
                    RecognizedSpeech(
                        result.text.trim(),
                        result.languageUsed,
                        if (result.complete) CompletionStatus.COMPLETE
                        else CompletionStatus.PARTIAL,
                    ),
                    ExecutionMode.OFFLINE,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            failure((error as? LocalFault)?.reason ?: LocalFailure.NATIVE_FAILED)
        }
    }
}

internal fun failure(reason: LocalFailure) =
    CapabilityResult.Failure(CapabilityFailure(reason.category, reason.explanation))
