package com.example.ta33.presentation

import com.example.ta33.FakeLocationStream
import com.example.ta33.FakePermissionController
import com.example.ta33.FakeTrackingController
import com.example.ta33.FakeTrackpointRepository
import com.example.ta33.SeqIdGenerator
import com.example.ta33.domain.geo.BreadcrumbThrottle
import com.example.ta33.domain.model.BreadcrumbConfig
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.GeoPosition
import com.example.ta33.domain.model.LocationPermissionStatus
import com.example.ta33.domain.usecase.ObserveTrackUseCase
import com.example.ta33.domain.usecase.RecordBreadcrumbUseCase
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LiveLocationViewModelTest {

    private val config = BreadcrumbConfig(minDistanceMeters = 10.0, minTimeMillis = 5_000, maxAccuracyMeters = 50.0)

    private lateinit var stream: FakeLocationStream
    private lateinit var repo: FakeTrackpointRepository
    private lateinit var permission: FakePermissionController
    private lateinit var tracking: FakeTrackingController

    private fun pos(latDelta: Double = 0.0, t: Long) =
        GeoPosition(GeoPoint(50.0 + latDelta, 16.0), 5.0, t)

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        stream = FakeLocationStream(listOf(pos(t = 0L), pos(latDelta = 0.0001, t = 6_000L)))
        repo = FakeTrackpointRepository()
        permission = FakePermissionController(LocationPermissionStatus.GRANTED_WHEN_IN_USE)
        tracking = FakeTrackingController()
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): LiveLocationViewModel {
        val record = RecordBreadcrumbUseCase(stream, repo, BreadcrumbThrottle(config), SeqIdGenerator("tp-"))
        return LiveLocationViewModel(stream, permission, record, ObserveTrackUseCase(repo), tracking)
    }

    @Test
    fun bind_surfacesPermissionCurrentPositionAndTrack_andStartsTracking() = runTest {
        val vm = viewModel()
        vm.bind("run-1")
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(LocationPermissionStatus.GRANTED_WHEN_IN_USE, state.permissionStatus)
        assertEquals(pos(latDelta = 0.0001, t = 6_000L), state.currentPosition)
        assertTrue(state.isTracking)
        assertEquals(1, tracking.startCalls)
        assertTrue(state.track.isNotEmpty())
    }

    @Test
    fun stopRecording_stopsTrackingAndClearsFlag() = runTest {
        val vm = viewModel()
        vm.bind("run-1")
        advanceUntilIdle()

        vm.stopRecording()
        advanceUntilIdle()

        assertFalse(vm.state.value.isTracking)
        assertEquals(1, tracking.stopCalls)
    }
}
