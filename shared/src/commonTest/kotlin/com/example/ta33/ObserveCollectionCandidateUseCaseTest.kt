package com.example.ta33

import app.cash.turbine.turbineScope
import com.example.ta33.domain.geo.ProximityEvaluator
import com.example.ta33.domain.model.CollectedControl
import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.GeofenceConfig
import com.example.ta33.domain.model.GeoPosition
import com.example.ta33.domain.model.Route
import com.example.ta33.domain.model.RunSession
import com.example.ta33.domain.usecase.ObserveCollectionCandidateUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveCollectionCandidateUseCaseTest {

    private val runId = "run-1"
    private val routeId = "r1"
    private val base = GeoPoint(50.6100, 16.1100)

    private lateinit var stream: MutableLocationStream
    private lateinit var routes: FakeRouteRepository
    private lateinit var runs: FakeRunRepository
    private lateinit var useCase: ObserveCollectionCandidateUseCase

    private fun pos(latDelta: Double = 0.0, accuracy: Double = 5.0) =
        GeoPosition(GeoPoint(50.6100 + latDelta, 16.1100), accuracyMeters = accuracy, timestampMillis = 0L)

    @BeforeTest
    fun setup() = runTest {
        stream = MutableLocationStream()
        routes = FakeRouteRepository()
        runs = FakeRunRepository()
        routes.upsertRoute(
            route = Route(id = routeId, name = "A", distanceKm = 33.0),
            controls = listOf(ControlPoint(id = "c1", routeId = routeId, ordinal = 1, name = "K1", location = base)),
        )
        useCase = ObserveCollectionCandidateUseCase(
            stream, routes, runs, ProximityEvaluator(GeofenceConfig(minConsecutiveFixes = 2)),
        )
    }

    private fun activeRun() = RunSession(runId, routeId, "p", startedAtMillis = 1_000L, finishedAtMillis = null)

    @Test
    fun runNotStarted_neverOffers() = runTest {
        runs.seedRun(RunSession(runId, routeId, "p", startedAtMillis = null))
        turbineScope {
            val candidates = useCase(runId, routeId).testIn(backgroundScope)
            runCurrent() // subscribe before emitting

            stream.emit(pos(latDelta = 0.00027)) // inside the radius, but the run has not started
            assertNull(candidates.awaitItem())
            stream.emit(pos(latDelta = 0.00027))
            candidates.expectNoEvents() // gate holds null; distinctUntilChanged suppresses duplicates
            candidates.cancel()
        }
    }

    @Test
    fun runActive_offersAfterDebounce_thenFlipsNullOnCollect() = runTest {
        runs.seedRun(activeRun())
        turbineScope {
            val candidates = useCase(runId, routeId).testIn(backgroundScope)
            runCurrent()

            stream.emit(pos(latDelta = 0.00135)) // far
            assertNull(candidates.awaitItem())
            stream.emit(pos(latDelta = 0.00027)) // near, streak 1 (still null, suppressed)
            stream.emit(pos(latDelta = 0.00027)) // near, streak 2 -> offered
            assertEquals("c1", candidates.awaitItem()?.control?.id)

            // Persist the collection -> observeCollected re-emits -> candidate flips to null.
            runs.addCollected(CollectedControl("cc-1", runId, "c1", collectedAtMillis = 2_000L))
            assertNull(candidates.awaitItem())
            candidates.cancel()
        }
    }

    @Test
    fun runFinished_neverOffers() = runTest {
        runs.seedRun(activeRun().copy(finishedAtMillis = 5_000L))
        turbineScope {
            val candidates = useCase(runId, routeId).testIn(backgroundScope)
            runCurrent()

            stream.emit(pos(latDelta = 0.00027))
            assertNull(candidates.awaitItem())
            stream.emit(pos(latDelta = 0.00027))
            candidates.expectNoEvents()
            candidates.cancel()
        }
    }

    @Test
    fun repeatedInRangeFixes_emitCandidateOnce() = runTest {
        runs.seedRun(activeRun())
        turbineScope {
            val candidates = useCase(runId, routeId).testIn(backgroundScope)
            runCurrent()

            repeat(5) { stream.emit(pos(latDelta = 0.00027)) }

            assertNull(candidates.awaitItem())                          // streak 1
            assertEquals("c1", candidates.awaitItem()?.control?.id)     // streak 2 -> offered
            candidates.expectNoEvents()                                 // further identical fixes suppressed
            candidates.cancel()
        }
    }
}
