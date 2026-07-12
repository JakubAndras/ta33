package com.example.ta33

import com.example.ta33.data.content.RouteCatalog
import com.example.ta33.data.repository.StaticRouteCatalogRepository
import com.example.ta33.domain.model.CollectedControl
import com.example.ta33.domain.model.Route
import com.example.ta33.domain.model.RunSession
import com.example.ta33.domain.model.StopStatus
import com.example.ta33.domain.model.WaypointKind
import com.example.ta33.domain.usecase.ObserveAppStateUseCase
import com.example.ta33.presentation.DenikViewModel
import com.example.ta33.presentation.navigation.StartDestinationResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DenikViewModelTest {

    private val ta33 = RouteCatalog.TA33_ROUTE_ID
    private val runId = "run-1"

    private lateinit var routeRepo: FakeRouteRepository
    private lateinit var runRepo: FakeRunRepository
    private lateinit var prefsRepo: FakeAppPreferencesRepository
    private lateinit var prepRepo: FakePreparationRepository

    private fun observeApp() =
        ObserveAppStateUseCase(routeRepo, runRepo, prefsRepo, prepRepo, StartDestinationResolver())

    private fun vm() = DenikViewModel(observeApp(), runRepo, StaticRouteCatalogRepository())

    private fun controlId(ordinal: Int) = "$ta33-kp$ordinal"

    @BeforeTest
    fun setup() {
        routeRepo = FakeRouteRepository()
        runRepo = FakeRunRepository()
        prefsRepo = FakeAppPreferencesRepository(initial = ta33)
        prepRepo = FakePreparationRepository()
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Seed a READY app state with an active started run on TA33 (mirrors DevSeed). */
    private suspend fun seedActiveRun() {
        routeRepo.upsertRoute(Route(id = ta33, name = "TA33", distanceKm = 33.2), emptyList())
        runRepo.seedRun(RunSession(id = runId, routeId = ta33, participantId = "p1", startedAtMillis = 1_000L))
    }

    private fun TestScope.installMain() = Dispatchers.setMain(StandardTestDispatcher(testScheduler))

    @Test
    fun activeRun_twoCollected_derivesDoneNextUpcomingAndStart() = runTest {
        installMain()
        seedActiveRun()
        runRepo.addCollected(CollectedControl("cc1", runId, controlId(1), 2_000L))
        runRepo.addCollected(CollectedControl("cc2", runId, controlId(2), 3_000L))

        val vm = vm()
        advanceUntilIdle()
        val state = vm.state.value

        assertEquals("TA33", state.shortId)
        assertEquals(33.2, state.distanceKm)
        assertEquals(1_000L, state.startTimeMillis)
        assertNull(state.finishTimeMillis)
        assertEquals(740, state.ascentMeters)
        assertTrue(state.canSwitch) // TA33 + TA50 in the catalog

        val start = state.stops.first()
        assertEquals(WaypointKind.START, start.kind)
        assertEquals(StopStatus.DONE, start.status)
        assertEquals(1_000L, start.startTimeMillis)

        val controls = state.stops.filter { it.kind == WaypointKind.CONTROL }
        assertEquals(StopStatus.DONE, controls[0].status)
        assertEquals(StopStatus.DONE, controls[1].status)
        assertEquals(StopStatus.NEXT, controls[2].status)
        assertTrue(controls.drop(3).all { it.status == StopStatus.UPCOMING })

        val finish = state.stops.last()
        assertTrue(finish.isFinish)
        assertEquals(StopStatus.UPCOMING, finish.status)
    }

    @Test
    fun segmentKm_setForEveryStopExceptLast() = runTest {
        installMain()
        seedActiveRun()

        val vm = vm()
        advanceUntilIdle()
        val stops = vm.state.value.stops

        assertTrue(stops.size >= 3)
        assertNull(stops.last().segmentKm)
        assertTrue(stops.dropLast(1).all { it.segmentKm != null })
        // START at 0.0 km → first control at 5.9 km ⇒ segment ≈ 5.9.
        assertEquals(5.9, stops[0].segmentKm!!, 0.0001)
    }

    @Test
    fun toggle_switchesToOtherRouteAsPreview() = runTest {
        installMain()
        seedActiveRun()

        val vm = vm()
        advanceUntilIdle()
        assertEquals("TA33", vm.state.value.shortId)

        vm.toggle()
        advanceUntilIdle()
        val state = vm.state.value

        assertEquals("TA50", state.shortId)
        // Not the active run's route → preview: start time gone, all stops UPCOMING.
        assertNull(state.startTimeMillis)
        assertTrue(state.stops.all { it.status == StopStatus.UPCOMING })
    }

    @Test
    fun noRun_previewAllUpcoming() = runTest {
        installMain()
        // No run seeded; prefs still point at TA33.
        routeRepo.upsertRoute(Route(id = ta33, name = "TA33", distanceKm = 33.2), emptyList())

        val vm = vm()
        advanceUntilIdle()
        val state = vm.state.value

        assertNull(state.startTimeMillis)
        assertTrue(state.stops.isNotEmpty())
        assertTrue(state.stops.all { it.status == StopStatus.UPCOMING })
    }
}
