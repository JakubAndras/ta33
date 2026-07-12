package com.example.ta33.domain.model

/** Derived progress of a timeline stop (START/CONTROL/FINISH) for the Deník `VariantPrehled` design. */
enum class StopStatus { DONE, NEXT, UPCOMING }

/**
 * One milestone on the "Kontroly na trase" timeline: start, a numbered control, or the finish.
 * Pure display model derived from [RouteItinerary] + the run state; no UI/formatting concerns
 * (wall-clock times are exposed as raw millis and formatted natively per platform).
 */
data class DenikStop(
    val kind: WaypointKind,
    val label: String,             // "Start" / "Kontrola 3" / "Cíl"
    val name: String,              // place name
    val km: Double,                // position from start
    val controlOrdinal: Int?,      // 1..N for CONTROL, null otherwise
    val status: StopStatus,
    val segmentKm: Double?,        // distance to the next stop; null for the last stop
    val startTimeMillis: Long? = null, // START only, when the run has started
    val isFinish: Boolean = false,
)

/**
 * State backing the redesigned Deník (`VariantPrehled`): dark header (route id + stats), the
 * "Kontroly na trase" timeline and the elevation profile. Combines the route catalog (RD-00)
 * with the run state; the per-control status is only meaningful when the shown route is the
 * active run's route (otherwise everything is UPCOMING = a preview of the other route).
 */
data class DenikUiState(
    val routeId: String? = null,
    val shortId: String = "",              // "TA33" / "TA50" (header)
    val distanceKm: Double = 0.0,
    val startTimeMillis: Long? = null,     // run start (header "Čas startu"); null → "—"
    val finishTimeMillis: Long? = null,    // run finish (header "Finální čas"); null → "—"
    val ascentMeters: Int = 0,
    val descentMeters: Int = 0,
    val elevation: ElevationProfile? = null,
    val stops: List<DenikStop> = emptyList(),
    val canSwitch: Boolean = true,         // ≥ 2 routes in the catalog
    val loading: Boolean = true,
)
