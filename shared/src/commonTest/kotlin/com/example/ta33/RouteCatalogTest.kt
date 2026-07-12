package com.example.ta33

import com.example.ta33.data.content.RouteCatalog
import com.example.ta33.domain.model.TrailMark
import com.example.ta33.domain.model.TurnDirection
import com.example.ta33.domain.model.WaypointKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteCatalogTest {

    private val ta33 = RouteCatalog.byId(RouteCatalog.TA33_ROUTE_ID)!!
    private val ta50 = RouteCatalog.byId(RouteCatalog.TA50_ROUTE_ID)!!

    @Test
    fun catalog_exposesBothRoutes() {
        assertEquals(2, RouteCatalog.itineraries.size)
        assertEquals(listOf("TA33", "TA50"), RouteCatalog.itineraries.map { it.shortId })
    }

    @Test
    fun byId_unknownRoute_isNull() {
        assertNull(RouteCatalog.byId("nope"))
    }

    @Test
    fun ta33_hasExpectedMetadataAndControls() {
        assertEquals("A", ta33.letter)
        assertEquals("Teplicko-Adršpašská 33", ta33.name)
        assertEquals(33.2, ta33.distanceKm)
        assertEquals(740, ta33.ascentMeters)
        assertEquals(740, ta33.descentMeters)
        assertEquals(5, ta33.controlsCount)
        assertEquals(19, ta33.waypoints.size)
        assertEquals(5, ta33.waypoints.count { it.kind == WaypointKind.CONTROL })
    }

    @Test
    fun ta50_hasExpectedMetadataAndControls() {
        assertEquals("B", ta50.letter)
        assertEquals(49.6, ta50.distanceKm)
        assertEquals(1085, ta50.ascentMeters)
        assertEquals(6, ta50.controlsCount)
        assertEquals(24, ta50.waypoints.size)
        assertEquals(6, ta50.waypoints.count { it.kind == WaypointKind.CONTROL })
    }

    @Test
    fun controls_areOrdered1toN_withGeoPoints() {
        val ordinals = ta33.waypoints
            .filter { it.kind == WaypointKind.CONTROL }
            .map { it.controlOrdinal }
        assertEquals(listOf(1, 2, 3, 4, 5), ordinals)
        ta33.waypoints.filter { it.kind == WaypointKind.CONTROL }.forEach {
            assertNotNull(it.location, "control ${it.name} must carry a GeoPoint")
        }
    }

    @Test
    fun nonControlWaypoints_haveNoOrdinal() {
        ta33.waypoints.filter { it.kind != WaypointKind.CONTROL }.forEach {
            assertNull(it.controlOrdinal)
        }
    }

    @Test
    fun waypointIndices_areSequentialFromZero() {
        listOf(ta33, ta50).forEach { route ->
            assertEquals(route.waypoints.indices.toList(), route.waypoints.map { it.index })
        }
    }

    @Test
    fun km_isMonotonicNonDecreasing_startsAtZero_endsAtDistance() {
        listOf(ta33, ta50).forEach { route ->
            val km = route.waypoints.map { it.km }
            assertEquals(0.0, km.first())
            assertEquals(route.distanceKm, km.last())
            assertEquals(WaypointKind.START, route.waypoints.first().kind)
            assertEquals(WaypointKind.FINISH, route.waypoints.last().kind)
            km.zipWithNext().forEach { (a, b) -> assertTrue(b >= a, "km must not decrease: $a -> $b") }
        }
    }

    @Test
    fun elevation_lowBelowHigh_andTicksMatchDesign() {
        assertTrue(ta33.elevation.lowMeters < ta33.elevation.highMeters)
        assertEquals(462, ta33.elevation.lowMeters)
        assertEquals(727, ta33.elevation.highMeters)
        assertEquals(15, ta33.elevation.pointsNormalized.size)
        assertEquals(listOf(8, 16, 24), ta33.elevation.tickKm)

        assertTrue(ta50.elevation.lowMeters < ta50.elevation.highMeters)
        assertEquals(452, ta50.elevation.lowMeters)
        assertEquals(19, ta50.elevation.pointsNormalized.size)
        assertEquals(listOf(15, 30, 45), ta50.elevation.tickKm)
        ta33.elevation.pointsNormalized.forEach { assertTrue(it in 0.0..1.0) }
        ta50.elevation.pointsNormalized.forEach { assertTrue(it in 0.0..1.0) }
    }

    @Test
    fun cycloWaypoints_carryMarkNumber() {
        val cyclo = ta33.waypoints.filter { it.mark == TrailMark.CYKLO }
        assertTrue(cyclo.isNotEmpty())
        cyclo.forEach { assertEquals("4036", it.markNumber) }
        // TA50 uses a non-numeric cyclo label too.
        assertTrue(ta50.waypoints.any { it.mark == TrailMark.CYKLO && it.markNumber == "Z.M." })
    }

    @Test
    fun sampleRow_marksAndDirectionsPortedFaithfully() {
        val bischofstein = ta33.waypoints.first { it.name == "Zámek Bischofstein" }
        assertEquals(WaypointKind.CONTROL, bischofstein.kind)
        assertEquals(TrailMark.ZLUTA, bischofstein.mark)
        assertEquals(TurnDirection.UP, bischofstein.direction)
        assertEquals(5.9, bischofstein.km)

        val adrspachZamek = ta33.waypoints.first { it.name == "Adršpach – Zámek" }
        assertEquals(TurnDirection.LEFT_UP, adrspachZamek.direction)
        assertEquals(TrailMark.CERVENA, adrspachZamek.mark)
    }
}
