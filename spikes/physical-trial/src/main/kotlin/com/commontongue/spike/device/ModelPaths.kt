package com.commontongue.spike.device

import java.io.File

/** Shared by path diagnostics and the actual model loaders. */
internal class ModelPaths(val root: File) {
    val madlad: File
        get() = File(root, "models/madlad")

    fun whisper(size: String): File {
        require(size == "base" || size == "tiny")
        return File(root, "models/whisper/ggml-$size-q5_1.bin")
    }

    fun opus(direction: String): File {
        require(direction == "en-es" || direction == "es-en")
        return File(root, "models/$direction")
    }

    fun required(translator: String, asr: String): List<File> {
        require(translator == "madlad" || translator == "opus")
        val translation =
            if (translator == "madlad") {
                listOf("model-q4k.gguf", "config.json", "tokenizer.json").map { File(madlad, it) }
            } else {
                listOf("en-es", "es-en").flatMap { direction ->
                    listOf(
                            "config.json",
                            "vocab.json",
                            "source.spm",
                            "target.spm",
                            "onnx/encoder_model_quantized.onnx",
                            "onnx/decoder_model_quantized.onnx",
                        )
                        .map { File(opus(direction), it) }
                }
            }
        return translation + whisper(asr)
    }
}
