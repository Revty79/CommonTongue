package com.commontongue.local.android

import android.content.Context
import com.commontongue.local.LocalAiSession
import com.commontongue.local.LocalDiagnostic
import com.commontongue.local.MadladTranslator
import com.commontongue.local.WhisperSpeechRecognizer
import com.commontongue.translation.SpeechRecognizer
import com.commontongue.translation.Translator

/** Composition root only. UI and use cases receive neutral capabilities. */
class LocalCapabilities
private constructor(
    val recognizer: SpeechRecognizer,
    val translator: Translator,
    val lifecycle: LocalAiSession,
) {
    companion object {
        fun create(
            context: Context,
            resources: AndroidResourceBindings,
            audio: LocalAudioSource,
            diagnostic: (LocalDiagnostic) -> Unit = {},
        ): LocalCapabilities {
            val runtime = AndroidLocalRuntime(context, resources, audio, diagnostic)
            val session = LocalAiSession(resources, runtime, diagnostic)
            return LocalCapabilities(
                WhisperSpeechRecognizer(session),
                MadladTranslator(session),
                session,
            )
        }
    }
}
