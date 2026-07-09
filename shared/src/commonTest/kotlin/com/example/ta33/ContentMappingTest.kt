package com.example.ta33

import com.example.ta33.data.dto.ControlDto
import com.example.ta33.data.dto.RouteDto
import com.example.ta33.data.dto.toDomain
import kotlin.test.Test
import kotlin.test.assertEquals

class ContentMappingTest {

    @Test
    fun routeDto_mapsToDomain_backfillsRouteIdAndDefaults() {
        val dto = RouteDto(
            id = "r1",
            name = "Trasa A",
            distanceKm = 33.0,
            controls = listOf(
                ControlDto(id = "c1", ordinal = 1, name = "Start", lat = 50.6, lon = 16.1),
                ControlDto(id = "c2", ordinal = 2, name = "Cil", lat = 50.7, lon = 16.2, radiusMeters = 25.0),
            ),
        )

        val (route, controls) = dto.toDomain()

        assertEquals("r1", route.id)
        assertEquals("Trasa A", route.name)
        assertEquals(33.0, route.distanceKm)
        assertEquals(2, route.controls.size)
        assertEquals(controls, route.controls)

        val c1 = controls[0]
        assertEquals("c1", c1.id)
        assertEquals("r1", c1.routeId)
        assertEquals(1, c1.ordinal)
        assertEquals("Start", c1.name)
        assertEquals(50.6, c1.location.latitude)
        assertEquals(16.1, c1.location.longitude)
        assertEquals(50.0, c1.radiusMeters) // default

        assertEquals(25.0, controls[1].radiusMeters)
        assertEquals("r1", controls[1].routeId)
    }
}
