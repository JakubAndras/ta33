package com.example.ta33.domain.model

/**
 * Geofence tuning for checkpoint collection (FR-08). Defaults are field-tunable on the
 * Adršpach/Teplice trail (canyon GPS noise). Independent of FR-05's BreadcrumbConfig but
 * the accuracy gate plays the same role.
 */
data class GeofenceConfig(
    /** Fixes worse than this cannot PROMOTE a candidate (they hold the current one). */
    val maxAccuracyMeters: Double = 50.0,
    /** Exit hysteresis: an offered control stays offered until distance exceeds radius + this. */
    val exitMarginMeters: Double = 20.0,
    /** Debounce: this many consecutive in-range fixes before a fresh control is offered. */
    val minConsecutiveFixes: Int = 2,
)
