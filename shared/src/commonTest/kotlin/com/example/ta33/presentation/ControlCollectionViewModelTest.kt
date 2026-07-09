package com.example.ta33.presentation

import com.example.ta33.FakeRouteRepository
import com.example.ta33.FakeRunRepository
import com.example.ta33.FakePermissionController
import com.example.ta33.MutableLocationStream
import com.example.ta33.MutableTimeProvider
import com.example.ta33.SeqIdGenerator
import com.example.ta33.domain.geo.ProximityEvaluator
import com.example.ta33.domain.model.CollectedControl
import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.GeofenceConfig
import com.example.ta33.domain.model.GeoPosition
import com.example.ta33.domain.model.LocationPermissionStatus
import com.example.ta33.domain.model.Route
import com.example.ta33.domain.model.RunSession
import com.example.ta33.domain.usecase.CollectControlUseCase
import com.example.ta33.domain.usecase.ObserveCollectionCandidateUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ControlCollectionViewModelTest {

    private val runId = "run-1"
    private val routeId = "r1"
    private val base = GeoPoint(50.6100, 16.1100)
    private val far = GeoPoint(50.6300, 16.1500) // > 50 m from base

    private lateinit var stream: MutableLocationStream
    private lateinit var candidateRoutes: FakeRouteRepository
    private lateinit var candidateRuns: FakeRunRepository
    private lateinit var permission: FakePermissionController

    private fun pos(latDelta: Double = 0.0) =
        GeoPosition(GeoPoint(50.6100 + latDelta, 16.1100), accuracyMeters = 5.0, timestampMillis = 0L)

    @BeforeTest
    fun setup() = runTest {
        Dispatchers.setMain(StandardTestDispatcher())
        stream = MutableLocationStream()
        candidateRoutes = FakeRouteRepository()
        candidateRuns = FakeRunRepository()
        permission = FakePermissionController(LocationPermissionStatus.GRANTED_WHEN_IN_USE)
        candidateRoutes.upsertRoute(
            route = Route(id = routeId, name = "A", distanceKm = 33.0),
            controls = listOf(ControlPoint(id = "c1", routeId = routeId, ordinal = 1, name = "K1", location = base)),
        )
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    /**
     * The write path uses a SEPARATE set of fakes so each CollectResult outcome can be driven
     * independently of how the candidate was offered.
     */
    private suspend fun viewModel(
        writeControlLocation: GeoPoint = base,
        preCollected: Boolean = false,
        runStarted: Boolean = true,
    ): ControlCollectionViewModel {
        if (runStarted) {
            candidateRuns.seedRun(RunSession(runId, routeId, "p", startedAtMillis = 1_000L))
        } else {
            candidateRuns.seedRun(RunSession(runId, routeId, "p", startedAtMillis = null))
        }
        val observe = ObserveCollectionCandidateUseCase(
            stream, candidateRoutes, candidateRuns, ProximityEvaluator(GeofenceConfig(minConsecutiveFixes = 1)),
        )
        val writeRoutes = FakeRouteRepository()
        val writeRuns = FakeRunRepository()
        writeRoutes.upsertRoute(
            route = Route(id = routeId, name = "A", distanceKm = 33.0),
            controls = listOf(ControlPoint(id = "c1", routeId = routeId, ordinal = 1, name = "K1", location = writeControlLocation)),
        )
        if (preCollected) {
            writeRuns.addCollected(CollectedControl("seed", runId, "c1", collectedAtMillis = 1L))
        }
        val collect = CollectControlUseCase(writeRuns, writeRoutes, MutableTimeProvider(now = 2_000L), SeqIdGenerator("cc-"))
        return ControlCollectionViewModel(observe, collect, permission)
    }

    @Test
    fun bind_surfacesPermissionAndCandidate() = runTest {
        val vm = viewModel()
        vm.bind(runId, routeId)
        stream.emit(pos(latDelta = 0.00005)) // in range
        advanceUntilIdle()

        assertEquals(LocationPermissionStatus.GRANTED_WHEN_IN_USE, vm.state.value.permissionStatus)
        assertEquals("c1", vm.state.value.candidate?.control?.id)
    }

    @Test
    fun confirm_collected_setsJustCollected() = runTest {
        val vm = viewModel(writeControlLocation = base)
        vm.bind(runId, routeId)
        stream.emit(pos(latDelta = 0.00005))
        advanceUntilIdle()

        vm.confirm()
        advanceUntilIdle()

        val result = assertIs<CollectionOutcome.JustCollected>(vm.state.value.lastResult)
        assertEquals("c1", result.controlId)
        assertFalse(vm.state.value.isCollecting)
    }

    @Test
    fun confirm_alreadyCollected_isReflected() = runTest {
        val vm = viewModel(writeControlLocation = base, preCollected = true)
        vm.bind(runId, routeId)
        stream.emit(pos(latDelta = 0.00005))
        advanceUntilIdle()

        vm.confirm()
        advanceUntilIdle()

        assertEquals(CollectionOutcome.AlreadyCollected, vm.state.value.lastResult)
    }

    @Test
    fun confirm_outOfRange_isReflected() = runTest {
        val vm = viewModel(writeControlLocation = far) // write-time geofence guard rejects the stale offer
        vm.bind(runId, routeId)
        stream.emit(pos(latDelta = 0.00005))
        advanceUntilIdle()

        vm.confirm()
        advanceUntilIdle()

        assertEquals(CollectionOutcome.OutOfRange, vm.state.value.lastResult)
    }

    @Test
    fun confirm_withNoCandidate_isNoOp() = runTest {
        val vm = viewModel(runStarted = false) // run not started => no candidate
        vm.bind(runId, routeId)
        stream.emit(pos(latDelta = 0.00005))
        advanceUntilIdle()

        assertNull(vm.state.value.candidate)
        vm.confirm()
        advanceUntilIdle()

        assertNull(vm.state.value.lastResult)
        assertFalse(vm.state.value.isCollecting)
    }
}
