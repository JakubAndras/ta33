package com.example.ta33.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class ContentDto(val routes: List<RouteDto>)

@Serializable
data class RouteDto(
    val id: String,
    val name: String,
    val distanceKm: Double,
    val controls: List<ControlDto> = emptyList(),
)

@Serializable
data class ControlDto(
    val id: String,
    val ordinal: Int,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radiusMeters: Double = 50.0,
)
