package com.commontongue.local

import com.commontongue.translation.FailureCategory

enum class LocalStage {
    RESOURCE_VALIDATE_START,
    RESOURCE_VALIDATE_COMPLETE,
    MODEL_LOAD_START,
    MODEL_LOAD_COMPLETE,
    ASR_START,
    ASR_COMPLETE,
    TRANSLATION_START,
    TRANSLATION_COMPLETE,
    TOKENIZE_START,
    TOKENIZE_COMPLETE,
    ENCODER_START,
    ENCODER_COMPLETE,
    DECODER_START,
    FIRST_TOKEN,
    CANCELLED,
    RELEASED,
    FAILED,
}

enum class LocalFailure(val category: FailureCategory, val explanation: String) {
    MISSING_RESOURCES(
        FailureCategory.MODEL_NOT_INSTALLED,
        "Set up the offline test resources first.",
    ),
    WRONG_RESOURCES(
        FailureCategory.MODEL_NOT_INSTALLED,
        "The offline resources are incomplete or damaged.",
    ),
    RUNTIME_UNAVAILABLE(
        FailureCategory.ENGINE_NOT_AVAILABLE,
        "The local engine is unavailable on this device.",
    ),
    OFFLINE_UNAVAILABLE(
        FailureCategory.OFFLINE_REQUIREMENT_NOT_MET,
        "A verified local engine is required.",
    ),
    CLOSED(FailureCategory.ENGINE_NOT_AVAILABLE, "The local inference session has ended."),
    BACKGROUND(FailureCategory.CANCELLED, "Return to Common Tongue to continue."),
    LANGUAGE_UNSUPPORTED(
        FailureCategory.LANGUAGE_NOT_SUPPORTED,
        "This build supports English and Spanish.",
    ),
    UNSUPPORTED(
        FailureCategory.UNSUPPORTED_CAPABILITY,
        "This requested option is not supported by the local engine.",
    ),
    INPUT_INVALID(FailureCategory.INPUT_INVALID, "The input is empty or unavailable."),
    INPUT_TOO_LONG(FailureCategory.INPUT_TOO_LONG, "The input exceeds the local engine's limit."),
    LOAD_FAILED(
        FailureCategory.ENGINE_NOT_AVAILABLE,
        "The local models could not load. Try again.",
    ),
    NATIVE_FAILED(
        FailureCategory.INFERENCE_FAILED,
        "The local engine could not complete this request.",
    ),
    WORKER_EXIT(FailureCategory.INFERENCE_FAILED, "The local engine stopped. Try again."),
    RESOURCE_LIMIT(FailureCategory.RESOURCE_LIMIT, "Not enough device resources for this request."),
    INVALID_RESULT(
        FailureCategory.INFERENCE_FAILED,
        "The local engine returned an invalid result.",
    ),
}

class LocalFault(val reason: LocalFailure) : Exception(reason.name)

/** Finite enums/numbers only. No audio, text, paths, addresses, request IDs or device IDs. */
data class LocalDiagnostic(
    val stage: LocalStage,
    val milliseconds: Double? = null,
    val failure: LocalFailure? = null,
    val workerPssBytes: Long? = null,
    val workerRssBytes: Long? = null,
    val exitReason: Int? = null,
    val exitStatus: Int? = null,
)

fun safeLocalDiagnostic(sink: (LocalDiagnostic) -> Unit, event: LocalDiagnostic) {
    try {
        sink(event)
    } catch (_: Throwable) {
        /* Diagnostics cannot interrupt inference. */
    }
}
