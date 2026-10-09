package com.commontongue.translation

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationTurnCoordinatorTest {
    private class Rig(val scope: TestScope) {
        val events = mutableListOf<TurnDiagnostic>()
        val calls = mutableListOf<String>()
        val recognition = mutableListOf<RecognitionRequest>()
        val translation = mutableListOf<TranslationRequest>()
        val synthesis = mutableListOf<SynthesisRequest>()
        var missing = false
        var failSpeech = false
        var online = false
        var partial = false
        var ignoredCancellation = false
        var componentCancels = false
        var delayAt: String? = null
        val blocked = CompletableDeferred<Unit>()
        var discarded = 0
        val description = CapabilityDescription(setOf(ExecutionMode.OFFLINE))
        val coordinator =
            ConversationTurnCoordinator(
                scope,
                object : ConversationInput {
                    override suspend fun capture(
                        released: Deferred<Unit>,
                        started: () -> Unit,
                    ): AudioReference {
                        calls += "microphone"
                        started()
                        released.await()
                        calls += "microphone-stopped"
                        return AudioReference.of("captured")
                    }

                    override suspend fun discard(audio: AudioReference) {
                        discarded++
                    }
                },
                object : SpeechRecognizer {
                    override val description = this@Rig.description

                    override suspend fun recognize(
                        request: RecognitionRequest
                    ): CapabilityResult<RecognizedSpeech> {
                        calls += "recognizer"
                        recognition += request
                        if (componentCancels) throw CancellationException("fixed test cancellation")
                        if (delayAt == "ASR") {
                            if (ignoredCancellation) withContext(NonCancellable) { blocked.await() }
                            else blocked.await()
                        }
                        val language = (request.language as LanguageSelection.Locked).language
                        return CapabilityResult.Success(
                            RecognizedSpeech(
                                "actual-source",
                                language,
                                if (partial) CompletionStatus.PARTIAL
                                else CompletionStatus.COMPLETE,
                            ),
                            if (online) ExecutionMode.ONLINE else ExecutionMode.OFFLINE,
                        )
                    }
                },
                object : Translator {
                    override val description = this@Rig.description

                    override suspend fun translate(request: TranslationRequest): TranslationResult {
                        calls += "translator"
                        translation += request
                        if (delayAt == "MT") blocked.await()
                        return CapabilityResult.Success(
                            TranslatedText("actual-target", request.direction),
                            ExecutionMode.OFFLINE,
                        )
                    }
                },
                object : SpeechSynthesizer {
                    override val description = this@Rig.description

                    override suspend fun synthesize(
                        request: SynthesisRequest
                    ): CapabilityResult<SynthesizedSpeech> {
                        calls += "synthesis"
                        synthesis += request
                        if (failSpeech)
                            return CapabilityResult.Failure(
                                CapabilityFailure(FailureCategory.MODEL_NOT_INSTALLED)
                            )
                        return CapabilityResult.Success(
                            SynthesizedSpeech(AudioReference.of("spoken"), request.language, null),
                            ExecutionMode.OFFLINE,
                        )
                    }
                },
                object : TranslationDestination {
                    override suspend fun play(
                        audio: AudioReference
                    ): CapabilityResult<SpeechPlaybackReceipt> {
                        calls += "play"
                        if (delayAt == "PLAY") blocked.await()
                        return CapabilityResult.Success(
                            SpeechPlaybackReceipt(100, 3.0, 1.0),
                            ExecutionMode.OFFLINE,
                        )
                    }

                    override suspend fun cancel() {
                        calls += "stop-playback"
                    }

                    override suspend fun release(audio: AudioReference) {
                        calls += "release-replay"
                    }
                },
                object : ConversationResources {
                    override suspend fun prepare(): CapabilityResult<Unit> {
                        calls += "prepare"
                        return if (missing)
                            CapabilityResult.Failure(
                                CapabilityFailure(FailureCategory.MODEL_NOT_INSTALLED)
                            )
                        else CapabilityResult.Success(Unit, ExecutionMode.OFFLINE)
                    }

                    override suspend fun setForeground(value: Boolean) {
                        calls += "foreground-$value"
                    }

                    override suspend fun close() {
                        calls += "close"
                    }
                },
                events::add,
            )

        fun ready() {
            coordinator.foreground(true)
            scope.runCurrent()
        }

        fun start(side: TalkSide = TalkSide.ENGLISH): Long {
            val token = coordinator.press(side)!!
            scope.runCurrent()
            return token
        }

        fun finish(token: Long) {
            coordinator.release(token)
            scope.runCurrent()
        }
    }

    @Test
    fun pressRecordsAndReleaseStartsRealCapabilitySequence() = runTest {
        val r = Rig(this)
        r.ready()
        val token = r.start()
        assertEquals(TurnStage.LISTENING, r.coordinator.state.value.stage)
        assertTrue(r.recognition.isEmpty())
        r.finish(token)
        assertEquals(TurnStage.COMPLETE, r.coordinator.state.value.stage)
        assertEquals(
            listOf(
                "microphone",
                "microphone-stopped",
                "recognizer",
                "translator",
                "synthesis",
                "play",
            ),
            r.calls.filter {
                it in
                    setOf(
                        "microphone",
                        "microphone-stopped",
                        "recognizer",
                        "translator",
                        "synthesis",
                        "play",
                    )
            },
        )
        assertEquals(1, r.discarded)
        assertTrue(r.calls.indexOf("stop-playback") < r.calls.indexOf("microphone"))
    }

    @Test
    fun bothDirectionsLockRecognitionAndSpeakTheTargetLanguage() = runTest {
        val r = Rig(this)
        r.ready()
        TalkSide.entries.forEach { side ->
            r.finish(r.start(side))
            assertEquals(
                side.source,
                (r.recognition.last().language as LanguageSelection.Locked).language,
            )
            assertEquals(side.direction, r.translation.last().direction)
            assertEquals(side.target, r.synthesis.last().language)
        }
        assertEquals(2, r.coordinator.state.value.recent.size)
    }

    @Test
    fun displayedTranscriptAndTranslationAreCapabilityOutputs() = runTest {
        val r = Rig(this)
        r.ready()
        r.finish(r.start())
        assertEquals("actual-source", r.coordinator.state.value.recognized)
        assertEquals("actual-target", r.coordinator.state.value.translated)
        assertEquals("actual-target", r.synthesis.single().text)
    }

    @Test
    fun meaningfulStagesAreInOrder() = runTest {
        val r = Rig(this)
        r.ready()
        r.finish(r.start())
        val stages = r.events.map { it.stage }.distinct()
        assertEquals(
            listOf(
                TurnStage.LOADING_MODELS,
                TurnStage.IDLE,
                TurnStage.STARTING,
                TurnStage.LISTENING,
                TurnStage.RECOGNIZING,
                TurnStage.TRANSLATING,
                TurnStage.SYNTHESIZING,
                TurnStage.SPEAKING,
                TurnStage.COMPLETE,
            ),
            stages,
        )
    }

    @Test
    fun replayDoesNotRecognizeTranslateOrSynthesizeAgain() = runTest {
        val r = Rig(this)
        r.ready()
        r.finish(r.start())
        r.coordinator.replay()
        runCurrent()
        assertEquals(2, r.calls.count { it == "play" })
        assertEquals(1, r.translation.size)
        assertEquals(1, r.synthesis.size)
    }

    @Test
    fun replacementRevokesReplayBeforeOpeningMicrophone() = runTest {
        val r = Rig(this)
        r.ready()
        r.finish(r.start())
        r.start(TalkSide.SPANISH)
        assertFalse(r.coordinator.state.value.replayAvailable)
        assertTrue(r.calls.lastIndexOf("release-replay") < r.calls.lastIndexOf("microphone"))
        r.coordinator.cancel()
        runCurrent()
    }

    @Test
    fun releaseFromPreviousPressDoesNotStopReplacement() = runTest {
        val r = Rig(this)
        r.ready()
        val first = r.start()
        val second = r.start(TalkSide.SPANISH)
        r.coordinator.release(first)
        runCurrent()
        assertEquals(TurnStage.LISTENING, r.coordinator.state.value.stage)
        r.finish(second)
        assertEquals(TalkSide.SPANISH, r.coordinator.state.value.side)
    }

    @Test
    fun staleNonCooperativeResultCannotPublishOrBeginTranslation() = runTest {
        val r = Rig(this)
        r.ready()
        r.delayAt = "ASR"
        r.ignoredCancellation = true
        r.finish(r.start())
        r.coordinator.cancel()
        runCurrent()
        r.blocked.complete(Unit)
        runCurrent()
        assertTrue(r.translation.isEmpty())
        assertEquals(TurnStage.CANCELLED, r.coordinator.state.value.stage)
        assertEquals("", r.coordinator.state.value.recognized)
        assertEquals(1, r.discarded)
    }

    @Test
    fun cancellationIsNotAnErrorAndNextTurnSucceeds() = runTest {
        val r = Rig(this)
        r.ready()
        r.delayAt = "MT"
        r.finish(r.start())
        r.coordinator.cancel()
        runCurrent()
        assertEquals(TurnStage.CANCELLED, r.coordinator.state.value.stage)
        r.delayAt = null
        r.finish(r.start(TalkSide.SPANISH))
        assertEquals(TurnStage.COMPLETE, r.coordinator.state.value.stage)
        assertFalse(r.events.any { it.stage == TurnStage.ERROR })
    }

    @Test
    fun backgroundDuringCaptureStopsTurnAndReturnCanTryAgain() = runTest {
        val r = Rig(this)
        r.ready()
        r.start()
        r.coordinator.foreground(false)
        runCurrent()
        assertFalse(r.coordinator.state.value.modelsReady)
        assertTrue(r.recognition.isEmpty())
        r.ready()
        r.finish(r.start())
        assertEquals(TurnStage.COMPLETE, r.coordinator.state.value.stage)
    }

    @Test
    fun backgroundDuringInferenceDiscardsPrivateRecording() = runTest {
        val r = Rig(this)
        r.ready()
        r.delayAt = "ASR"
        r.finish(r.start())
        r.coordinator.foreground(false)
        runCurrent()
        assertEquals(1, r.discarded)
        assertTrue(r.translation.isEmpty())
    }

    @Test
    fun backgroundDuringPlaybackRevokesReplay() = runTest {
        val r = Rig(this)
        r.ready()
        r.delayAt = "PLAY"
        r.finish(r.start())
        r.coordinator.foreground(false)
        runCurrent()
        assertFalse(r.coordinator.state.value.replayAvailable)
        assertTrue("release-replay" in r.calls)
    }

    @Test
    fun missingResourcesBlockTalkWithoutAcquisition() = runTest {
        val r = Rig(this)
        r.missing = true
        r.ready()
        assertEquals(TurnStage.RESOURCES_REQUIRED, r.coordinator.state.value.stage)
        assertNull(r.coordinator.press(TalkSide.ENGLISH))
        assertFalse("microphone" in r.calls)
    }

    @Test
    fun offlineRequiredIsPassedToEveryCapabilityWithoutHistory() = runTest {
        val r = Rig(this)
        r.ready()
        repeat(2) { r.finish(r.start()) }
        assertTrue(r.recognition.all { it.execution == ExecutionRequirement.OFFLINE_REQUIRED })
        assertTrue(
            r.translation.all {
                it.execution == ExecutionRequirement.OFFLINE_REQUIRED &&
                    it.context == ConversationContext.None &&
                    it.terminology.isEmpty()
            }
        )
        assertTrue(r.synthesis.all { it.execution == ExecutionRequirement.OFFLINE_REQUIRED })
    }

    @Test
    fun onlineResultIsRejectedBeforeTranslationOrPlayback() = runTest {
        val r = Rig(this)
        r.ready()
        r.online = true
        r.finish(r.start())
        assertEquals(TurnProblem.OFFLINE_REQUIRED, r.coordinator.state.value.problem)
        assertTrue(r.translation.isEmpty())
        assertFalse("play" in r.calls)
    }

    @Test
    fun partialSourceIsShownButNeverSilentlySpokenAsComplete() = runTest {
        val r = Rig(this)
        r.ready()
        r.partial = true
        r.finish(r.start())
        assertEquals("actual-source", r.coordinator.state.value.recognized)
        assertEquals(TurnProblem.PARTIAL_RECOGNITION, r.coordinator.state.value.problem)
        assertTrue(r.translation.isEmpty())
    }

    @Test
    fun missingOfflineVoiceHasSpecificFailureAndRetryWorks() = runTest {
        val r = Rig(this)
        r.ready()
        r.failSpeech = true
        r.finish(r.start())
        assertEquals(TurnProblem.OFFLINE_VOICE_UNAVAILABLE, r.coordinator.state.value.problem)
        r.failSpeech = false
        r.coordinator.retry()
        runCurrent()
        assertEquals(TurnStage.COMPLETE, r.coordinator.state.value.stage)
        assertEquals(1, r.recognition.size)
    }

    @Test
    fun permissionDenialAndPermanentDenialAreDistinct() = runTest {
        val r = Rig(this)
        r.ready()
        r.coordinator.permissionDenied(false)
        runCurrent()
        assertEquals(TurnProblem.MICROPHONE_DENIED, r.coordinator.state.value.problem)
        r.coordinator.permissionDenied(true)
        runCurrent()
        assertEquals(TurnProblem.MICROPHONE_SETTINGS, r.coordinator.state.value.problem)
        assertTrue(r.recognition.isEmpty())
    }

    @Test
    fun historyIsBoundedAndClearRevokesTextAndAudio() = runTest {
        val r = Rig(this)
        r.ready()
        repeat(12) { r.finish(r.start()) }
        assertEquals(8, r.coordinator.state.value.recent.size)
        r.coordinator.cancel(clear = true)
        runCurrent()
        assertEquals("", r.coordinator.state.value.recognized)
        assertTrue(r.coordinator.state.value.recent.isEmpty())
        assertFalse(r.coordinator.state.value.replayAvailable)
        assertTrue(r.coordinator.state.value.modelsReady)
    }

    @Test
    fun closeErasesSessionAndReleasesResources() = runTest {
        val r = Rig(this)
        r.ready()
        r.finish(r.start())
        r.coordinator.close().join()
        assertEquals(ConversationState(), r.coordinator.state.value)
        assertTrue("close" in r.calls)
        assertNull(r.coordinator.press(TalkSide.ENGLISH))
    }

    @Test
    fun diagnosticsContainOnlyFiniteStageAndProblemValues() = runTest {
        val r = Rig(this)
        r.ready()
        r.finish(r.start())
        assertFalse(r.events.toString().contains("actual-source"))
        assertFalse(r.events.toString().contains("actual-target"))
        assertEquals(
            setOf("stage", "problem"),
            TurnDiagnostic::class
                .java
                .declaredFields
                .filterNot { it.isSynthetic }
                .map { it.name }
                .toSet(),
        )
    }

    @Test
    fun unavailableReplayDoesNotOpenAnyCapability() = runTest {
        val r = Rig(this)
        r.ready()
        r.coordinator.replay()
        assertEquals(TurnProblem.AUDIO_UNAVAILABLE, r.coordinator.state.value.problem)
        assertTrue(r.recognition.isEmpty())
    }

    @Test
    fun componentCancellationEndsTheVisibleProcessingStateAndNextTurnSucceeds() = runTest {
        val r = Rig(this)
        r.ready()
        r.componentCancels = true
        r.finish(r.start())
        assertEquals(TurnStage.CANCELLED, r.coordinator.state.value.stage)
        assertTrue(r.translation.isEmpty())
        assertFalse(r.events.any { it.stage == TurnStage.ERROR })
        r.componentCancels = false
        r.finish(r.start())
        assertEquals(TurnStage.COMPLETE, r.coordinator.state.value.stage)
    }
}
