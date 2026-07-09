package com.example.ta33.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.ta33.data.db.Ta33Database
import com.example.ta33.data.mapper.toDomain
import com.example.ta33.domain.model.Trackpoint
import com.example.ta33.domain.repository.TrackpointRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TrackpointRepositoryImpl(private val db: Ta33Database) : TrackpointRepository {
    private val q get() = db.trackpointQueries

    override suspend fun append(trackpoint: Trackpoint) {
        withContext(Dispatchers.Default) {
            q.insertTrackpoint(
                trackpoint.id,
                trackpoint.runSessionId,
                trackpoint.location.latitude,
                trackpoint.location.longitude,
                trackpoint.accuracyMeters,
                trackpoint.timestampMillis,
                trackpoint.syncStatus.name,
            )
        }
    }

    override fun observeTrack(runSessionId: String): Flow<List<Trackpoint>> =
        q.selectTrackForRun(runSessionId).asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getTrack(runSessionId: String): List<Trackpoint> =
        withContext(Dispatchers.Default) {
            q.selectTrackForRun(runSessionId).executeAsList().map { it.toDomain() }
        }

    override suspend fun getLastTrackpoint(runSessionId: String): Trackpoint? =
        withContext(Dispatchers.Default) {
            q.selectLastTrackpoint(runSessionId).executeAsOneOrNull()?.toDomain()
        }

    override suspend fun clearTrack(runSessionId: String) {
        withContext(Dispatchers.Default) {
            q.deleteForRun(runSessionId)
        }
    }
}
