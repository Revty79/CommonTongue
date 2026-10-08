package com.commontongue.translation

import com.commontongue.domain.DeviceQualityProfile
import com.commontongue.domain.LanguageId

/** Opaque handle resolved by a platform adapter; no path, URI, runtime buffer, or codec. */
@JvmInline
value class AudioReference private constructor(val token: String) {
    companion object {
        fun of(token: String): AudioReference {
            require(token.matches(Regex("[A-Za-z0-9_-]{1,128}")))
            return AudioReference(token)
        }
    }
}

@JvmInline
value class VoiceId private constructor(val token: String) {
    companion object {
        fun of(token: String): VoiceId {
            require(token.matches(Regex("[A-Za-z0-9_-]{1,128}")))
            return VoiceId(token)
        }
    }
}

sealed interface LanguageSelection {
    data class Locked(val language: LanguageId) : LanguageSelection

    /** Only this explicit choice permits detection; expectedLanguage is a hint, not a lock. */
    class Automatic(
        val expectedLanguage: LanguageId? = null,
        candidates: Set<LanguageId> = emptySet(),
    ) : LanguageSelection {
        val candidates = snapshotSet(candidates)
    }
}

class RecognitionRequest(
    val audio: AudioReference,
    val language: LanguageSelection,
    vocabulary: List<String> = emptyList(),
    val execution: ExecutionRequirement = ExecutionRequirement.OFFLINE_REQUIRED,
    val qualityProfile: DeviceQualityProfile = DeviceQualityProfile.STANDARD,
) {
    val vocabulary = snapshotList(vocabulary)

    init {
        require(this.vocabulary.all { it.isNotBlank() })
    }
}

data class RecognizedSpeech(
    val text: String,
    val languageUsed: LanguageId,
    val completion: CompletionStatus,
    val confidence: ConfidenceEvidence = ConfidenceEvidence.Unavailable,
)

sealed interface DetectionInput {
    data class Text(val text: String) : DetectionInput

    data class Audio(val audio: AudioReference) : DetectionInput
}

data class LanguageDetectionRequest(
    val input: DetectionInput,
    val selection: LanguageSelection,
    val execution: ExecutionRequirement = ExecutionRequirement.OFFLINE_REQUIRED,
)

sealed interface DetectedLanguage {
    data class Known(
        val language: LanguageId,
        val confidence: ConfidenceEvidence = ConfidenceEvidence.Unavailable,
    ) : DetectedLanguage

    data class Unknown(val explanation: String? = null) : DetectedLanguage
}

enum class SpeakerPreservation {
    DISABLED,
    REQUESTED,
}

data class SynthesisRequest(
    val text: String,
    val language: LanguageId,
    val voicePreference: VoiceId? = null,
    val rate: Double = 1.0,
    val speakerPreservation: SpeakerPreservation = SpeakerPreservation.DISABLED,
    val execution: ExecutionRequirement = ExecutionRequirement.OFFLINE_REQUIRED,
    val qualityProfile: DeviceQualityProfile = DeviceQualityProfile.STANDARD,
) {
    init {
        require(rate.isFinite() && rate > 0)
    }
}

data class SynthesizedSpeech(
    val audio: AudioReference,
    val languageUsed: LanguageId,
    val voiceUsed: VoiceId?,
    val completion: CompletionStatus = CompletionStatus.COMPLETE,
)
