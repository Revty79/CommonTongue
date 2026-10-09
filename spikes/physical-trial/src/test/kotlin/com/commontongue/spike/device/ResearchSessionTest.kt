package com.commontongue.spike.device

import org.junit.Assert.*
import org.junit.Test

class ResearchSessionTest {
    @Test
    fun speechWaitsForSuccessfulModelLoading() {
        val session = ResearchSession()
        assertFalse(session.canSpeak)
        assertThrows(IllegalStateException::class.java) { session.beginRecording() }
        session.beginLoad()
        assertFalse(session.canSpeak)
        assertThrows(IllegalStateException::class.java) { session.beginTurn() }
        session.modelsLoaded()
        assertTrue(session.canSpeak)
    }

    @Test
    fun loadFailureCannotEnableSpeechAndCanBeReloadedExplicitly() {
        val session = ResearchSession()
        session.beginLoad()
        session.failLoad()
        assertFalse(session.canSpeak)
        assertFalse(session.busy)
        assertThrows(IllegalStateException::class.java) { session.beginTurn() }
        session.beginLoad()
        session.modelsLoaded()
        assertTrue(session.canSpeak)
    }

    @Test
    fun recordingAndProcessingPreventOverlappingTurns() {
        val session = loaded()
        session.beginRecording()
        assertFalse(session.canSpeak)
        assertThrows(IllegalStateException::class.java) { session.beginRecording() }
        session.endRecording(true)
        assertTrue(session.busy)
        assertFalse(session.canSpeak)
        session.finishTurn()
        assertTrue(session.canSpeak)
    }

    @Test
    fun discardedRecordingAllowsAnotherTurn() {
        val session = loaded()
        session.beginRecording()
        session.endRecording(false)
        assertTrue(session.canSpeak)
    }

    @Test
    fun cancellationDuringLoadingOrTurnRequiresFreshModels() {
        val session = ResearchSession()
        session.beginLoad()
        session.unload()
        assertThrows(IllegalStateException::class.java) { session.modelsLoaded() }
        session.beginLoad()
        session.modelsLoaded()
        session.beginTurn()
        session.unload()
        session.finishTurn()
        assertFalse(session.canSpeak)
        assertFalse(session.busy)
        assertFalse(session.modelsReady)
    }

    @Test
    fun failedTurnDoesNotLeaveBusyStateOrLoadedModels() {
        val session = loaded()
        session.beginTurn()
        session.failLoad()
        assertFalse(session.canSpeak)
        assertFalse(session.busy)
        session.unload()
        assertEquals(ResearchSession.Phase.PACK_INSTALLED, session.phase)
    }

    private fun loaded() =
        ResearchSession().apply {
            beginLoad()
            modelsLoaded()
        }
}
