package com.commontongue.prototype.ui.foundation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FoundationViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(FoundationUiState())
    val uiState: StateFlow<FoundationUiState> = mutableUiState.asStateFlow()
}
