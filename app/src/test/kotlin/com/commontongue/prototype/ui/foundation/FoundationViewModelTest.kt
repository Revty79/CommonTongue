package com.commontongue.prototype.ui.foundation

import com.commontongue.domain.TranslationAvailability
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FoundationViewModelTest {
    @Test
    fun initialStateReportsNoTranslationEngine() {
        val state = FoundationViewModel().uiState.value

        assertEquals(TranslationAvailability.NOT_INSTALLED, state.translationAvailability)
        assertFalse(state.translationAvailability.canTranslate)
    }

    @Test
    fun flowCollectorImmediatelyReceivesFoundationState() = runTest {
        val viewModel = FoundationViewModel()

        assertEquals(FoundationUiState(), viewModel.uiState.first())
    }
}
