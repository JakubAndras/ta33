package com.example.ta33.domain.model

/** Downsample thresholds. Defaults are field-tunable (see plan §12.2). */
data class BreadcrumbConfig(
    val minDistanceMeters: Double = 10.0, // keep a point after ~10 m of movement
    val minTimeMillis: Long = 5_000,      // …but no more often than every 5 s
    val maxAccuracyMeters: Double = 50.0, // drop fixes worse than this (canyon noise)
)
