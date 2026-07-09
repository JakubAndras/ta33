package com.example.ta33

import com.example.ta33.domain.geo.GeoUtils
import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.GeoPoint
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeoUtilsTest {

    // Reference point (Adršpach area).
    private val base = GeoPoint(50.6100, 16.1100)

    // ~1 deg latitude ≈ 111_320 m, so 0.00027 deg ≈ 30 m and 0.00050 deg ≈ 55 m north.
    private val near30m = GeoPoint(50.6100 + 0.00027, 16.1100)
    private val far55m = GeoPoint(50.6100 + 0.00050, 16.1100)

    @Test
    fun distance_pointAround30m_isBelow50() {
        val d = GeoUtils.distanceMeters(base, near30m)
        assertTrue(d < 50.0, "expected < 50 m but was $d")
    }

    @Test
    fun distance_pointAround55m_isAbove50() {
        val d = GeoUtils.distanceMeters(base, far55m)
        assertTrue(d > 50.0, "expected > 50 m but was $d")
    }

    @Test
    fun isWithinRadius_respectsControlRadius() {
        val control = ControlPoint(
            id = "c1", routeId = "r1", ordinal = 1, name = "K1", location = base,
        )
        assertTrue(GeoUtils.isWithinRadius(control, near30m))
        assertFalse(GeoUtils.isWithinRadius(control, far55m))
    }
}
