package com.example.ta33.domain.model

/** A single GPS fix. Reuses FR-02 GeoPoint; adds accuracy + fix timestamp. Shared by FR-05 breadcrumb and FR-08 geofencing. */
data class GeoPosition(
    val location: GeoPoint,        // FR-02 model (latitude, longitude)
    val accuracyMeters: Double,    // horizontal accuracy radius; larger = worse (canyon reflections)
    val timestampMillis: Long,     // time of the fix, from the platform provider
)
