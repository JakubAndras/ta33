package com.example.ta33

import com.example.ta33.domain.model.Route
import com.example.ta33.domain.model.RunSession
import com.example.ta33.domain.usecase.ObserveAppStateUseCase
import com.example.ta33.presentation.navigation.AppReadiness
import com.example.ta33.presentation.navigation.StartDestinationResolver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveAppStateUseCaseTest {

    private fun useCase(
        routeRepo: FakeRouteRepository,
        runRepo: FakeRunRepository,
        prefsRepo: FakeAppPreferencesRepository,
        prepRepo: FakePreparationRepository,
    ) = ObserveAppStateUseCase(routeRepo, runRepo, prefsRepo, prepRepo, StartDestinationResolver())

    @Test
    fun noContent_notReady() = runTest {
        val state = useCase(
            FakeRouteRepository(), FakeRunRepository(), FakeAppPreferencesRepository(), FakePreparationRepository(),
        )().first()

        assertEquals(AppReadiness.NOT_READY, state.readiness)
        assertEquals(null, state.activeRouteId)
        assertEquals(null, state.activeRunId)
    }

    @Test
    fun routeAndActiveRun_readyWithIds() = runTest {
        val routeRepo = FakeRouteRepository()
        val runRepo = FakeRunRepository()
        routeRepo.upsertRoute(Route(id = "r1", name = "A", distanceKm = 33.0), emptyList())
        runRepo.seedRun(RunSession(id = "run-1", routeId = "r1", participantId = "p1", startedAtMillis = 1_000L))

        val state = useCase(routeRepo, runRepo, FakeAppPreferencesRepository(), FakePreparationRepository())().first()

        assertEquals(AppReadiness.READY, state.readiness)
        assertEquals("r1", state.activeRouteId)
        assertEquals("run-1", state.activeRunId)
    }
}
