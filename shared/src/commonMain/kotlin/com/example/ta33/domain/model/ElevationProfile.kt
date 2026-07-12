package com.example.ta33.domain.model

/** Elevation profile for a route, as digitised in the design `TrasaData` source. */
data class ElevationProfile(
    val pointsNormalized: List<Double>, // 0..1 height samples, like TrasaData.elev
    val lowMeters: Int,                 // 462
    val highMeters: Int,                // 727
    val ascentMeters: Int,              // 740
    val descentMeters: Int,             // 740
    val kmTotal: Double,                // 33.2
    val tickKm: List<Int>,              // [8, 16, 24]
)
