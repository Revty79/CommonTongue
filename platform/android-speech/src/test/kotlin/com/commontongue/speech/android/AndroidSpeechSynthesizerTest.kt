package com.commontongue.speech.android

import com.commontongue.domain.LanguageId
import com.commontongue.translation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidSpeechSynthesizerTest {
    private class Engine(
        override val engine: String = "example.engine",
        override val voices: List<InstalledVoice> =
            listOf(
                InstalledVoice(engine, "english", LanguageId.parse("en-US"), false, true),
                InstalledVoice(engine, "spanish", LanguageId.parse("es-MX"), false, true),
            ),
    ) : VoiceEngine {
        var calls = 0
        var closes = 0
        var stops = 0
        var running = 0
        var peak = 0
        var delayMs = 0L
        var cleanupMs = 0L
        var failure: Exception? = null

        override suspend fun synthesize(
            text: String,
            voice: InstalledVoice,
            rate: Double,
        ): PcmAudio {
            calls++
            running++
            peak = maxOf(peak, running)
            try {
                delay(delayMs)
                failure?.let { throw it }
                return PcmAudio(byteArrayOf(100, 0, 100, 0), 16000, 1, 0)
            } finally {
                withContext(NonCancellable) {
                    delay(cleanupMs)
                    running--
                }
            }
        }

        override fun stop() {
            stops++
        }

        override fun close() {
            closes++
        }
    }

    private class Factory(val engine: Engine) : VoiceEngineFactory {
        var opens = 0
        var initializeMs = 0L
        var unavailable = false

        override suspend fun catalog() = EngineCatalog(engine.engine, listOf(engine.engine))

        override suspend fun open(engine: String): VoiceEngine {
            delay(initializeMs)
            if (unavailable) throw SpeechFault(SpeechCode.ENGINE_UNAVAILABLE)
            opens++
            return this.engine
        }
    }

    private class Player : LocalAudioPlayer {
        var calls = 0
        var delayMs = 0L
        var cleanupMs = 0L
        val requestTimes = mutableListOf<Long>()
        var code: SpeechCode? = null

        override suspend fun play(
            pcm: PcmAudio,
            synthesisRequestedNanos: Long,
        ): SpeechPlaybackReceipt {
            calls++
            requestTimes.add(synthesisRequestedNanos)
            try {
                delay(delayMs)
                code?.let { throw SpeechFault(it) }
                return SpeechPlaybackReceipt(10, 20.0, 5.0)
            } finally {
                withContext(NonCancellable) { delay(cleanupMs) }
            }
        }

        override fun stop() {}
    }

    private class Rig(scope: TestScope, val engine: Engine = Engine()) {
        val factory = Factory(engine)
        val player = Player()
        val events = mutableListOf<SpeechDiagnostic>()
        val speech =
            AndroidSpeechSynthesizer(
                factory,
                player,
                StandardTestDispatcher(scope.testScheduler),
                { scope.testScheduler.currentTime * 1000000 },
                { events.add(it) },
            )

        suspend fun ready() = speech.setForeground(true)

        suspend fun synthesize(language: String = "en", text: String = "PRIVATE_SENTINEL") =
            speech.synthesize(SynthesisRequest(text, LanguageId.parse(language)))
    }

    @Test
    fun implementsExistingContractAndReplaysWithoutResynthesis() = runTest {
        val rig = Rig(this)
        rig.ready()
        val capability: SpeechSynthesizer = rig.speech
        val output =
            capability.synthesize(SynthesisRequest("private", LanguageId.parse("es")))
                as CapabilityResult.Success
        assertEquals(ExecutionMode.OFFLINE, output.executionMode)
        assertEquals("es-MX", output.value.languageUsed.tag)
        assertTrue(rig.speech.play(output.value.audio) is CapabilityResult.Success)
        assertTrue(rig.speech.play(output.value.audio) is CapabilityResult.Success)
        assertEquals(1, rig.engine.calls)
        assertEquals(2, rig.player.calls)
        rig.speech.release(output.value.audio)
        assertEquals(
            FailureCategory.INPUT_INVALID,
            (rig.speech.play(output.value.audio) as CapabilityResult.Failure).failure.category,
        )
    }

    @Test
    fun rejectsMissingOfflineVoiceBeforeAnyTextCrossesEngine() = runTest {
        val engine =
            Engine(
                voices =
                    listOf(
                        InstalledVoice(
                            "example.engine",
                            "cloud",
                            LanguageId.parse("es-MX"),
                            true,
                            true,
                        )
                    )
            )
        val rig = Rig(this, engine)
        rig.ready()
        assertEquals(
            FailureCategory.MODEL_NOT_INSTALLED,
            (rig.synthesize("es") as CapabilityResult.Failure).failure.category,
        )
        assertEquals(0, engine.calls)
    }

    @Test
    fun rejectsSpeakerPreferenceBlankTextAndLongInput() = runTest {
        val rig = Rig(this)
        rig.ready()
        assertTrue(
            rig.speech.synthesize(
                SynthesisRequest(
                    "private",
                    LanguageId.parse("en"),
                    speakerPreservation = SpeakerPreservation.REQUESTED,
                )
            ) is CapabilityResult.Failure
        )
        assertTrue(rig.synthesize(text = " ") is CapabilityResult.Failure)
        assertEquals(
            FailureCategory.INPUT_TOO_LONG,
            (rig.synthesize(text = "a".repeat(4001)) as CapabilityResult.Failure).failure.category,
        )
        assertEquals(0, rig.engine.calls)
    }

    @Test
    fun rapidReplacementWaitsForOldNativeCleanupAndOnlyLatestSurvives() = runTest {
        val rig = Rig(this)
        rig.ready()
        rig.engine.delayMs = 100
        rig.engine.cleanupMs = 30
        val jobs =
            (1..8).map {
                async { rig.synthesize() }
                    .also {
                        advanceTimeBy(5)
                        runCurrent()
                    }
            }
        advanceUntilIdle()
        assertTrue(jobs.dropLast(1).all { it.isCancelled })
        assertTrue(jobs.last().await() is CapabilityResult.Success)
        assertEquals(1, rig.engine.peak)
    }

    @Test
    fun externalCancellationPropagatesAndCannotPublishAudio() = runTest {
        val rig = Rig(this)
        rig.ready()
        rig.engine.delayMs = 1000
        val job = async { rig.synthesize() }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertFalse(rig.events.any { it.stage == SpeechStage.SYNTHESIS_COMPLETE })
    }

    @Test
    fun backgroundClosesEngineAndResumesWithoutLosingReplay() = runTest {
        val rig = Rig(this)
        rig.ready()
        val audio = (rig.synthesize() as CapabilityResult.Success).value.audio
        rig.speech.setForeground(false)
        assertEquals(1, rig.engine.closes)
        assertEquals(
            FailureCategory.CANCELLED,
            (rig.speech.play(audio) as CapabilityResult.Failure).failure.category,
        )
        rig.ready()
        assertTrue(rig.speech.play(audio) is CapabilityResult.Success)
        assertTrue(rig.synthesize() is CapabilityResult.Success)
        assertEquals(2, rig.factory.opens)
    }

    @Test
    fun newSynthesisCancelsCurrentPlayback() = runTest {
        val rig = Rig(this)
        rig.ready()
        val audio = (rig.synthesize() as CapabilityResult.Success).value.audio
        rig.player.delayMs = 1000
        val playback = async { rig.speech.play(audio) }
        runCurrent()
        assertTrue(rig.synthesize() is CapabilityResult.Success)
        assertTrue(playback.isCancelled)
    }

    @Test
    fun focusDenialLossAndRouteDisconnectRemainStructured() = runTest {
        for (code in
            listOf(SpeechCode.FOCUS_DENIED, SpeechCode.FOCUS_LOST, SpeechCode.ROUTE_DISCONNECTED)) {
            val rig = Rig(this)
            rig.ready()
            val audio = (rig.synthesize() as CapabilityResult.Success).value.audio
            rig.player.code = code
            val failure = rig.speech.play(audio) as CapabilityResult.Failure
            assertEquals(code.category, failure.failure.category)
            assertTrue(failure.failure.explanation!!.startsWith(code.name))
        }
    }

    @Test
    fun diagnosticsCannotExposePrivateTextOrExceptionMessages() = runTest {
        val rig = Rig(this)
        rig.ready()
        rig.engine.failure = IllegalStateException("PRIVATE_SENTINEL /private/path")
        val result = rig.synthesize() as CapabilityResult.Failure
        assertFalse(result.failure.toString().contains("PRIVATE_SENTINEL"))
        assertFalse(rig.events.toString().contains("PRIVATE_SENTINEL"))
        assertFalse(rig.events.toString().contains("/private/path"))
    }

    @Test
    fun unavailableEngineAndCloseHaveDistinctFailureFromMissingVoice() = runTest {
        val rig = Rig(this)
        rig.ready()
        rig.factory.unavailable = true
        assertEquals(
            FailureCategory.ENGINE_NOT_AVAILABLE,
            (rig.synthesize() as CapabilityResult.Failure).failure.category,
        )
        rig.speech.close()
        rig.speech.close()
        assertEquals(
            FailureCategory.ENGINE_NOT_AVAILABLE,
            (rig.synthesize() as CapabilityResult.Failure).failure.category,
        )
    }

    @Test
    fun synthesisLatencyExcludesEngineInitialization() = runTest {
        val rig = Rig(this)
        rig.ready()
        rig.factory.initializeMs = 200
        rig.engine.delayMs = 100
        assertTrue(rig.synthesize() is CapabilityResult.Success)
        assertEquals(
            100.0,
            rig.events.single { it.stage == SpeechStage.SYNTHESIS_COMPLETE }.milliseconds!!,
            0.0,
        )
    }

    @Test
    fun replayMeasuresNewRequestInsteadOfTimeSinceOriginalSynthesis() = runTest {
        val rig = Rig(this)
        rig.ready()
        val audio = (rig.synthesize() as CapabilityResult.Success).value.audio
        rig.speech.play(audio)
        advanceTimeBy(1000)
        rig.speech.play(audio)
        assertEquals(listOf(0L, 1000000000L), rig.player.requestTimes)
    }

    @Test
    fun releasingOldReplayDuringReplacementCannotEraseNewAudio() = runTest {
        val rig = Rig(this)
        rig.ready()
        val old = (rig.synthesize() as CapabilityResult.Success).value.audio
        rig.player.delayMs = 1000
        rig.player.cleanupMs = 50
        val playback = async { rig.speech.play(old) }
        runCurrent()
        val release = launch { rig.speech.release(old) }
        runCurrent()
        val replacement = async { rig.synthesize() }
        runCurrent()
        advanceUntilIdle()
        assertTrue(playback.isCancelled)
        release.join()
        val latest = (replacement.await() as CapabilityResult.Success).value.audio
        assertTrue(rig.speech.play(latest) is CapabilityResult.Success)
    }

    @Test
    fun cancelledCloseCallerStillReleasesEngineAndPlayback() = runTest {
        val rig = Rig(this)
        rig.ready()
        rig.engine.delayMs = 1000
        rig.engine.cleanupMs = 50
        val speaking = async { rig.synthesize() }
        runCurrent()
        val closing = launch { rig.speech.close() }
        runCurrent()
        closing.cancelAndJoin()
        assertTrue(speaking.isCancelled)
        assertEquals(1, rig.engine.closes)
        assertTrue(rig.synthesize() is CapabilityResult.Failure)
    }

    @Test
    fun brokenDiagnosticSinkCannotBreakSpeech() = runTest {
        val engine = Engine()
        val speech =
            AndroidSpeechSynthesizer(
                Factory(engine),
                Player(),
                StandardTestDispatcher(testScheduler),
                { 0L },
                { error("sink broken") },
            )
        speech.setForeground(true)
        assertTrue(
            speech.synthesize(SynthesisRequest("private", LanguageId.parse("en")))
                is CapabilityResult.Success
        )
        speech.close()
    }
}
