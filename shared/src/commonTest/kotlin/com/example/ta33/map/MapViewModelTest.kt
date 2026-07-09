package com.example.ta33.map

import com.example.ta33.FakeLocationStream
import com.example.ta33.FakeRouteRepository
import com.example.ta33.FakeRunRepository
import com.example.ta33.FakeTrackpointRepository
import com.example.ta33.domain.map.MapTileConfig
import com.example.ta33.domain.map.TileStore
import com.example.ta33.domain.model.CollectedControl
import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.ControlPointState
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.GeoPosition
import com.example.ta33.domain.model.MapTileSource
import com.example.ta33.domain.model.MapTileSourceState
import com.example.ta33.domain.model.Route
import com.example.ta33.domain.model.RunSession
import com.example.ta33.domain.model.TileFormat
import com.example.ta33.domain.model.Trackpoint
import com.example.ta33.domain.usecase.ObserveRouteDetailUseCase
import com.example.ta33.domain.usecase.ObserveRunLogUseCase
import com.example.ta33.domain.usecase.ObserveTrackUseCase
import com.example.ta33.presentation.MapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private val routeId = "r1"
    private val runId = "run-1"
    private val c1 = ControlPoint("c1", routeId, ordinal = 1, name = "K1", location = GeoPoint(50.10, 16.10))
    private val c2 = ControlPoint("c2", routeId, ordinal = 2, name = "K2", location = GeoPoint(50.20, 16.20))

    private class FakeTileStore(state: MapTileSourceState) : TileStore {
        private val flow = MutableStateFlow(state)
        override fun observeTileSource(): Flow<MapTileSourceState> = flow.asStateFlow()
        override suspend fun resolveTileSource(): MapTileSourceState = flow.value
    }

    private lateinit var routes: FakeRouteRepository
    private lateinit var runs: FakeRunRepository
    private lateinit var trackpoints: FakeTrackpointRepository

    @BeforeTest
    fun setup() = runTest {
        Dispatchers.setMain(StandardTestDispatcher())
        routes = FakeRouteRepository()
        runs = FakeRunRepository()
        trackpoints = FakeTrackpointRepository()
        routes.upsertRoute(Route(id = routeId, name = "A", distanceKm = 33.0), listOf(c2, c1))
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        tile: MapTileSourceState = MapTileSourceState.NotDownloaded,
        locations: List<GeoPosition> = emptyList(),
    ) = MapViewModel(
        tileStore = FakeTileStore(tile),
        observeRouteDetail = ObserveRouteDetailUseCase(routes),
        observeRunLog = ObserveRunLogUseCase(routes, runs),
        observeTrack = ObserveTrackUseCase(trackpoints),
        locationStream = FakeLocationStream(locations),
        config = MapTileConfig(),
    )

    @Test
    fun runIdNull_derivesStatesAndEmptyBreadcrumb() = runTest {
        val vm = viewModel(tile = MapTileSourceState.Ready(MapTileSource("adrspach", "/fake/tiles/adrspach.mbtiles", TileFormat.MBTILES)))
        vm.bind(routeId, runId = null)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.loading)
        assertIs<MapTileSourceState.Ready>(state.tileSource)
        assertTrue(state.isRouteLoaded)
        assertEquals(listOf(c1.location, c2.location), state.overlay.routePolyline)
        val markers = state.overlay.checkpointMarkers
        assertEquals(ControlPointState.ACTIVE, markers.first { it.controlId == "c1" }.state)
        assertTrue(markers.first { it.controlId == "c1" }.isNext)
        assertEquals(ControlPointState.LOCKED, markers.first { it.controlId == "c2" }.state)
        assertTrue(state.overlay.breadcrumb.isEmpty())
        assertNull(state.overlay.livePosition)
    }

    @Test
    fun runIdSet_usesRealRunStatesAndTrack() = runTest {
        runs.seedRun(RunSession(runId, routeId, "p", startedAtMillis = 1_000L))
        runs.addCollected(CollectedControl("cc", runId, "c1", collectedAtMillis = 2_000L))
        trackpoints.seed(Trackpoint("t1", runId, GeoPoint(50.15, 16.15), 5.0, 1_500L))
        val vm = viewModel()
        vm.bind(routeId, runId)
        advanceUntilIdle()

        val state = vm.state.value
        val markers = state.overlay.checkpointMarkers
        assertEquals(ControlPointState.DONE, markers.first { it.controlId == "c1" }.state)
        assertEquals(ControlPointState.ACTIVE, markers.first { it.controlId == "c2" }.state)
        assertEquals(listOf(GeoPoint(50.15, 16.15)), state.overlay.breadcrumb)
    }

    @Test
    fun onMarkerTapped_setsSelectedMarkerId() = runTest {
        val vm = viewModel()
        vm.bind(routeId, runId = null)
        advanceUntilIdle()

        vm.onMarkerTapped(GeoPoint(50.10001, 16.10001), maxMeters = 60.0)
        advanceUntilIdle()
        assertEquals("c1", vm.state.value.selectedMarkerId)

        vm.clearSelection()
        advanceUntilIdle()
        assertNull(vm.state.value.selectedMarkerId)
    }

    @Test
    fun onMarkerTapped_farFromAnyControl_selectsNull() = runTest {
        val vm = viewModel()
        vm.bind(routeId, runId = null)
        advanceUntilIdle()

        vm.onMarkerTapped(GeoPoint(48.0, 14.0), maxMeters = 60.0)
        advanceUntilIdle()
        assertNull(vm.state.value.selectedMarkerId)
    }

    @Test
    fun livePosition_surfacedWhenAvailable() = runTest {
        val fix = GeoPosition(GeoPoint(50.15, 16.15), accuracyMeters = 5.0, timestampMillis = 100L)
        val vm = viewModel(locations = listOf(fix))
        vm.bind(routeId, runId = null)
        advanceUntilIdle()

        assertEquals(fix, vm.state.value.overlay.livePosition)
        assertEquals(fix.location, vm.state.value.camera?.focus)
    }
}
