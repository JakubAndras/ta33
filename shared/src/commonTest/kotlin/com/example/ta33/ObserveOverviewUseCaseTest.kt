package com.example.ta33

import app.cash.turbine.turbineScope
import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.Route
import com.example.ta33.domain.model.RunSession
import com.example.ta33.domain.usecase.ObserveAppStateUseCase
import com.example.ta33.domain.usecase.ObserveOverviewUseCase
import com.example.ta33.domain.usecase.ObservePreparationStateUseCase
import com.example.ta33.domain.usecase.ObserveRunLogUseCase
import com.example.ta33.presentation.navigation.StartDestinationResolver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveOverviewUseCaseTest {

    private val routeId = "r1"

    private lateinit var routeRepo: FakeRouteRepository
    private lateinit var runRepo: FakeRunRepository
    private lateinit var prefsRepo: FakeAppPreferencesRepository
    private lateinit var prepRepo: FakePreparationRepository
    private lateinit var useCase: ObserveOverviewUseCase

    private fun control(n: Int) = ControlPoint(
        id = "c$n", routeId = routeId, ordinal = n, name = "K$n", location = GeoPoint(50.0 + n, 16.0),
    )

    private fun build() {
        routeRepo = FakeRouteRepository()
        runRepo = FakeRunRepository()
        prefsRepo = FakeAppPreferencesRepository()
        prepRepo = FakePreparationRepository()
        useCase = ObserveOverviewUseCase(
            observeAppState = ObserveAppStateUseCase(routeRepo, runRepo, prefsRepo, prepRepo, StartDestinationResolver()),
            routes = routeRepo,
            observeRunLog = ObserveRunLogUseCase(routeRepo, runRepo),
            observePreparation = ObservePreparationStateUseCase(prepRepo),
        )
    }

    @Test
    fun noActiveRun_progressNull_thenRunStarts_progressAppears_thenClears() = runTest {
        build()
        routeRepo.upsertRoute(Route(id = routeId, name = "A", distanceKm = 33.0), (1..5).map { control(it) })

        turbineScope {
            val states = useCase().testIn(backgroundScope)
            runCurrent()

            val initial = states.expectMostRecentItem()
            assertEquals(routeId, initial.activeRoute?.routeId)
            assertFalse(initial.hasActiveRun)
            assertNull(initial.progress)

            // Run starts: flatMapLatest binds the run log, progress appears.
            runRepo.seedRun(RunSession(id = "run-1", routeId = routeId, participantId = "p1", startedAtMillis = 1_000L))
            runCurrent()
            val active = states.expectMostRecentItem()
            assertTrue(active.hasActiveRun)
            assertEquals(0, active.progress?.collectedCount)
            assertEquals(5, active.progress?.totalCount)

            // Run ends: activeRunId clears, flatMapLatest falls back to flowOf(null).
            runRepo.setFinished("run-1", finishedAtMillis = 2_000L)
            runCurrent()
            val ended = states.expectMostRecentItem()
            assertFalse(ended.hasActiveRun)
            assertNull(ended.progress)

            states.cancelAndIgnoreRemainingEvents()
        }
    }
}
