package com.example.ta33.domain.repository

import com.example.ta33.domain.model.Trackpoint
import kotlinx.coroutines.flow.Flow

interface TrackpointRepository {
    suspend fun append(trackpoint: Trackpoint)
    fun observeTrack(runSessionId: String): Flow<List<Trackpoint>>
    suspend fun getTrack(runSessionId: String): List<Trackpoint>
    suspend fun getLastTrackpoint(runSessionId: String): Trackpoint? // for resume-seed
    suspend fun clearTrack(runSessionId: String)
}
