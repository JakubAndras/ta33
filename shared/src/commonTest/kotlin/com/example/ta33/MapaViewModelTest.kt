package com.example.ta33

import com.example.ta33.data.content.RouteCatalog
import com.example.ta33.data.repository.StaticRouteCatalogRepository
import com.example.ta33.domain.model.Route
import com.example.ta33.domain.usecase.ObserveAppStateUseCase
import com.example.ta33.presentation.MapaViewModel
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MapaViewModelTest {

    private val ta33 = RouteCatalog.TA33_ROUTE_ID

    private lateinit var routeRepo: FakeRouteRepository
    private lateinit var runRepo: FakeRunRepository
    private lateinit var prefsRepo: FakeAppPreferencesRepository
    private lateinit var prepRepo: FakePreparationRepository

    private fun observeApp() =
        ObserveAppStateUseCase(routeRepo, runRepo, prefsRepo, prepRepo, StartDestinationResolver())

    private fun vm() = MapaViewModel(observeApp(), StaticRouteCatalogRepository())

    @BeforeTest
    fun setup() {
        routeRepo = FakeRouteRepository()
        runRepo = FakeRunRepository()
        prefsRepo = FakeAppPreferencesRepository(initial = ta33)
        prepRepo = FakePreparationRepository()
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.installMain() = Dispatchers.setMain(StandardTestDispatcher(testScheduler))

    /** Seed a route so the app state resolves an active route (mirrors DevSeed / prefs selection). */
    private suspend fun seedRoute() {
        routeRepo.upsertRoute(Route(id = ta33, name = "TA33", distanceKm = 33.2), emptyList())
    }

    @Test
    fun defaultsToActiveRoute_loadsMetadataAndWaypoints() = runTest {
        installMain()
        seedRoute()

        val vm = vm()
        advanceUntilIdle()
        val state = vm.state.value

        assertFalse(state.loading)
        assertEquals(ta33, state.routeId)
        assertEquals("TA33", state.shortId)
        assertEquals("A", state.letter)
        assertEquals(33.2, state.distanceKm)
        assertEquals(740, state.ascentMeters)
        assertEquals(5, state.controlsCount)
        assertTrue(state.waypoints.isNotEmpty())
        assertNull(state.highlightedControl)
        assertTrue(state.canSwitch) // TA33 + TA50 in the catalog
    }

    @Test
    fun highlight_setsAndClearsHighlightedControl() = runTest {
        installMain()
        seedRoute()

        val vm = vm()
        advanceUntilIdle()
        assertNull(vm.state.value.highlightedControl)

        vm.highlight(2)
        advanceUntilIdle()
        assertEquals(2, vm.state.value.highlightedControl)

        vm.highlight(null)
        advanceUntilIdle()
        assertNull(vm.state.value.highlightedControl)
    }

    @Test
    fun toggle_switchesRouteAndClearsHighlight() = runTest {
        installMain()
        seedRoute()

        val vm = vm()
        advanceUntilIdle()
        assertEquals("TA33", vm.state.value.shortId)

        vm.highlight(3)
        advanceUntilIdle()
        assertEquals(3, vm.state.value.highlightedControl)

        vm.toggle()
        advanceUntilIdle()
        val state = vm.state.value

        assertEquals("TA50", state.shortId)
        assertEquals(6, state.controlsCount) // TA50 has 6 controls
        assertNull(state.highlightedControl) // switching routes clears the highlight
    }
}
