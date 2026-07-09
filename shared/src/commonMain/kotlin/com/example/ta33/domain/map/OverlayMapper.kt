package com.example.ta33.domain.map

import com.example.ta33.domain.model.CheckpointMarker
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.LogUiState
import com.example.ta33.domain.model.Trackpoint

object OverlayMapper {
    fun toCheckpointMarkers(log: LogUiState): List<CheckpointMarker> =
        log.entries.map { entry ->
            CheckpointMarker(
                controlId = entry.control.id,
                ordinal = entry.control.ordinal,
                name = entry.control.name,
                location = entry.control.location,
                radiusMeters = entry.control.radiusMeters,
                state = entry.state,
                isNext = entry.control.id == log.nextControl?.id,
            )
        }

    /** Ordered control locations. NOTE: straight segments, not a real trail path (see plan §7). */
    fun toRoutePolyline(log: LogUiState): List<GeoPoint> =
        log.entries.sortedBy { it.control.ordinal }.map { it.control.location }

    fun toBreadcrumb(track: List<Trackpoint>): List<GeoPoint> = track.map { it.location }
}
