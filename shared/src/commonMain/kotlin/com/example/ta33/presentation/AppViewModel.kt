package com.example.ta33.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ta33.domain.usecase.EnsureLocalParticipantUseCase
import com.example.ta33.domain.usecase.ObserveAppStateUseCase
import com.example.ta33.presentation.navigation.AppUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class AppViewModel(
    private val observeAppState: ObserveAppStateUseCase,
    private val ensureLocalParticipant: EnsureLocalParticipantUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AppUiState())     // starts LOADING
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init { start() }

    private fun start() {
        // Seed the anonymous local participant for later run creation (does NOT gate navigation).
        viewModelScope.launch { ensureLocalParticipant() }

        observeAppState()
            .onEach { _state.value = it }
            .launchIn(viewModelScope)
    }
}
