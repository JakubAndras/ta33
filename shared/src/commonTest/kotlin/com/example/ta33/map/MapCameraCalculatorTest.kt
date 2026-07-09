package com.example.ta33.map

import com.example.ta33.domain.map.MapCameraCalculator
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.GeoPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapCameraCalculatorTest {

    private val fallback = GeoPoint(50.6156, 16.1122)

    private fun pos(lat: Double, lon: Double) = GeoPosition(GeoPoint(lat, lon), accuracyMeters = 5.0, timestampMillis = 0L)

    @Test
    fun livePresent_focusIsLivePosition() {
        val route = listOf(GeoPoint(50.0, 16.0), GeoPoint(51.0, 17.0))
        val cam = MapCameraCalculator.initialCamera(route, pos(50.5, 16.5), fallback)
        assertEquals(GeoPoint(50.5, 16.5), cam.focus)
    }

    @Test
    fun noLiveWithRoute_focusIsBoundsCenter() {
        val route = listOf(GeoPoint(50.0, 16.0), GeoPoint(51.0, 17.0))
        val cam = MapCameraCalculator.initialCamera(route, null, fallback)
        assertEquals(GeoPoint(50.5, 16.5), cam.focus)
    }

    @Test
    fun noRouteNoLive_focusIsFallbackAndBoundsNull() {
        val cam = MapCameraCalculator.initialCamera(emptyList(), null, fallback)
        assertEquals(fallback, cam.focus)
        assertNull(cam.bounds)
    }
}
