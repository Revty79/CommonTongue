package com.commontongue.domain

/** Directional translation between distinct base languages; locale rewriting is a separate task. */
data class TranslationDirection(val source: LanguageId, val target: LanguageId) {
    init {
        require(source.baseLanguage != target.baseLanguage) {
            "Translation requires different base languages"
        }
    }

    fun reversed() = TranslationDirection(target, source)
}
