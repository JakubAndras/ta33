package com.example.ta33

import com.example.ta33.domain.mapy.MapyCzUrlBuilder
import com.example.ta33.domain.mapy.MapyRouteType
import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapyCzUrlBuilderTest {

    private val builder = MapyCzUrlBuilder()

    private fun control(ordinal: Int, lat: Double, lon: Double): ControlPoint =
        ControlPoint(
            id = "c$ordinal",
            routeId = "r1",
            ordinal = ordinal,
            name = "Control $ordinal",
            location = GeoPoint(latitude = lat, longitude = lon),
        )

    @Test
    fun coordinateOrder_isLongitudeFirst() {
        val url = builder.build(listOf(control(1, lat = 50.0878, lon = 14.4606)))
        assertTrue(url!!.contains("start=14.4606,50.0878"), url)
    }

    @Test
    fun ordering_startFirstEndLastMiddleAsWaypoints() {
        val shuffled = listOf(
            control(3, lat = 50.2, lon = 14.2),
            control(1, lat = 50.0, lon = 14.0),
            control(2, lat = 50.1, lon = 14.1),
        )
        val url = builder.build(shuffled)
        assertEquals(
            "https://mapy.com/fnc/v1/route?start=14.0,50.0&end=14.2,50.2&waypoints=14.1,50.1&routeType=foot_hiking",
            url,
        )
    }

    @Test
    fun twoControls_startAndEnd_noWaypoints() {
        val url = builder.build(
            listOf(
                control(1, lat = 50.0, lon = 14.0),
                control(2, lat = 50.2, lon = 14.2),
            ),
        )
        assertEquals(
            "https://mapy.com/fnc/v1/route?start=14.0,50.0&end=14.2,50.2&routeType=foot_hiking",
            url,
        )
        assertTrue(!url!!.contains("waypoints"), url)
    }

    @Test
    fun singleControl_startEqualsEnd_noWaypoints() {
        val url = builder.build(listOf(control(1, lat = 50.0, lon = 14.0)))
        assertEquals(
            "https://mapy.com/fnc/v1/route?start=14.0,50.0&end=14.0,50.0&routeType=foot_hiking",
            url,
        )
        assertTrue(!url!!.contains("waypoints"), url)
    }

    @Test
    fun empty_returnsNull() {
        assertNull(builder.build(emptyList()))
    }

    @Test
    fun routeType_defaultsToFootHiking() {
        val url = builder.build(listOf(control(1, lat = 50.0, lon = 14.0)))
        assertTrue(url!!.endsWith("routeType=foot_hiking"), url)
    }

    @Test
    fun routeType_overridable() {
        val url = builder.build(
            listOf(control(1, lat = 50.0, lon = 14.0)),
            routeType = MapyRouteType.BIKE_MOUNTAIN,
        )
        assertTrue(url!!.endsWith("routeType=bike_mountain"), url)
    }

    @Test
    fun noOverEncoding_literalCommasAndSemicolons() {
        // 4 controls => 2 intermediate waypoints => the ';' separator is present.
        val url = builder.build(
            listOf(
                control(1, lat = 50.0878, lon = 14.4606),
                control(2, lat = 50.1, lon = 14.1),
                control(3, lat = 50.15, lon = 14.15),
                control(4, lat = 50.2, lon = 14.2),
            ),
        )!!
        assertTrue(url.contains("14.4606,50.0878"), url)
        assertTrue(url.contains("14.1,50.1;14.15,50.15"), url)
        assertTrue(!url.contains("%2C"), url)
        assertTrue(!url.contains("%3B"), url)
        assertTrue(!url.contains("%3b"), url)
        assertTrue(!url.contains("%2c"), url)
    }

    @Test
    fun decimalFormatting_localeIndependentNoArtifacts() {
        val url = builder.build(
            listOf(
                control(1, lat = 16.0, lon = -0.5),
                control(2, lat = 50.0, lon = 14.0),
            ),
        )!!
        // longitude-first: -0.5 then 16.0
        assertTrue(url.contains("start=-0.5,16.0"), url)
        // no scientific notation (Kotlin Double.toString emits uppercase 'E' for that)
        assertTrue(!url.contains("E"), url)
    }

    @Test
    fun waypointCap_reducesToAtMost15() {
        val controls = (1..20).map { control(it, lat = 50.0 + it * 0.01, lon = 14.0 + it * 0.01) }
        val url = builder.build(controls)!!
        val waypointsPart = url.substringAfter("waypoints=").substringBefore("&")
        val waypointCount = waypointsPart.split(";").size
        assertTrue(waypointCount <= 15, "waypoint count was $waypointCount")
        // start = ordinal 1, end = ordinal 20
        assertTrue(url.contains("start=${lonLat(14.01, 50.01)}"), url)
        assertTrue(url.contains("end=${lonLat(14.2, 50.2)}"), url)
    }

    private fun lonLat(lon: Double, lat: Double): String {
        val r = { v: Double -> (kotlin.math.round(v * 1_000_000.0) / 1_000_000.0).toString() }
        return "${r(lon)},${r(lat)}"
    }
}
