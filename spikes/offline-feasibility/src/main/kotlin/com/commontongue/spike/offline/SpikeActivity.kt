package com.commontongue.spike.offline

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

class SpikeActivity : Activity() {
    private lateinit var screen: TextView
    private lateinit var tts: TextToSpeech
    private val engine = "com.reecedunn.espeak"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen =
            TextView(this).apply {
                text = "Offline feasibility research. Waiting for local TTS..."
                textSize = 16f
                setPadding(24, 48, 24, 48)
            }
        setContentView(ScrollView(this).apply { addView(screen) })
        tts =
            TextToSpeech(
                this,
                { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        Thread({ runProof() }, "offline-proof").start()
                    } else {
                        reportFailure(
                            IllegalStateException(
                                "Local eSpeak engine initialization failed: $status"
                            )
                        )
                    }
                },
                engine,
            )
    }

    private fun memory(): JSONObject {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        val status =
            File("/proc/self/status").readLines().filter {
                it.startsWith("VmRSS:") || it.startsWith("VmHWM:")
            }
        return JSONObject()
            .put("total_pss_kb", info.totalPss)
            .put("native_heap_bytes", Debug.getNativeHeapAllocatedSize())
            .put("proc_status", JSONArray(status))
            .put("process_cpu_ms", Process.getElapsedCpuTime())
    }

    private fun pcm(file: File): FloatArray {
        val buffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        check(String(buffer.array(), 0, 4, Charsets.US_ASCII) == "RIFF")
        check(String(buffer.array(), 8, 4, Charsets.US_ASCII) == "WAVE")
        buffer.position(12)
        var validFormat = false
        while (buffer.remaining() >= 8) {
            val name = ByteArray(4).also { buffer.get(it) }.toString(Charsets.US_ASCII)
            val size = buffer.int
            check(size >= 0 && size <= buffer.remaining())
            val end = buffer.position() + size
            if (name == "fmt ") {
                check(size >= 16)
                val format = buffer.short.toInt()
                val channels = buffer.short.toInt()
                val rate = buffer.int
                buffer.int
                buffer.short
                val bits = buffer.short.toInt()
                check(format == 1 && channels == 1 && rate == 16000 && bits == 16) {
                    "Only mono 16 kHz PCM16 WAV is accepted"
                }
                validFormat = true
            } else if (name == "data") {
                check(validFormat && size % 2 == 0)
                return FloatArray(size / 2) { buffer.short / 32768f }
            }
            buffer.position(end + size % 2)
        }
        error("WAV data chunk missing")
    }

    private fun speech(text: String, language: String, output: File): JSONObject {
        val voice =
            tts.voices
                ?.filter { it.locale.language == language && !it.isNetworkConnectionRequired }
                ?.sortedWith(
                    compareBy<android.speech.tts.Voice> {
                            if (it.locale.country == (if (language == "en") "US" else "ES")) 0
                            else 1
                        }
                        .thenBy { it.name }
                )
                ?.firstOrNull() ?: error("No explicitly offline voice for $language")
        check(tts.setVoice(voice) == TextToSpeech.SUCCESS)
        check(!tts.voice.isNetworkConnectionRequired)
        val complete = CountDownLatch(1)
        var failure: Int? = null
        val started = SystemClock.elapsedRealtimeNanos()
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    complete.countDown()
                }

                @Deprecated("Required abstract compatibility callback")
                override fun onError(utteranceId: String?) {
                    failure = -1
                    complete.countDown()
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    failure = errorCode
                    complete.countDown()
                }
            }
        )
        check(tts.synthesizeToFile(text, Bundle(), output, output.name) == TextToSpeech.SUCCESS)
        check(complete.await(60, TimeUnit.SECONDS)) { "TTS timed out" }
        check(failure == null) { "TTS failed: $failure" }
        check(output.length() > 44) { "TTS produced no audio" }
        return JSONObject()
            .put("engine", engine)
            .put("voice", voice.name)
            .put("locale", voice.locale.toLanguageTag())
            .put("requires_network", voice.isNetworkConnectionRequired)
            .put("ms", (SystemClock.elapsedRealtimeNanos() - started) / 1e6)
            .put("wav_bytes", output.length())
            .put("file", output.name)
    }

    private fun runProof() {
        try {
            val root = filesDir
            val asrModel = intent.getStringExtra("asr") ?: "tiny"
            check(asrModel in listOf("tiny", "base"))
            val result =
                JSONObject()
                    .put("status", "RUNNING")
                    .put("sdk", Build.VERSION.SDK_INT)
                    .put("device", Build.MODEL)
                    .put("abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
                    .put("asr_model", "Whisper $asrModel Q5_1")
                    .put("threads", 4)
                    .put("native_build", Native.buildInfo())
                    .put("start_memory", memory())
                    .put("engine", engine)
            val started = SystemClock.elapsedRealtimeNanos()
            val weights = File(root, "models/whisper/ggml-$asrModel-q5_1.bin")
            check(weights.isFile && weights.canRead()) {
                "Cannot read cached model: ${weights.path}"
            }
            Log.i("OfflineProof", "Model file: ${weights.path}; bytes=${weights.length()}")
            val context = Native.asrCreate(weights.path)
            val load = (SystemClock.elapsedRealtimeNanos() - started) / 1e6
            result.put("asr_load_ms", load).put("asr_loaded_memory", memory())
            val cases = JSONArray()
            try {
                for (direction in listOf("en-es", "es-en")) {
                    val audio = File(root, "audio/${direction.take(2)}01-clean.wav")
                    val sourcePcm = pcm(audio)
                    val begin = SystemClock.elapsedRealtimeNanos()
                    val cpuStart = Process.getElapsedCpuTime()
                    val transcript = Native.asrRun(context, sourcePcm, direction.take(2)).trim()
                    val asrMs = (SystemClock.elapsedRealtimeNanos() - begin) / 1e6
                    check(transcript.isNotBlank())
                    val translationLoadBegin = SystemClock.elapsedRealtimeNanos()
                    val translator = Marian(File(root, "models/$direction"))
                    val translationLoad =
                        (SystemClock.elapsedRealtimeNanos() - translationLoadBegin) / 1e6
                    val loadedMemory = memory()
                    val translationBegin = SystemClock.elapsedRealtimeNanos()
                    val translated = translator.use { it.translate(transcript) }
                    val translationMs =
                        (SystemClock.elapsedRealtimeNanos() - translationBegin) / 1e6
                    check(translated.isNotBlank())
                    val speech =
                        speech(
                            translated,
                            direction.takeLast(2),
                            File(root, "$direction-$asrModel-speech.wav"),
                        )
                    val case =
                        JSONObject()
                            .put("direction", direction)
                            .put("transcript", transcript)
                            .put("translation", translated)
                            .put("asr_ms", asrMs)
                            .put("translation_load_ms", translationLoad)
                            .put("translation_ms", translationMs)
                            .put("translation_loaded_memory", loadedMemory)
                            .put("tts", speech)
                            .put(
                                "total_ms_including_translator_load",
                                (SystemClock.elapsedRealtimeNanos() - begin) / 1e6,
                            )
                            .put("process_cpu_ms", Process.getElapsedCpuTime() - cpuStart)
                            .put("end_memory", memory())
                    cases.put(case)
                    Log.i("OfflineProof", "CASE_RESULT:$case")
                    runOnUiThread { screen.text = cases.toString(2) }
                }
            } finally {
                Native.asrClose(context)
            }
            result.put("cases", cases).put("status", "PASS").put("end_memory", memory())
            File(root, "results-$asrModel.json").writeText(result.toString(2))
            Log.i("OfflineProof", "PROOF_RESULT:$result")
            runOnUiThread { screen.text = result.toString(2) }
        } catch (failure: Throwable) {
            reportFailure(failure)
        }
    }

    private fun reportFailure(failure: Throwable) {
        Log.e("OfflineProof", "PROOF_FAILED", failure)
        runOnUiThread { screen.text = "FAILED: ${failure.message}" }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) tts.shutdown()
        super.onDestroy()
    }
}
