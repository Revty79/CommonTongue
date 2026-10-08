package com.commontongue.translation

import com.commontongue.domain.LanguageId
import com.commontongue.domain.TranslationDirection

@JvmInline
value class DomainContext private constructor(val id: String) {
    companion object {
        val GENERAL = of("general")

        fun of(id: String): DomainContext {
            require(id.matches(Regex("[a-z][a-z0-9._-]{0,63}"))) { "Invalid context identifier" }
            return DomainContext(id)
        }
    }
}

enum class ConversationSide {
    FIRST,
    SECOND,
}

data class ConversationTurn(
    val side: ConversationSide,
    val sourceText: String,
    val translatedText: String,
    val direction: TranslationDirection,
) {
    init {
        require(sourceText.isNotBlank() && translatedText.isNotBlank())
    }
}

data class ContextLimits(val maxTurns: Int = 8, val maxCharacters: Int = 8000) {
    init {
        require(maxTurns > 0 && maxCharacters > 0)
    }
}

/** In-memory request snapshot, not storage. Overflow is rejected, never silently truncated. */
class ConversationContext
private constructor(turns: List<ConversationTurn>, val limits: ContextLimits) {
    val turns = snapshotList(turns)

    companion object {
        val None = ConversationContext(emptyList(), ContextLimits())

        fun recent(
            turns: List<ConversationTurn>,
            limits: ContextLimits = ContextLimits(),
        ): ConversationContext {
            val snapshot = snapshotList(turns)
            require(snapshot.size <= limits.maxTurns)
            require(
                snapshot.sumOf { it.sourceText.length.toLong() + it.translatedText.length } <=
                    limits.maxCharacters
            )
            return ConversationContext(snapshot, limits)
        }
    }
}

enum class TerminologyStrength {
    PREFERRED,
    REQUIRED,
}

data class TerminologyHint(
    val sourcePhrase: String,
    val targetTerm: String? = null,
    val targetMeaning: String? = null,
    val domain: DomainContext? = null,
    val explanation: String? = null,
    val strength: TerminologyStrength = TerminologyStrength.PREFERRED,
) {
    init {
        require(sourcePhrase.isNotBlank())
        require(targetTerm == null || targetTerm.isNotBlank())
        require(targetMeaning == null || targetMeaning.isNotBlank())
        require(targetTerm != null || targetMeaning != null)
    }
}

enum class TargetStyle {
    NATURAL,
    NEUTRAL,
}

/** A region can be a macroregion such as 419; it is not assumed to be a country. */
data class RegionalPreference(val locale: LanguageId, val style: TargetStyle = TargetStyle.NATURAL)
