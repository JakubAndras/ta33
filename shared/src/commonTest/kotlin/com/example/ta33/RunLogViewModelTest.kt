package com.example.ta33

import com.example.ta33.domain.model.CollectedControl
import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.ControlPointState
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.Route
import com.example.ta33.domain.model.RunSession
import com.example.ta33.domain.usecase.ObserveRunLogUseCase
import com.example.ta33.presentation.RunLogViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RunLogViewModelTest {

    private val routeId = "r1"
    private val runId = "run-1"

    private lateinit var runRepo: FakeRunRepository
    private lateinit var routeRepo: FakeRouteRepository
    private lateinit var observeRunLog: ObserveRunLogUseCase

    private fun control(n: Int) = ControlPoint(
        id = "c$n", routeId = routeId, ordinal = n, name = "K$n", location = GeoPoint(50.0 + n, 16.0),
    )

    @BeforeTest
    fun setup() = runTest {
        runRepo = FakeRunRepository()
        routeRepo = FakeRouteRepository()
        observeRunLog = ObserveRunLogUseCase(routeRepo, runRepo)
        routeRepo.upsertRoute(
            route = Route(id = routeId, name = "A", distanceKm = 33.0),
            controls = (1..5).map { control(it) },
        )
        runRepo.seedRun(RunSession(id = runId, routeId = routeId, participantId = "p1", startedAtMillis = 1_000L))
    }

    private suspend fun collect(controlId: String, at: Long) {
        runRepo.addCollected(
            CollectedControl(id = "cc-$controlId", runSessionId = runId, controlId = controlId, collectedAtMillis = at),
        )
    }

    @Test
    fun useCase_twoOfFive_progressAndStates() = runTest {
        collect("c1", 1_300L)
        collect("c2", 1_600L)

        val state = observeRunLog(runId, routeId).first()

        assertEquals("2 z 5", state.progressLabel)
        assertEquals(2, state.collectedCount)
        assertEquals(5, state.totalCount)
        assertEquals(
            listOf(
                ControlPointState.DONE,
                ControlPointState.DONE,
                ControlPointState.ACTIVE,
                ControlPointState.LOCKED,
                ControlPointState.LOCKED,
            ),
            state.entries.map { it.state },
        )
        assertEquals("c3", state.nextControl?.id)
        // split = collectedAt - startedAt
        assertEquals(300L, state.entries[0].splitMillis)
        assertEquals(600L, state.entries[1].splitMillis)
    }

    @Test
    fun emptyRoute_progressZeroOfZero_noCrash() = runTest {
        routeRepo.upsertRoute(Route(id = "empty", name = "E", distanceKm = 0.0), emptyList())
        runRepo.seedRun(RunSession(id = "run-2", routeId = "empty", participantId = "p1"))
        val state = observeRunLog("run-2", "empty").first()
        assertEquals("0 z 0", state.progressLabel)
        assertEquals(emptyList(), state.entries)
        assertEquals(ControlPointState.LOCKED, state.finishState)
    }

    @Test
    fun viewModel_emitsStateOnCollection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = RunLogViewModel(observeRunLog)
            vm.bind(runId, routeId)
            advanceUntilIdle()
            assertEquals("0 z 5", vm.state.value.progressLabel)
            assertEquals(false, vm.state.value.loading)
            assertEquals("c1", vm.state.value.nextControl?.id)

            collect("c1", 1_300L)
            advanceUntilIdle()
            assertEquals("1 z 5", vm.state.value.progressLabel)
            assertEquals(ControlPointState.DONE, vm.state.value.entries[0].state)
            assertEquals("c2", vm.state.value.nextControl?.id)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
