package com.example.ta33.domain.usecase

import com.example.ta33.domain.model.Trackpoint
import com.example.ta33.domain.repository.TrackpointRepository
import kotlinx.coroutines.flow.Flow

class ObserveTrackUseCase(private val trackpoints: TrackpointRepository) {
    operator fun invoke(runId: String): Flow<List<Trackpoint>> = trackpoints.observeTrack(runId)
}
