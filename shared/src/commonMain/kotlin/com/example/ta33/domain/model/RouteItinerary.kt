package com.example.ta33.domain.model

/**
 * Rich, display-oriented route content (full itinerary + elevation + metadata) backing the
 * redesigned Deník/Mapa/Profil screens. Complements the run/geofence-oriented [Route]/[ControlPoint].
 */
data class RouteItinerary(
    val routeId: String,
    val letter: String,      // "A" / "B"
    val name: String,        // "Teplicko-Adršpašská 33"
    val shortId: String,     // "TA33" / "TA50" (header)
    val distanceKm: Double,  // 33.2
    val ascentMeters: Int,
    val descentMeters: Int,
    val controlsCount: Int,
    val elevation: ElevationProfile,
    val waypoints: List<RouteWaypoint>,
)
