package com.example.ta33.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.example.ta33.data.db.Ta33Database
import com.example.ta33.domain.repository.AppPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AppPreferencesRepositoryImpl(
    private val db: Ta33Database,
) : AppPreferencesRepository {
    private val q get() = db.appPreferencesQueries

    override fun observeSelectedRouteId(): Flow<String?> =
        q.selectPreferences().asFlow().mapToOneOrNull(Dispatchers.Default)
            .map { it?.selectedRouteId }

    override suspend fun getSelectedRouteId(): String? =
        withContext(Dispatchers.Default) {
            q.selectPreferences().executeAsOneOrNull()?.selectedRouteId
        }

    override suspend fun setSelectedRouteId(routeId: String?) {
        withContext(Dispatchers.Default) {
            q.setSelectedRouteId(routeId)
        }
    }

    override fun observeNotificationsEnabled(): Flow<Boolean> =
        q.selectPreferences().asFlow().mapToOneOrNull(Dispatchers.Default)
            .map { (it?.notificationsEnabled ?: 1L) == 1L }

    override suspend fun getNotificationsEnabled(): Boolean =
        withContext(Dispatchers.Default) {
            (q.selectPreferences().executeAsOneOrNull()?.notificationsEnabled ?: 1L) == 1L
        }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        withContext(Dispatchers.Default) {
            q.setNotificationsEnabled(if (enabled) 1L else 0L)
        }
    }
}
