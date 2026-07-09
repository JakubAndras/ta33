package com.example.ta33.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ta33.domain.model.LogUiState
import com.example.ta33.domain.usecase.ObserveRunLogUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class RunLogViewModel(
    private val observeRunLog: ObserveRunLogUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(LogUiState()) // loading = true, empty
    val state: StateFlow<LogUiState> = _state.asStateFlow()

    private var bindJob: Job? = null

    /** Bind to a concrete run + route. runId/routeId come from FR-01 app state / FR-03 active route. */
    fun bind(runId: String, routeId: String) {
        bindJob?.cancel()
        bindJob = observeRunLog(runId, routeId)
            .onEach { _state.value = it }
            .launchIn(viewModelScope)
    }
}
