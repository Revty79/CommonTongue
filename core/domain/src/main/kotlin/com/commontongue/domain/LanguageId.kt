package com.commontongue.domain

import java.util.IllformedLocaleException
import java.util.Locale

/** Canonical BCP-47 identity with a two/three-letter base, not proof of engine/dialect support. */
@JvmInline
value class LanguageId private constructor(val tag: String) {
    val baseLanguage: String
        get() = Locale.forLanguageTag(tag).language

    val region: String?
        get() = Locale.forLanguageTag(tag).country.ifEmpty { null }

    val script: String?
        get() = Locale.forLanguageTag(tag).script.ifEmpty { null }

    companion object {
        fun parse(value: String): LanguageId {
            val input = value.trim()
            require(input.isNotEmpty()) { "Language identity cannot be empty" }
            val locale =
                try {
                    Locale.Builder().setLanguageTag(input).build()
                } catch (failure: IllformedLocaleException) {
                    throw IllegalArgumentException("Malformed language identity", failure)
                }
            require(locale.language.isNotEmpty() && locale.language != "und") {
                "An explicit language is required; unknown detection has its own result"
            }
            require(locale.language.length in 2..3) {
                "Use a two/three-letter language code, not a language name"
            }
            return LanguageId(locale.toLanguageTag())
        }
    }
}
