package com.example.ta33.map

import com.example.ta33.domain.map.RouteBounds
import com.example.ta33.domain.model.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouteBoundsTest {

    @Test
    fun emptyList_returnsNull() {
        assertNull(RouteBounds.boundingBox(emptyList()))
    }

    @Test
    fun singlePoint_returnsDegenerateBox() {
        val p = GeoPoint(50.6, 16.1)
        val box = RouteBounds.boundingBox(listOf(p))!!
        assertEquals(p, box.southWest)
        assertEquals(p, box.northEast)
        assertEquals(p, box.center)
    }

    @Test
    fun multiPoint_returnsMinMaxAndCenter() {
        val points = listOf(
            GeoPoint(50.0, 16.0),
            GeoPoint(51.0, 17.0),
            GeoPoint(50.5, 15.5),
        )
        val box = RouteBounds.boundingBox(points)!!
        assertEquals(GeoPoint(50.0, 15.5), box.southWest)
        assertEquals(GeoPoint(51.0, 17.0), box.northEast)
        assertEquals(GeoPoint(50.5, 16.25), box.center)
    }
}
