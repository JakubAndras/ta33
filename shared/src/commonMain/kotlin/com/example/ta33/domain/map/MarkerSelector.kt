package com.example.ta33.domain.map

import com.example.ta33.domain.geo.GeoUtils
import com.example.ta33.domain.model.CheckpointMarker
import com.example.ta33.domain.model.GeoPoint

object MarkerSelector {
    /** Nearest marker within `maxMeters`, or null. Uses FR-02 GeoUtils Haversine. */
    fun selectNearest(markers: List<CheckpointMarker>, tap: GeoPoint, maxMeters: Double): CheckpointMarker? =
        markers
            .map { it to GeoUtils.distanceMeters(it.location, tap) }
            .filter { it.second <= maxMeters }
            .minByOrNull { it.second }
            ?.first
}
