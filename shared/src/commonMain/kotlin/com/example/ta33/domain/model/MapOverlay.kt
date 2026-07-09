package com.example.ta33.domain.model

/** A single checkpoint as the native marker layer needs it (projection of RunLogEntry). */
data class CheckpointMarker(
    val controlId: String,
    val ordinal: Int,
    val name: String,
    val location: GeoPoint,
    val radiusMeters: Double,
    val state: ControlPointState, // FR-04 → drives native marker color/style
    val isNext: Boolean, // matches LogUiState.nextControl → native highlight
)

/** Everything the native map draws on top of the basemap. */
data class MapOverlay(
    val routePolyline: List<GeoPoint> = emptyList(), // ordered control locations (see plan §7)
    val checkpointMarkers: List<CheckpointMarker> = emptyList(),
    val livePosition: GeoPosition? = null, // FR-05 live dot + accuracy radius
    val breadcrumb: List<GeoPoint> = emptyList(), // FR-05 track.map { it.location }
)
