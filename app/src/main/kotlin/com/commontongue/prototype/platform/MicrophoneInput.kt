package com.commontongue.prototype.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.commontongue.local.android.LocalAudioSource
import com.commontongue.translation.AudioReference
import com.commontongue.translation.CaptureFailure
import com.commontongue.translation.ConversationInput
import com.commontongue.translation.TurnProblem
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Small backend seam allows deterministic press/release/cancellation testing without Android. */
internal interface PcmCapture {
    fun start()

    fun read(buffer: ByteArray): Int

    fun close()
}

/** Private, session-scoped recording store; paths never cross into application/UI contracts. */
internal class MicrophoneInput(
    private val directory: File,
    private val open: () -> PcmCapture,
    private val permitted: () -> Boolean,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val ui: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ConversationInput, LocalAudioSource {
    constructor(
        context: Context
    ) : this(
        File(context.cacheDir, "microphone-turns"),
        { AndroidPcmCapture(context.applicationContext) },
        {
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        },
    )

    init {
        directory.mkdirs()
        // This dedicated private directory contains only disposable recordings, including after a
        // process death. Never recursively delete a caller-selected directory.
        directory.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
    }

    override suspend fun capture(released: Deferred<Unit>, started: () -> Unit): AudioReference =
        withContext(io) {
            if (!permitted()) throw CaptureFailure(TurnProblem.MICROPHONE_DENIED)
            if (released.isCompleted) throw CaptureFailure(TurnProblem.NO_SPEECH)
            val reference =
                AudioReference.of("mic_" + UUID.randomUUID().toString().replace("-", ""))
            val file = File(directory, reference.token)
            var source: PcmCapture? = null
            var retained = false
            val buffer = ByteArray(3200)
            try {
                source = open()
                source.start()
                val began = System.nanoTime()
                withContext(ui) { started() }
                RandomAccessFile(file, "rw").use { output ->
                    output.write(ByteArray(44))
                    var bytes = 0
                    while (
                        !released.isCompleted &&
                            bytes < MAXIMUM_BYTES &&
                            System.nanoTime() - began < 30_000_000_000L
                    ) {
                        currentCoroutineContext().ensureActive()
                        val count = source.read(buffer)
                        if (count < 0 || count > buffer.size || count % 2 != 0)
                            throw CaptureFailure(TurnProblem.MICROPHONE_UNAVAILABLE)
                        val accepted = minOf(count, MAXIMUM_BYTES - bytes)
                        if (accepted > 0) {
                            output.write(buffer, 0, accepted)
                            bytes += accepted
                        } else
                            delay(
                                10
                            ) // Nonblocking AudioRecord read keeps release/cancel responsive.
                    }
                    source.close()
                    source = null
                    if (bytes < 6400) throw CaptureFailure(TurnProblem.NO_SPEECH)
                    output.seek(0)
                    output.write(PcmWaveHeader.create(bytes))
                }
                currentCoroutineContext().ensureActive()
                retained = true
                reference
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (fault: CaptureFailure) {
                throw fault
            } catch (_: SecurityException) {
                throw CaptureFailure(TurnProblem.MICROPHONE_DENIED)
            } catch (_: Exception) {
                throw CaptureFailure(TurnProblem.MICROPHONE_UNAVAILABLE)
            } finally {
                withContext(NonCancellable) {
                    try {
                        source?.close()
                    } catch (_: Exception) {}
                    buffer.fill(0)
                    if (!retained) file.delete()
                }
            }
        }

    override fun resolve(audio: AudioReference): File {
        if (!audio.token.matches(Regex("mic_[a-f0-9]{32}")))
            throw CaptureFailure(TurnProblem.NO_SPEECH)
        return File(directory, audio.token).also {
            if (!it.isFile) throw CaptureFailure(TurnProblem.NO_SPEECH)
        }
    }

    override suspend fun discard(audio: AudioReference) =
        withContext(io) {
            if (audio.token.matches(Regex("mic_[a-f0-9]{32}")))
                File(directory, audio.token).delete()
            Unit
        }

    companion object {
        const val MAXIMUM_BYTES = 16000 * 2 * 30
    }
}

internal object PcmWaveHeader {
    fun create(bytes: Int): ByteArray {
        require(bytes in 0..MicrophoneInput.MAXIMUM_BYTES && bytes % 2 == 0)
        val header = ByteArray(44)
        fun text(offset: Int, value: String) =
            value.toByteArray(Charsets.US_ASCII).copyInto(header, offset)
        fun number(offset: Int, value: Int, count: Int) {
            repeat(count) { header[offset + it] = (value ushr (it * 8)).toByte() }
        }
        text(0, "RIFF")
        number(4, bytes + 36, 4)
        text(8, "WAVE")
        text(12, "fmt ")
        number(16, 16, 4)
        number(20, 1, 2)
        number(22, 1, 2)
        number(24, 16000, 4)
        number(28, 32000, 4)
        number(32, 2, 2)
        number(34, 16, 2)
        text(36, "data")
        number(40, bytes, 4)
        return header
    }
}

private class AndroidPcmCapture(context: Context) : PcmCapture {
    private val recorder: AudioRecord

    init {
        if (
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
        )
            throw CaptureFailure(TurnProblem.MICROPHONE_DENIED)
        val minimum =
            AudioRecord.getMinBufferSize(
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        if (minimum <= 0) throw CaptureFailure(TurnProblem.MICROPHONE_UNAVAILABLE)
        recorder =
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(16000)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minimum * 2, 6400))
                .build()
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw CaptureFailure(TurnProblem.MICROPHONE_UNAVAILABLE)
        }
    }

    override fun start() {
        recorder.startRecording()
        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING)
            throw CaptureFailure(TurnProblem.MICROPHONE_UNAVAILABLE)
    }

    override fun read(buffer: ByteArray) =
        recorder.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)

    override fun close() {
        try {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
        } finally {
            recorder.release()
        }
    }
}
