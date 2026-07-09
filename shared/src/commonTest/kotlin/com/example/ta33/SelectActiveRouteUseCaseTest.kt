package com.example.ta33

import com.example.ta33.domain.model.Route
import com.example.ta33.domain.usecase.SelectActiveRouteUseCase
import com.example.ta33.domain.usecase.SelectRouteResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SelectActiveRouteUseCaseTest {

    @Test
    fun knownRoute_persistsSelection_returnsSelected() = runTest {
        val routes = FakeRouteRepository()
        routes.upsertRoute(Route(id = "r1", name = "A", distanceKm = 33.0), emptyList())
        val prefs = FakeAppPreferencesRepository()
        val useCase = SelectActiveRouteUseCase(routes, prefs)

        val result = useCase("r1")

        assertEquals(SelectRouteResult.Selected, result)
        assertEquals("r1", prefs.getSelectedRouteId())
    }

    @Test
    fun unknownRoute_returnsUnknown_prefsUntouched() = runTest {
        val routes = FakeRouteRepository()
        routes.upsertRoute(Route(id = "r1", name = "A", distanceKm = 33.0), emptyList())
        val prefs = FakeAppPreferencesRepository()
        val useCase = SelectActiveRouteUseCase(routes, prefs)

        val result = useCase("nope")

        assertEquals(SelectRouteResult.UnknownRoute, result)
        assertNull(prefs.getSelectedRouteId())
    }
}
