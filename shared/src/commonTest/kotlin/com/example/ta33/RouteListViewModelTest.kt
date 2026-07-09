package com.example.ta33

import com.example.ta33.domain.model.Route
import com.example.ta33.domain.usecase.ObserveRoutesUseCase
import com.example.ta33.domain.usecase.ObserveSelectedRouteUseCase
import com.example.ta33.domain.usecase.SelectActiveRouteUseCase
import com.example.ta33.presentation.RouteListViewModel
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

@OptIn(ExperimentalCoroutinesApi::class)
class RouteListViewModelTest {

    private lateinit var routeRepo: FakeRouteRepository
    private lateinit var prefsRepo: FakeAppPreferencesRepository

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

    private fun viewModel() = RouteListViewModel(
        ObserveRoutesUseCase(routeRepo),
        ObserveSelectedRouteUseCase(routeRepo, prefsRepo),
        SelectActiveRouteUseCase(routeRepo, prefsRepo),
    )

    @Test
    fun emitsSummariesAndSelectedRouteId() = runTest {
        routeRepo.upsertRoute(Route(id = "r1", name = "A", distanceKm = 33.0), emptyList())
        routeRepo.upsertRoute(Route(id = "r2", name = "B", distanceKm = 22.0), emptyList())
        prefsRepo.setSelectedRouteId("r2")

        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(false, state.loading)
        assertEquals(listOf("r1", "r2"), state.routes.map { it.routeId })
        assertEquals("r2", state.selectedRouteId)
    }

    @Test
    fun selectRoute_persistsSelection() = runTest {
        routeRepo.upsertRoute(Route(id = "r1", name = "A", distanceKm = 33.0), emptyList())
        routeRepo.upsertRoute(Route(id = "r2", name = "B", distanceKm = 22.0), emptyList())

        val vm = viewModel()
        advanceUntilIdle()

        vm.selectRoute("r2")
        advanceUntilIdle()

        assertEquals("r2", prefsRepo.getSelectedRouteId())
        assertEquals("r2", vm.state.value.selectedRouteId)
    }
}
