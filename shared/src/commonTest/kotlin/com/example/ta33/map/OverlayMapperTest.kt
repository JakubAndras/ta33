package com.example.ta33.map

import com.example.ta33.domain.log.ControlLogDeriver
import com.example.ta33.domain.map.OverlayMapper
import com.example.ta33.domain.model.CollectedControl
import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.ControlPointState
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.Trackpoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverlayMapperTest {

    private val routeId = "r1"

    // Intentionally out of ordinal order to prove sorting.
    private val c2 = ControlPoint("c2", routeId, ordinal = 2, name = "K2", location = GeoPoint(50.2, 16.2))
    private val c1 = ControlPoint("c1", routeId, ordinal = 1, name = "K1", location = GeoPoint(50.1, 16.1))

    private fun log(collected: List<CollectedControl> = emptyList()) =
        ControlLogDeriver.deriveLog(listOf(c2, c1), collected, run = null)

    @Test
    fun toCheckpointMarkers_mapStateAndIsNext() {
        val markers = OverlayMapper.toCheckpointMarkers(log())
        assertEquals(listOf("c1", "c2"), markers.map { it.controlId }) // ordered by ordinal
        val first = markers.first { it.controlId == "c1" }
        val second = markers.first { it.controlId == "c2" }
        assertEquals(ControlPointState.ACTIVE, first.state)
        assertTrue(first.isNext)
        assertEquals(ControlPointState.LOCKED, second.state)
        assertFalse(second.isNext)
    }

    @Test
    fun toCheckpointMarkers_reflectsCollectedState() {
        val markers = OverlayMapper.toCheckpointMarkers(log(listOf(CollectedControl("cc", "run", "c1", 1_000L))))
        assertEquals(ControlPointState.DONE, markers.first { it.controlId == "c1" }.state)
        val next = markers.first { it.controlId == "c2" }
        assertEquals(ControlPointState.ACTIVE, next.state)
        assertTrue(next.isNext)
    }

    @Test
    fun toRoutePolyline_orderedByOrdinal() {
        assertEquals(listOf(c1.location, c2.location), OverlayMapper.toRoutePolyline(log()))
    }

    @Test
    fun toBreadcrumb_extractsLocations() {
        val track = listOf(
            Trackpoint("t1", "run", GeoPoint(1.0, 2.0), 5.0, 0L),
            Trackpoint("t2", "run", GeoPoint(3.0, 4.0), 5.0, 1L),
        )
        assertEquals(listOf(GeoPoint(1.0, 2.0), GeoPoint(3.0, 4.0)), OverlayMapper.toBreadcrumb(track))
    }
}
