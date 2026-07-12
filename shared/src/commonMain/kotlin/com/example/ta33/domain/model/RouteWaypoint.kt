package com.example.ta33.domain.model

/**
 * A single row of the route itinerary (propozice): a place with its trail mark, turn direction
 * and km position from the start. [location] is populated only for [WaypointKind.CONTROL]
 * waypoints (approximate coords for FR-08 geofence logic).
 */
data class RouteWaypoint(
    val index: Int,                  // order within the itinerary (0-based)
    val name: String,                // "Zámek Bischofstein"
    val km: Double,                  // position from start, e.g. 5.9
    val kind: WaypointKind,
    val controlOrdinal: Int? = null, // set only for kind == CONTROL (1..N)
    val direction: TurnDirection = TurnDirection.STRAIGHT,
    val mark: TrailMark = TrailMark.VLASTNI,
    val markNumber: String? = null,  // cyclo route number (mark == CYKLO)
    val location: GeoPoint? = null,  // approximate coords, set only for kind == CONTROL
)
