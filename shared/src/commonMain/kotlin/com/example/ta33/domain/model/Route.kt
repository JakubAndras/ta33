package com.example.ta33.domain.model

data class Route(
    val id: String,
    val name: String,          // e.g. "Trasa A"
    val distanceKm: Double,     // e.g. 33.0
    val controls: List<ControlPoint> = emptyList(), // aggregate; empty when metadata-only
) {
    val controlCount: Int get() = controls.size
}
