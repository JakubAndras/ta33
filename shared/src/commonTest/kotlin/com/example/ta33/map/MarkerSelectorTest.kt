package com.example.ta33.map

import com.example.ta33.domain.map.MarkerSelector
import com.example.ta33.domain.model.CheckpointMarker
import com.example.ta33.domain.model.ControlPointState
import com.example.ta33.domain.model.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MarkerSelectorTest {

    private fun marker(id: String, lat: Double, lon: Double) = CheckpointMarker(
        controlId = id,
        ordinal = 1,
        name = id,
        location = GeoPoint(lat, lon),
        radiusMeters = 50.0,
        state = ControlPointState.LOCKED,
        isNext = false,
    )

    @Test
    fun nearestWithinRadius_selected() {
        val markers = listOf(marker("far", 50.010, 16.0), marker("near", 50.0001, 16.0))
        val chosen = MarkerSelector.selectNearest(markers, GeoPoint(50.0, 16.0), maxMeters = 60.0)
        assertEquals("near", chosen?.controlId)
    }

    @Test
    fun noneWithinRadius_returnsNull() {
        val markers = listOf(marker("far", 50.010, 16.0))
        assertNull(MarkerSelector.selectNearest(markers, GeoPoint(50.0, 16.0), maxMeters = 60.0))
    }

    @Test
    fun tie_returnsFirstDeterministically() {
        // Both equidistant (mirror image around the tap latitude).
        val markers = listOf(marker("a", 50.0005, 16.0), marker("b", 49.9995, 16.0))
        val chosen = MarkerSelector.selectNearest(markers, GeoPoint(50.0, 16.0), maxMeters = 1000.0)
        assertEquals("a", chosen?.controlId)
    }

    @Test
    fun emptyList_returnsNull() {
        assertNull(MarkerSelector.selectNearest(emptyList(), GeoPoint(50.0, 16.0), maxMeters = 60.0))
    }
}
