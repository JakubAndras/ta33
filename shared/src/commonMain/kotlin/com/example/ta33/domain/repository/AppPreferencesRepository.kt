package com.example.ta33.domain.repository

import kotlinx.coroutines.flow.Flow

interface AppPreferencesRepository {
    fun observeSelectedRouteId(): Flow<String?>
    suspend fun getSelectedRouteId(): String?
    suspend fun setSelectedRouteId(routeId: String?)

    fun observeNotificationsEnabled(): Flow<Boolean>
    suspend fun getNotificationsEnabled(): Boolean
    suspend fun setNotificationsEnabled(enabled: Boolean)
}
