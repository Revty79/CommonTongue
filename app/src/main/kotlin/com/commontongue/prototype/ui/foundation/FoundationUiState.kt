package com.commontongue.prototype.ui.foundation

import com.commontongue.domain.TranslationAvailability

data class FoundationUiState(
    val translationAvailability: TranslationAvailability = TranslationAvailability.NOT_INSTALLED
)
