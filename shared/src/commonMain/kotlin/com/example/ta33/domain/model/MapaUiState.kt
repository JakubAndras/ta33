package com.example.ta33.domain.model

/**
 * State backing the redesigned Mapa (`VariantHybrid`): a schematic route map (loop + control pins)
 * on top and a compact itinerary below. Sourced from the route catalog (RD-00); [highlightedControl]
 * is UI-local cross-highlighting between a tapped pin and its itinerary row (control ordinal 1..N).
 *
 * This is the schematic-design VM state; a real MapLibre map/GPS layer (FR-06 [MapUiState]) is a
 * separate follow-up and is intentionally not modelled here.
 */
data class MapaUiState(
    val routeId: String? = null,
    val shortId: String = "",             // "TA33" / "TA50" (route chip)
    val letter: String = "",              // "A" / "B" (chip badge)
    val distanceKm: Double = 0.0,
    val ascentMeters: Int = 0,
    val controlsCount: Int = 0,           // drives the pin layout (5 → PINS_5, 6 → PINS_6)
    val waypoints: List<RouteWaypoint> = emptyList(),
    val highlightedControl: Int? = null,  // controlOrdinal of the highlighted pin/row, or null
    val canSwitch: Boolean = true,        // ≥ 2 routes in the catalog
    val loading: Boolean = true,
)
