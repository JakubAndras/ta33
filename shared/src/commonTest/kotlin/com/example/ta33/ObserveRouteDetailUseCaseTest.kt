package com.example.ta33

import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.Route
import com.example.ta33.domain.usecase.ObserveRouteDetailUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObserveRouteDetailUseCaseTest {

    private fun control(id: String, routeId: String, ordinal: Int) = ControlPoint(
        id = id, routeId = routeId, ordinal = ordinal, name = "K$ordinal", location = GeoPoint(50.0, 16.0),
    )

    @Test
    fun controlsSortedByOrdinal_distancePassedThrough() = runTest {
        val repo = FakeRouteRepository()
        repo.upsertRoute(
            route = Route(id = "r1", name = "A", distanceKm = 33.0),
            controls = listOf(
                control("c3", "r1", 3),
                control("c1", "r1", 1),
                control("c2", "r1", 2),
            ),
        )
        val useCase = ObserveRouteDetailUseCase(repo)

        val detail = useCase("r1").first()

        assertEquals("r1", detail?.routeId)
        assertEquals(33.0, detail?.distanceKm)
        assertEquals(listOf(1, 2, 3), detail?.controls?.map { it.ordinal })
        assertEquals(3, detail?.controlCount)
    }

    @Test
    fun unknownRoute_emitsNull() = runTest {
        val repo = FakeRouteRepository()
        val useCase = ObserveRouteDetailUseCase(repo)

        assertNull(useCase("nope").first())
    }
}
