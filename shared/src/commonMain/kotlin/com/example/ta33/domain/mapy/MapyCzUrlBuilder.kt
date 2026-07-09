package com.example.ta33.domain.mapy

import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.GeoPoint

/**
 * Builds a universal mapy.cz route link: https://mapy.com/fnc/v1/route
 * Opens the mapy.cz app if installed, otherwise the browser (handled by the OS).
 * Pure & deterministic — unit-tested in commonTest.
 */
class MapyCzUrlBuilder {

    /**
     * @param controls route controls; order is normalised by [ControlPoint.ordinal] here.
     * @return the route URL, or null when there is nothing to navigate (no controls).
     */
    fun build(
        controls: List<ControlPoint>,
        routeType: MapyRouteType = MapyRouteType.FOOT_HIKING,
    ): String? {
        val ordered = controls.sortedBy { it.ordinal }
        if (ordered.isEmpty()) return null

        val start = ordered.first().location
        val end = ordered.last().location
        // intermediate = everything between first and last, capped to MAX_WAYPOINTS, order kept.
        val middle = if (ordered.size <= 2) {
            emptyList()
        } else {
            capWaypoints(ordered.subList(1, ordered.size - 1))
        }

        val params = buildString {
            append("start=").append(coord(start))
            append("&end=").append(coord(end))
            if (middle.isNotEmpty()) {
                append("&waypoints=")
                append(middle.joinToString(WAYPOINT_SEPARATOR) { coord(it.location) })
            }
            append("&routeType=").append(routeType.apiValue)
        }
        return "$BASE_URL?$params"
    }

    /** GeoPoint(lat, lon) -> "lon,lat" (mapy.cz is longitude-first). */
    private fun coord(p: GeoPoint): String =
        "${formatCoordinate(p.longitude)},${formatCoordinate(p.latitude)}"

    /** Locale-independent decimal (Kotlin Double.toString always uses '.'), rounded to 6 dp (~0.1 m). */
    internal fun formatCoordinate(value: Double): String {
        val rounded = kotlin.math.round(value * 1_000_000.0) / 1_000_000.0
        return rounded.toString()
    }

    /** Even down-sample to at most MAX_WAYPOINTS while preserving order. */
    private fun capWaypoints(points: List<ControlPoint>): List<ControlPoint> {
        if (points.size <= MAX_WAYPOINTS) return points
        val step = points.size.toDouble() / MAX_WAYPOINTS
        return (0 until MAX_WAYPOINTS).map { i -> points[(i * step).toInt()] }
    }

    companion object {
        const val BASE_URL = "https://mapy.com/fnc/v1/route"
        const val WAYPOINT_SEPARATOR = ";"
        const val MAX_WAYPOINTS = 15
    }
}
