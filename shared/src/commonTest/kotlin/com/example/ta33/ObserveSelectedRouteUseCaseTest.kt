package com.example.ta33

import com.example.ta33.domain.model.Route
import com.example.ta33.domain.usecase.ObserveSelectedRouteUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObserveSelectedRouteUseCaseTest {

    @Test
    fun persistedIdPresentAndAvailable_returnsThatId() = runTest {
        val routes = FakeRouteRepository()
        routes.upsertRoute(Route(id = "r1", name = "A", distanceKm = 33.0), emptyList())
        routes.upsertRoute(Route(id = "r2", name = "B", distanceKm = 22.0), emptyList())
        val prefs = FakeAppPreferencesRepository(initial = "r2")
        val useCase = ObserveSelectedRouteUseCase(routes, prefs)

        assertEquals("r2", useCase().first())
    }

    @Test
    fun persistedIdForRemovedRoute_ambiguous_fallsBackToNull() = runTest {
        val routes = FakeRouteRepository()
        routes.upsertRoute(Route(id = "r1", name = "A", distanceKm = 33.0), emptyList())
        routes.upsertRoute(Route(id = "r2", name = "B", distanceKm = 22.0), emptyList())
        val prefs = FakeAppPreferencesRepository(initial = "gone")
        val useCase = ObserveSelectedRouteUseCase(routes, prefs)

        assertNull(useCase().first())
    }

    @Test
    fun noPersistedId_singleRoute_returnsThatRoute() = runTest {
        val routes = FakeRouteRepository()
        routes.upsertRoute(Route(id = "only", name = "A", distanceKm = 33.0), emptyList())
        val prefs = FakeAppPreferencesRepository(initial = null)
        val useCase = ObserveSelectedRouteUseCase(routes, prefs)

        assertEquals("only", useCase().first())
    }
}
