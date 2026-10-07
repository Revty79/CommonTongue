package com.commontongue.prototype.ui.foundation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.commontongue.domain.TranslationAvailability
import com.commontongue.prototype.R

@Composable
fun FoundationRoute(viewModel: FoundationViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FoundationScreen(uiState)
}

@Composable
fun FoundationScreen(uiState: FoundationUiState, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.product_name),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.foundation_build),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text =
                    when (uiState.translationAvailability) {
                        TranslationAvailability.NOT_INSTALLED ->
                            stringResource(R.string.engine_not_installed)
                    },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.prototype_description),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
