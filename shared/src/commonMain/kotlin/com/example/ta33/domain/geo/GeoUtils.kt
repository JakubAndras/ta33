package com.example.ta33.domain.geo

import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.GeoPoint
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Haversine great-circle distance in metres. */
    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val dLat = (b.latitude - a.latitude).toRadians()
        val dLon = (b.longitude - a.longitude).toRadians()
        val lat1 = a.latitude.toRadians()
        val lat2 = b.latitude.toRadians()
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    fun isWithinRadius(control: ControlPoint, location: GeoPoint): Boolean =
        distanceMeters(control.location, location) <= control.radiusMeters

    private fun Double.toRadians() = this * PI / 180.0
}
