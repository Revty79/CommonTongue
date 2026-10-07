package com.commontongue.spike.offline

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.LongBuffer
import org.json.JSONObject

internal class Marian(directory: File) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment().apply { setTelemetry(false) }
    private val config = JSONObject(File(directory, "config.json").readText())
    private val vocab = JSONObject(File(directory, "vocab.json").readText())
    private val inverse = vocab.keys().asSequence().associate { vocab.getInt(it) to it }
    private val source = Native.tokenizerCreate(File(directory, "source.spm").path)
    private val target = Native.tokenizerCreate(File(directory, "target.spm").path)
    private val options =
        OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setInterOpNumThreads(1)
        }
    private val encoder =
        environment.createSession(
            File(directory, "onnx/encoder_model_quantized.onnx").path,
            options,
        )
    private val decoder =
        environment.createSession(
            File(directory, "onnx/decoder_model_quantized.onnx").path,
            options,
        )

    private fun tensor(ids: LongArray) =
        OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(ids),
            longArrayOf(1, ids.size.toLong()),
        )

    fun translate(text: String): String {
        val pieces = Native.encode(source, text)
        check(pieces.size <= 256) { "Input exceeds research decoding limit" }
        val eos = config.getInt("eos_token_id")
        val pad = config.getInt("pad_token_id")
        val ids = pieces.map { vocab.optInt(it, vocab.getInt("<unk>")).toLong() } + eos.toLong()
        val generated = mutableListOf(config.getInt("decoder_start_token_id").toLong())
        tensor(ids.toLongArray()).use { input ->
            tensor(LongArray(ids.size) { 1 }).use { mask ->
                encoder.run(mapOf("input_ids" to input, "attention_mask" to mask)).use { encoded ->
                    val hidden = encoded[0] as OnnxTensor
                    repeat(96) {
                        tensor(generated.toLongArray()).use { tokens ->
                            decoder
                                .run(
                                    mapOf(
                                        "input_ids" to tokens,
                                        "encoder_attention_mask" to mask,
                                        "encoder_hidden_states" to hidden,
                                    )
                                )
                                .use { decoded ->
                                    @Suppress("UNCHECKED_CAST")
                                    val logits =
                                        (decoded[0].value as Array<Array<FloatArray>>)[0].last()
                                    var best = 0
                                    var score = Float.NEGATIVE_INFINITY
                                    logits.forEachIndexed { index, value ->
                                        if (index != pad && value > score) {
                                            best = index
                                            score = value
                                        }
                                    }
                                    if (best == eos)
                                        return Native.decode(
                                            target,
                                            generated
                                                .drop(1)
                                                .map { inverse.getValue(it.toInt()) }
                                                .toTypedArray(),
                                        )
                                    generated += best.toLong()
                                }
                        }
                    }
                }
            }
        }
        error("Translation exceeded 96 tokens; partial output is not success")
    }

    override fun close() {
        decoder.close()
        encoder.close()
        options.close()
        Native.tokenizerClose(source)
        Native.tokenizerClose(target)
    }
}
