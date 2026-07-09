package com.example.ta33.domain.model

/** List-row projection for FR-03. Raw fields only — label/units/plurals are a UI concern. */
data class RouteSummary(
    val routeId: String,
    val name: String,        // e.g. "Trasa A"
    val distanceKm: Double,  // stored value from content JSON (FR-11)
    val controlCount: Int,   // e.g. 5
)
