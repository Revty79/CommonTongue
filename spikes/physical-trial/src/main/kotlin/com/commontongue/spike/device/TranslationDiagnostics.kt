package com.commontongue.spike.device

/** Never coerce a floating point value (or an address) into a model handle. */
internal object OpaqueT5Handle {
    fun parse(value: Any?): Long {
        require(value is Int || value is Long) { "Translation handle must be an opaque integer ID" }
        return (value as Number).toLong().also {
            require(it in 1..0xffffffffL) { "Invalid translation model ID" }
        }
    }
}

/** Invoked synchronously on the native call's thread, before/after each boundary. */
internal fun interface TranslationProgress {
    fun onStage(code: Int)
}

internal object TranslationBoundary {
    fun fromCode(code: Int): PipelineStage =
        when (code) {
            1 -> PipelineStage.TOKENIZE_START
            2 -> PipelineStage.TOKENIZE_COMPLETE
            3 -> PipelineStage.ENCODER_START
            4 -> PipelineStage.ENCODER_COMPLETE
            5 -> PipelineStage.DECODER_START
            6 -> PipelineStage.FIRST_TOKEN
            else -> error("Unknown translation boundary")
        }
}
