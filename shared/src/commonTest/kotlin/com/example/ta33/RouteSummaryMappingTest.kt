package com.example.ta33

import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.Route
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RouteSummaryMappingTest {

    private fun control(id: String, routeId: String, ordinal: Int) = ControlPoint(
        id = id, routeId = routeId, ordinal = ordinal, name = "K$ordinal", location = GeoPoint(50.0, 16.0),
    )

    @Test
    fun routeWithNoControls_controlCountZero() = runTest {
        val repo = FakeRouteRepository()
        repo.upsertRoute(Route(id = "r1", name = "A", distanceKm = 33.0), emptyList())

        val summaries = repo.observeRouteSummaries().first()

        assertEquals(1, summaries.size)
        assertEquals(0, summaries.first().controlCount)
        assertEquals(33.0, summaries.first().distanceKm)
    }

    @Test
    fun controlCountAndOrderingByName() = runTest {
        val repo = FakeRouteRepository()
        repo.upsertRoute(
            route = Route(id = "rB", name = "Beta", distanceKm = 22.0),
            controls = listOf(control("c1", "rB", 1), control("c2", "rB", 2)),
        )
        repo.upsertRoute(
            route = Route(id = "rA", name = "Alfa", distanceKm = 33.0),
            controls = listOf(control("c3", "rA", 1)),
        )

        val summaries = repo.observeRouteSummaries().first()

        assertEquals(listOf("Alfa", "Beta"), summaries.map { it.name })
        assertEquals(1, summaries[0].controlCount)
        assertEquals(2, summaries[1].controlCount)
    }
}
