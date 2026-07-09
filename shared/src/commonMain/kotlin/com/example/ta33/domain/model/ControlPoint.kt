package com.example.ta33.domain.model

data class ControlPoint(
    val id: String,
    val routeId: String,
    val ordinal: Int,               // 1-based order along the route
    val name: String,
    val location: GeoPoint,
    val radiusMeters: Double = 50.0, // FR-08 ~50 m
)
