package com.example.ta33

import com.example.ta33.domain.geo.BreadcrumbDecision
import com.example.ta33.domain.geo.BreadcrumbThrottle
import com.example.ta33.domain.model.BreadcrumbConfig
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.GeoPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BreadcrumbThrottleTest {

    private val config = BreadcrumbConfig(minDistanceMeters = 10.0, minTimeMillis = 5_000, maxAccuracyMeters = 50.0)
    private val throttle = BreadcrumbThrottle(config)

    // Base near the Adršpach/Teplice trail. 0.0001° latitude ≈ 11.1 m; 0.00005° ≈ 5.6 m.
    private fun pos(latDelta: Double = 0.0, accuracy: Double = 5.0, t: Long = 0L) =
        GeoPosition(GeoPoint(50.0 + latDelta, 16.0), accuracy, t)

    @Test
    fun firstGoodFix_isKept() {
        val d = throttle.decide(lastKept = null, candidate = pos())
        assertTrue(d is BreadcrumbDecision.Keep)
    }

    @Test
    fun poorAccuracy_isDropped() {
        val d = throttle.decide(lastKept = null, candidate = pos(accuracy = 51.0))
        assertEquals(BreadcrumbDecision.Drop(BreadcrumbDecision.DropReason.POOR_ACCURACY), d)
    }

    @Test
    fun withinMinTime_isDroppedTooSoon() {
        val last = pos(t = 0L)
        val d = throttle.decide(last, candidate = pos(latDelta = 0.0001, t = 4_000L)) // moved enough, too soon
        assertEquals(BreadcrumbDecision.Drop(BreadcrumbDecision.DropReason.TOO_SOON), d)
    }

    @Test
    fun enoughTimeButTooClose_isDroppedTooClose() {
        val last = pos(t = 0L)
        val d = throttle.decide(last, candidate = pos(latDelta = 0.00005, t = 6_000L)) // ~5.6 m < 10 m
        assertEquals(BreadcrumbDecision.Drop(BreadcrumbDecision.DropReason.TOO_CLOSE), d)
    }

    @Test
    fun enoughTimeAndDistance_isKept() {
        val last = pos(t = 0L)
        val d = throttle.decide(last, candidate = pos(latDelta = 0.0001, t = 6_000L)) // ~11.1 m, 6 s
        assertTrue(d is BreadcrumbDecision.Keep)
    }
}
