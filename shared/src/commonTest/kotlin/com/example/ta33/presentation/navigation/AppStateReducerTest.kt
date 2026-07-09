package com.example.ta33.presentation.navigation

import com.example.ta33.domain.model.PreparationState
import com.example.ta33.domain.model.PreparationStatus
import com.example.ta33.domain.model.Route
import com.example.ta33.domain.model.RunSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppStateReducerTest {

    private val resolver = StartDestinationResolver()
    private val notStarted = PreparationState()

    private fun route(id: String) = Route(id = id, name = id, distanceKm = 33.0)

    @Test
    fun emptyRoutes_notReady_gateAtPreparation() {
        val state = AppStateReducer.reduce(
            routes = emptyList(),
            activeRun = null,
            selectedRouteId = null,
            preparation = notStarted,
            resolver = resolver,
        )

        assertEquals(AppReadiness.NOT_READY, state.readiness)
        assertEquals(ContentAvailability.ABSENT, state.contentAvailability)
        assertEquals(Destination.Preparation, state.startDestination)
        assertNull(state.activeRouteId)
        assertNull(state.activeRunId)
    }

    @Test
    fun oneRoute_noActiveRun_readyMainDenik_routeDerived() {
        val state = AppStateReducer.reduce(
            routes = listOf(route("r1")),
            activeRun = null,
            selectedRouteId = null,
            preparation = notStarted,
            resolver = resolver,
        )

        assertEquals(AppReadiness.READY, state.readiness)
        assertEquals(ContentAvailability.PRESENT, state.contentAvailability)
        assertEquals(Destination.Main(TopLevelDestination.DENIK), state.startDestination)
        assertEquals("r1", state.activeRouteId)
        assertNull(state.activeRunId)
    }

    @Test
    fun twoRoutes_noActiveRun_activeRouteAmbiguousNull() {
        val state = AppStateReducer.reduce(
            routes = listOf(route("r1"), route("r2")),
            activeRun = null,
            selectedRouteId = null,
            preparation = notStarted,
            resolver = resolver,
        )

        assertEquals(AppReadiness.READY, state.readiness)
        assertNull(state.activeRouteId)
        assertEquals(Destination.Main(TopLevelDestination.DENIK), state.startDestination)
    }

    @Test
    fun activeRun_resumesRunActive_routeFromRun() {
        val run = RunSession(id = "run-1", routeId = "r2", participantId = "p1", startedAtMillis = 1_000L)
        val state = AppStateReducer.reduce(
            routes = listOf(route("r1"), route("r2")),
            activeRun = run,
            selectedRouteId = null,
            preparation = notStarted,
            resolver = resolver,
        )

        assertEquals(AppReadiness.READY, state.readiness)
        assertEquals(Destination.RunActive("run-1"), state.startDestination)
        assertEquals("run-1", state.activeRunId)
        assertEquals("r2", state.activeRouteId)
    }

    @Test
    fun twoRoutes_persistedSelection_selectionBecomesActive() {
        val state = AppStateReducer.reduce(
            routes = listOf(route("r1"), route("r2")),
            activeRun = null,
            selectedRouteId = "r2",
            preparation = notStarted,
            resolver = resolver,
        )

        assertEquals("r2", state.activeRouteId)
    }

    @Test
    fun activeRun_overridesPersistedSelection() {
        val run = RunSession(id = "run-1", routeId = "r1", participantId = "p1", startedAtMillis = 1_000L)
        val state = AppStateReducer.reduce(
            routes = listOf(route("r1"), route("r2")),
            activeRun = run,
            selectedRouteId = "r2",
            preparation = notStarted,
            resolver = resolver,
        )

        assertEquals("r1", state.activeRouteId)
    }

    @Test
    fun staleSelection_ignored_ambiguousNull() {
        val state = AppStateReducer.reduce(
            routes = listOf(route("r1"), route("r2")),
            activeRun = null,
            selectedRouteId = "gone",
            preparation = notStarted,
            resolver = resolver,
        )

        assertNull(state.activeRouteId)
    }

    @Test
    fun preparing_readinessPreparing_gatesAtPreparation() {
        val state = AppStateReducer.reduce(
            routes = emptyList(),
            activeRun = null,
            selectedRouteId = null,
            preparation = PreparationState(status = PreparationStatus.PREPARING, manifestVersion = 1),
            resolver = resolver,
        )

        assertEquals(AppReadiness.PREPARING, state.readiness)
        assertEquals(Destination.Preparation, state.startDestination)
    }

    @Test
    fun ready_readinessReady_contentPresent_evenWithoutRoutesInList() {
        val state = AppStateReducer.reduce(
            routes = listOf(route("r1")),
            activeRun = null,
            selectedRouteId = null,
            preparation = PreparationState(status = PreparationStatus.READY, manifestVersion = 1, readyAtMillis = 10L),
            resolver = resolver,
        )

        assertEquals(AppReadiness.READY, state.readiness)
        assertEquals(ContentAvailability.PRESENT, state.contentAvailability)
        assertEquals(Destination.Main(TopLevelDestination.DENIK), state.startDestination)
    }
}
