package com.example.ta33.domain.usecase

import com.example.ta33.core.IdGenerator
import com.example.ta33.data.location.LocationStream
import com.example.ta33.domain.geo.BreadcrumbDecision
import com.example.ta33.domain.geo.BreadcrumbThrottle
import com.example.ta33.domain.model.GeoPosition
import com.example.ta33.domain.model.Trackpoint
import com.example.ta33.domain.repository.TrackpointRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class RecordBreadcrumbUseCase(
    private val locationStream: LocationStream,
    private val trackpoints: TrackpointRepository,
    private val throttle: BreadcrumbThrottle,
    private val ids: IdGenerator, // FR-02
) {
    /** Records the breadcrumb for [runId] until cancelled. Emits each kept Trackpoint (for logging/tests). */
    fun record(runId: String): Flow<Trackpoint> = flow {
        // Resume-seed: don't duplicate a point right after an app kill.
        var lastKept: GeoPosition? = trackpoints.getLastTrackpoint(runId)?.let {
            GeoPosition(it.location, it.accuracyMeters, it.timestampMillis)
        }
        locationStream.positions().collect { position ->
            when (throttle.decide(lastKept, position)) {
                is BreadcrumbDecision.Keep -> {
                    val tp = Trackpoint(
                        id = ids.newId(),
                        runSessionId = runId,
                        location = position.location,
                        accuracyMeters = position.accuracyMeters,
                        timestampMillis = position.timestampMillis,
                    )
                    trackpoints.append(tp)
                    lastKept = position
                    emit(tp)
                }
                is BreadcrumbDecision.Drop -> {
                    // ignore; the drop reason is available for debug logging
                }
            }
        }
    }
}
