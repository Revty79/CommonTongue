package com.commontongue.spike.device

/** Model lifetime and one active turn; radio settings are only research observations. */
internal class ResearchSession {
    enum class Phase {
        PACK_INSTALLED,
        LOADING_MODELS,
        READY_TO_SPEAK,
        LOAD_FAILED,
    }

    var phase = Phase.PACK_INSTALLED
        private set

    var recording = false
        private set

    private var turnActive = false

    val modelsReady: Boolean
        get() = phase == Phase.READY_TO_SPEAK

    val busy: Boolean
        get() = phase == Phase.LOADING_MODELS || turnActive

    val canSpeak: Boolean
        get() = modelsReady && !busy && !recording

    fun beginLoad() {
        check(!busy && !modelsReady && !recording) { "Please wait for model loading to finish." }
        phase = Phase.LOADING_MODELS
    }

    fun modelsLoaded() {
        check(phase == Phase.LOADING_MODELS)
        phase = Phase.READY_TO_SPEAK
    }

    fun beginRecording() {
        check(canSpeak) { "Wait for Ready to Speak before holding a language button." }
        recording = true
    }

    fun endRecording(submit: Boolean) {
        check(recording)
        recording = false
        turnActive = submit
    }

    fun beginTurn() {
        check(canSpeak) { "Wait for Ready to Speak and the current turn to finish." }
        turnActive = true
    }

    fun finishTurn() {
        turnActive = false
    }

    fun failLoad() {
        reset(Phase.LOAD_FAILED)
    }

    fun unload() {
        reset(Phase.PACK_INSTALLED)
    }

    private fun reset(next: Phase) {
        phase = next
        recording = false
        turnActive = false
    }
}
