package com.example.ta33.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ta33.domain.model.SettingsUiState
import com.example.ta33.domain.repository.AppInfoRepository
import com.example.ta33.domain.usecase.ObserveNotificationsEnabledUseCase
import com.example.ta33.domain.usecase.SetNotificationsEnabledUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    observeNotifications: ObserveNotificationsEnabledUseCase,
    private val setNotifications: SetNotificationsEnabledUseCase,
    appInfo: AppInfoRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        SettingsUiState(organizerContact = appInfo.organizerContact(), faq = appInfo.faq()),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        observeNotifications()
            .onEach { enabled -> _state.update { it.copy(notificationsEnabled = enabled, loading = false) } }
            .launchIn(viewModelScope)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { setNotifications(enabled) }
    }
}
