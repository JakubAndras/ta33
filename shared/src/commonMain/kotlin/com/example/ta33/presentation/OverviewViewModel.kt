package com.example.ta33.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ta33.domain.model.OverviewUiState
import com.example.ta33.domain.usecase.ObserveOverviewUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class OverviewViewModel(
    private val observeOverview: ObserveOverviewUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(OverviewUiState())   // loading = true
    val state: StateFlow<OverviewUiState> = _state.asStateFlow()

    init {
        observeOverview().onEach { _state.value = it }.launchIn(viewModelScope)
    }
}
