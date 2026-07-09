package com.example.ta33.domain.usecase

import com.example.ta33.domain.repository.AppPreferencesRepository

class SetNotificationsEnabledUseCase(private val prefs: AppPreferencesRepository) {
    suspend operator fun invoke(enabled: Boolean) = prefs.setNotificationsEnabled(enabled)
}
