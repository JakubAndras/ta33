package com.example.ta33.domain.model

/** Detail projection: route metadata + its controls ordered by ordinal. */
data class RouteDetail(
    val routeId: String,
    val name: String,
    val distanceKm: Double,
    val controls: List<ControlPoint>, // sorted by ordinal (1-based)
) {
    val controlCount: Int get() = controls.size
}
