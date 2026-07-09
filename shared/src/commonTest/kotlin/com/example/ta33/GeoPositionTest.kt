package com.example.ta33

import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.GeoPosition
import kotlin.test.Test
import kotlin.test.assertEquals

class GeoPositionTest {

    @Test
    fun reusesGeoPointAndAddsAccuracyAndTimestamp() {
        val p = GeoPosition(GeoPoint(50.0, 16.0), accuracyMeters = 7.5, timestampMillis = 123_456L)
        assertEquals(GeoPoint(50.0, 16.0), p.location)
        assertEquals(7.5, p.accuracyMeters)
        assertEquals(123_456L, p.timestampMillis)
    }
}
