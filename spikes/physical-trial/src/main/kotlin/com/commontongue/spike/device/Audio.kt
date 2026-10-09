package com.commontongue.spike.device

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

internal class Capture {
    private val recording = AtomicBoolean(false)
    private val bytes = ByteArrayOutputStream()
    private var recorder: AudioRecord? = null
    private var thread: Thread? = null
    private var failure: String? = null

    @SuppressLint("MissingPermission") // Activity checks RECORD_AUDIO before construction/start.
    fun start() {
        val minimum =
            AudioRecord.getMinBufferSize(
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        check(minimum > 0) { "16 kHz mono microphone capture unavailable" }
        val audio =
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimum * 2, 8192),
            )
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            audio.release()
            error("Microphone initialization failed")
        }
        recorder = audio
        bytes.reset()
        failure = null
        recording.set(true)
        audio.startRecording()
        thread =
            Thread(
                    {
                        try {
                            val buffer = ByteArray(4096)
                            while (recording.get()) {
                                val count = audio.read(buffer, 0, buffer.size)
                                if (count > 0) bytes.write(buffer, 0, count)
                                else if (recording.get()) error("AudioRecord read failed: $count")
                                if (bytes.size() >= 16000 * 2 * 30) {
                                    failure =
                                        "30-second research capture limit reached; release and retry shorter"
                                    recording.set(false)
                                }
                            }
                        } catch (error: Exception) {
                            failure = error.message ?: "Microphone failure"
                        }
                    },
                    "trial-capture",
                )
                .also { it.start() }
    }

    fun stop(output: File?): Double {
        recording.set(false)
        try {
            recorder?.stop()
        } catch (_: IllegalStateException) {}
        thread?.join(2000)
        check(thread?.isAlive != true) { "Microphone thread did not stop" }
        recorder?.release()
        recorder = null
        thread = null
        val pcm = bytes.toByteArray()
        bytes.reset()
        if (output != null) {
            check(failure == null) { failure ?: "Capture failed" }
            check(pcm.size >= 3200) { "Speech capture too short" }
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header
                .put("RIFF".toByteArray())
                .putInt(36 + pcm.size)
                .put("WAVEfmt ".toByteArray())
                .putInt(16)
                .putShort(1)
                .putShort(1)
                .putInt(16000)
                .putInt(32000)
                .putShort(2)
                .putShort(16)
                .put("data".toByteArray())
                .putInt(pcm.size)
            output.outputStream().use {
                it.write(header.array())
                it.write(pcm)
            }
        }
        return pcm.size / 32000.0
    }
}
