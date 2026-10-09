package com.commontongue.prototype.platform

import com.commontongue.translation.CaptureFailure
import com.commontongue.translation.TurnProblem
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class MicrophoneInputTest {
    @get:Rule val temporary = TemporaryFolder()

    private class Source : PcmCapture {
        var started = false
        var closed = false
        var reads = 0
        var limit = 2
        var error = false

        override fun start() {
            started = true
        }

        override fun read(buffer: ByteArray): Int {
            reads++
            if (error) return -3
            if (reads > limit) return 0
            buffer.fill(12)
            return buffer.size
        }

        override fun close() {
            closed = true
        }
    }

    @Test
    fun pressStartsPcmCaptureReleaseClosesAndWritesWhisperCompatibleWav() = runTest {
        val directory = temporary.newFolder()
        val source = Source()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mic = MicrophoneInput(directory, { source }, { true }, dispatcher, dispatcher)
        val release = CompletableDeferred<Unit>()
        var started = false
        val recording = async { mic.capture(release) { started = true } }
        runCurrent()
        assertTrue(source.started && started)
        assertFalse(source.closed)
        release.complete(Unit)
        runCurrent()
        val reference = recording.await()
        val file = mic.resolve(reference)
        val bytes = file.readBytes()
        assertTrue(source.closed)
        assertEquals(44 + 6400, bytes.size)
        assertEquals("RIFF", bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1, header.getShort(20).toInt())
        assertEquals(1, header.getShort(22).toInt())
        assertEquals(16000, header.getInt(24))
        assertEquals(16, header.getShort(34).toInt())
        assertEquals(6400, header.getInt(40))
        mic.discard(reference)
        assertFalse(file.exists())
    }

    @Test
    fun cancellingCaptureClosesRecorderAndDeletesPrivateRecording() = runTest {
        val source = Source()
        val directory = temporary.newFolder()
        val d = StandardTestDispatcher(testScheduler)
        val mic = MicrophoneInput(directory, { source }, { true }, d, d)
        val recording = launch { mic.capture(CompletableDeferred()) {} }
        runCurrent()
        recording.cancelAndJoin()
        assertTrue(source.closed)
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test
    fun permissionDeniedNeverOpensRecorderOrWritesFile() = runTest {
        val directory = temporary.newFolder()
        var opened = false
        val d = StandardTestDispatcher(testScheduler)
        val mic =
            MicrophoneInput(
                directory,
                {
                    opened = true
                    Source()
                },
                { false },
                d,
                d,
            )
        val failure =
            try {
                mic.capture(CompletableDeferred()) {}
                null
            } catch (error: CaptureFailure) {
                error
            }
        assertEquals(TurnProblem.MICROPHONE_DENIED, failure!!.problem)
        assertFalse(opened)
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test
    fun maximumThirtySecondsEndsEvenIfUserKeepsHolding() = runTest {
        val source = Source().apply { limit = Int.MAX_VALUE }
        val directory = temporary.newFolder()
        val d = StandardTestDispatcher(testScheduler)
        val mic = MicrophoneInput(directory, { source }, { true }, d, d)
        val reference = mic.capture(CompletableDeferred()) {}
        assertEquals((MicrophoneInput.MAXIMUM_BYTES + 44).toLong(), mic.resolve(reference).length())
        assertTrue(source.closed)
        mic.discard(reference)
    }

    @Test
    fun invalidReadProducesPlainFailureAndDeletesFile() = runTest {
        val source = Source().apply { error = true }
        val directory = temporary.newFolder()
        val d = StandardTestDispatcher(testScheduler)
        val mic = MicrophoneInput(directory, { source }, { true }, d, d)
        val failure =
            try {
                mic.capture(CompletableDeferred()) {}
                null
            } catch (error: CaptureFailure) {
                error
            }
        assertEquals(TurnProblem.MICROPHONE_UNAVAILABLE, failure!!.problem)
        assertNull(failure.message)
        assertTrue(source.closed)
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test
    fun releaseBeforeRecorderIsReadyDoesNotCreateEmptyTurn() = runTest {
        var opened = false
        val d = StandardTestDispatcher(testScheduler)
        val directory = temporary.newFolder()
        val mic =
            MicrophoneInput(
                directory,
                {
                    opened = true
                    Source()
                },
                { true },
                d,
                d,
            )
        try {
            mic.capture(CompletableDeferred(Unit)) {}
            fail()
        } catch (failure: CaptureFailure) {
            assertEquals(TurnProblem.NO_SPEECH, failure.problem)
        }
        assertFalse(opened)
    }

    @Test
    fun restartDeletesOnlyTheDedicatedRecordingCache() {
        val directory = temporary.newFolder()
        File(directory, "mic_old").writeText("private")
        val other = temporary.newFile("unrelated").apply { writeText("keep") }
        MicrophoneInput(
            directory,
            { Source() },
            { true },
            StandardTestDispatcher(),
            StandardTestDispatcher(),
        )
        assertTrue(directory.listFiles()!!.isEmpty())
        assertEquals("keep", other.readText())
    }
}
