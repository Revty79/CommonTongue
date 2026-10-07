package com.commontongue.domain

/** Product readiness only. No translator, model, or runtime exists in the foundation. */
enum class TranslationAvailability(val canTranslate: Boolean) {
    NOT_INSTALLED(canTranslate = false)
}
