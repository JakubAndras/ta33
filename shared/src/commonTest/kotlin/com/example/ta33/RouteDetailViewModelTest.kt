package com.example.ta33

import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.Route
import com.example.ta33.domain.usecase.ObserveRouteDetailUseCase
import com.example.ta33.domain.usecase.ObserveSelectedRouteUseCase
import com.example.ta33.domain.usecase.SelectActiveRouteUseCase
import com.example.ta33.presentation.RouteDetailViewModel
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RouteDetailViewModelTest {

    private lateinit var routeRepo: FakeRouteRepository
    private lateinit var prefsRepo: FakeAppPreferencesRepository

    private fun control(id: String, routeId: String, ordinal: Int) = ControlPoint(
        id = id, routeId = routeId, ordinal = ordinal, name = "K$ordinal", location = GeoPoint(50.0, 16.0),
    )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        routeRepo = FakeRouteRepository()
        prefsRepo = FakeAppPreferencesRepository()
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = RouteDetailViewModel(
        ObserveRouteDetailUseCase(routeRepo),
        ObserveSelectedRouteUseCase(routeRepo, prefsRepo),
        SelectActiveRouteUseCase(routeRepo, prefsRepo),
    )

    @Test
    fun bind_emitsDetail_isActiveWhenSelectionMatches() = runTest {
        routeRepo.upsertRoute(
            route = Route(id = "r1", name = "A", distanceKm = 33.0),
            controls = listOf(control("c1", "r1", 1), control("c2", "r1", 2)),
        )
        routeRepo.upsertRoute(Route(id = "r2", name = "B", distanceKm = 22.0), emptyList())
        prefsRepo.setSelectedRouteId("r1")

        val vm = viewModel()
        vm.bind("r1")
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(false, state.loading)
        assertEquals("r1", state.detail?.routeId)
        assertEquals(2, state.detail?.controlCount)
        assertTrue(state.isActive)
        assertEquals(false, state.notFound)
    }

    @Test
    fun bind_unknownRoute_notFound() = runTest {
        val vm = viewModel()
        vm.bind("nope")
        advanceUntilIdle()

        val state = vm.state.value
        assertNull(state.detail)
        assertTrue(state.notFound)
    }

    @Test
    fun makeActive_persistsSelection() = runTest {
        routeRepo.upsertRoute(Route(id = "r1", name = "A", distanceKm = 33.0), emptyList())

        val vm = viewModel()
        vm.bind("r1")
        advanceUntilIdle()

        vm.makeActive()
        advanceUntilIdle()

        assertEquals("r1", prefsRepo.getSelectedRouteId())
        assertTrue(vm.state.value.isActive)
    }
}
