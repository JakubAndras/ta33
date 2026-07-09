package com.example.ta33.presentation

import com.example.ta33.FakeAppPreferencesRepository
import com.example.ta33.FakePreparationRepository
import com.example.ta33.FakeRouteRepository
import com.example.ta33.FakeRunRepository
import com.example.ta33.domain.model.Route
import com.example.ta33.domain.usecase.ObserveAppStateUseCase
import com.example.ta33.domain.usecase.ObserveOverviewUseCase
import com.example.ta33.domain.usecase.ObservePreparationStateUseCase
import com.example.ta33.domain.usecase.ObserveRunLogUseCase
import com.example.ta33.presentation.navigation.StartDestinationResolver
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
class OverviewViewModelTest {

    private lateinit var routeRepo: FakeRouteRepository
    private lateinit var runRepo: FakeRunRepository
    private lateinit var prefsRepo: FakeAppPreferencesRepository
    private lateinit var prepRepo: FakePreparationRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        routeRepo = FakeRouteRepository()
        runRepo = FakeRunRepository()
        prefsRepo = FakeAppPreferencesRepository()
        prepRepo = FakePreparationRepository()
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = OverviewViewModel(
        ObserveOverviewUseCase(
            observeAppState = ObserveAppStateUseCase(routeRepo, runRepo, prefsRepo, prepRepo, StartDestinationResolver()),
            routes = routeRepo,
            observeRunLog = ObserveRunLogUseCase(routeRepo, runRepo),
            observePreparation = ObservePreparationStateUseCase(prepRepo),
        ),
    )

    @Test
    fun initialLoadingTrue_thenComposedLoadingFalse() = runTest {
        val vm = viewModel()
        assertTrue(vm.state.value.loading)

        routeRepo.upsertRoute(Route(id = "r1", name = "A", distanceKm = 33.0), emptyList())
        advanceUntilIdle()

        assertFalse(vm.state.value.loading)
        assertEquals("r1", vm.state.value.activeRoute?.routeId)
    }
}
