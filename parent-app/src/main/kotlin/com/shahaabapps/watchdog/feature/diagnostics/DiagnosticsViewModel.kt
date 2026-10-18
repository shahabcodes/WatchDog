package com.shahaabapps.watchdog.feature.diagnostics

import androidx.lifecycle.ViewModel
import com.shahaabapps.core.firebase.diagnostics.DiagnosticsLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class DiagnosticsUiState(
    val enabled: Boolean,
    val events: List<DiagnosticsLogger.Event>,
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val logger: DiagnosticsLogger,
) : ViewModel() {

    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<DiagnosticsUiState> = _state.asStateFlow()

    private fun snapshot() = DiagnosticsUiState(enabled = logger.enabled, events = logger.recent().asReversed())

    fun refresh() { _state.value = snapshot() }

    fun setEnabled(value: Boolean) {
        logger.setEnabled(value)
        refresh()
    }

    fun exportText(): String = logger.recentText()
}
