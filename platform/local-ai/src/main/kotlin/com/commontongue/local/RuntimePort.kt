package com.commontongue.local

import com.commontongue.domain.LanguageId
import com.commontongue.domain.TranslationDirection
import com.commontongue.translation.AudioReference

data class NativeRecognition(val text: String, val languageUsed: LanguageId, val complete: Boolean)

data class NativeTranslation(
    val text: String,
    val directionUsed: TranslationDirection,
    val complete: Boolean,
)

/**
 * Owned by one LocalAiSession; a backend must finish/reclaim active native work before returning
 * from release.
 */
interface LocalRuntime {
    val isOffline: Boolean

    suspend fun load(resources: ValidatedCoreResources)

    suspend fun recognize(audio: AudioReference, language: LanguageId): NativeRecognition

    suspend fun translate(text: String, direction: TranslationDirection): NativeTranslation

    suspend fun release()
}
