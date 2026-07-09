package com.example.ta33.domain.usecase

import com.example.ta33.domain.repository.AppPreferencesRepository
import kotlinx.coroutines.flow.Flow

class ObserveNotificationsEnabledUseCase(private val prefs: AppPreferencesRepository) {
    operator fun invoke(): Flow<Boolean> = prefs.observeNotificationsEnabled()
}
