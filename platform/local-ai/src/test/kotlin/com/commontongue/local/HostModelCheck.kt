package com.commontongue.local

import com.commontongue.domain.LanguageId
import com.commontongue.domain.TranslationDirection
import com.commontongue.translation.*
import java.io.File
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** Explicit optional real-model task. CI/unit tests never download/load multi-GB resources. */
fun main(args: Array<String>) = runBlocking {
    val root = File(args.single()).canonicalFile
    val process =
        ProcessBuilder("python", File(root, "tools/local-ai/host-worker.py").path)
            .directory(root)
            .start()
    val reader = process.inputStream.bufferedReader()
    val writer = process.outputStream.bufferedWriter()
    suspend fun command(value: String): List<String> =
        withContext(Dispatchers.IO) {
            writer.write(value)
            writer.newLine()
            writer.flush()
            val response = reader.readLine() ?: error("HOST_WORKER_EXIT")
            check(response != "FAILED") { "HOST_NATIVE_FAILED" }
            response.split('\t')
        }
    fun decode(value: String) = String(Base64.getDecoder().decode(value), Charsets.UTF_8)
    val source = CoreResourceSource {
        val names =
            mapOf(
                CoreResourceRole.RECOGNIZER to ".local/models/whisper/ggml-base-q5_1.bin",
                CoreResourceRole.TRANSLATOR to
                    ".local/quality/models/google--madlad400-3b-mt/model-q4k.gguf",
                CoreResourceRole.TOKENIZER to
                    ".local/quality/models/google--madlad400-3b-mt/tokenizer.json",
                CoreResourceRole.CONFIGURATION to
                    ".local/quality/models/google--madlad400-3b-mt/config.json",
            )
        withContext(Dispatchers.IO) {
            names.forEach { (role, path) ->
                ResourceIntegrity.verify(File(root, path), LockedCore.identities.getValue(role))
            }
        }
        ValidatedCoreResources("controlled-host", LockedCore.VERSION, LockedCore.identities)
    }
    val runtime =
        object : LocalRuntime {
            override val isOffline = true

            override suspend fun load(resources: ValidatedCoreResources) {
                check(command("LOAD") == listOf("OK"))
            }

            override suspend fun recognize(
                audio: AudioReference,
                language: LanguageId,
            ): NativeRecognition {
                check(audio.token == "control_${language.baseLanguage}")
                val result = command("ASR\t${language.baseLanguage}")
                return NativeRecognition(decode(result[1]), language, result[2].toBooleanStrict())
            }

            override suspend fun translate(
                text: String,
                direction: TranslationDirection,
            ): NativeTranslation {
                val encoded = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
                val result =
                    command(
                        "MT\t${direction.source.baseLanguage}-${direction.target.baseLanguage}\t$encoded"
                    )
                return NativeTranslation(decode(result[1]), direction, result[2].toBooleanStrict())
            }

            override suspend fun release() {
                check(command("CLOSE") == listOf("OK"))
            }
        }
    val session = LocalAiSession(source, runtime)
    val recognizer: SpeechRecognizer = WhisperSpeechRecognizer(session)
    val translator: Translator = MadladTranslator(session)
    val results = mutableListOf<String>()
    try {
        for (i in 0..1) {
            val control = command("CONTROL\t$i").map(::decode)
            val language = LanguageId.parse(control[0])
            val direction =
                TranslationDirection(
                    language,
                    LanguageId.parse(if (language.baseLanguage == "en") "es" else "en"),
                )
            val startAsr = System.nanoTime()
            val recognized =
                recognizer.recognize(
                    RecognitionRequest(
                        AudioReference.of("control_${language.baseLanguage}"),
                        LanguageSelection.Locked(language),
                    )
                ) as CapabilityResult.Success
            check(
                recognized.value.text == control[3] &&
                    recognized.value.languageUsed == language &&
                    recognized.value.completion == CompletionStatus.COMPLETE
            )
            results.add(
                "{\"check\":\"ASR_${control[0].uppercase()}\",\"status\":\"PASS\",\"milliseconds\":${(System.nanoTime() - startAsr) / 1_000_000}}"
            )
            val startMt = System.nanoTime()
            val translated =
                translator.translate(TranslationRequest(control[1], direction))
                    as CapabilityResult.Success
            check(
                translated.value.text == control[2] &&
                    translated.value.completion == CompletionStatus.COMPLETE
            ) {
                "MATERIAL_TRANSLATION_DIVERGENCE_STOP_ADOPTION"
            }
            results.add(
                "{\"check\":\"MT_${control[0].uppercase()}\",\"status\":\"PASS\",\"milliseconds\":${(System.nanoTime() - startMt) / 1_000_000}}"
            )
        }
        val output = File(root, ".local/local-ai/host-adapter-results.json")
        output.writeText(
            "{\"scope\":\"PRODUCTION_JVM_ADAPTERS_WITH_WINDOWS_NATIVE_CONTROL_NOT_ANDROID_NOT_PHYSICAL\",\"network_tripwire\":true,\"checks\":[${results.joinToString()}]}\n"
        )
        println(
            "Real-model production JVM adapter controls: 4 PASS; Android physical validation remains separate."
        )
    } finally {
        session.close()
        writer.close()
        process.waitFor()
        reader.close()
    }
}
