package com.example.ta33.presentation

import com.example.ta33.FakeAppPreferencesRepository
import com.example.ta33.FakePreparationRepository
import com.example.ta33.FakeRouteRepository
import com.example.ta33.FakeRunRepository
import com.example.ta33.domain.model.Participant
import com.example.ta33.domain.model.PreparationState
import com.example.ta33.domain.model.PreparationStatus
import com.example.ta33.domain.model.Route
import com.example.ta33.domain.model.RunSession
import com.example.ta33.domain.repository.ParticipantRepository
import com.example.ta33.domain.usecase.EnsureLocalParticipantUseCase
import com.example.ta33.domain.usecase.ObserveAppStateUseCase
import com.example.ta33.presentation.navigation.AppReadiness
import com.example.ta33.presentation.navigation.Destination
import com.example.ta33.presentation.navigation.StartDestinationResolver
import com.example.ta33.presentation.navigation.TopLevelDestination
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
class AppViewModelTest {

    private class CountingParticipantRepository : ParticipantRepository {
        var invocations = 0
            private set

        override suspend fun getOrCreateLocalParticipant(): Participant {
            invocations++
            return Participant(id = "p1", createdAtMillis = 0L)
        }
    }

    private lateinit var routeRepo: FakeRouteRepository
    private lateinit var runRepo: FakeRunRepository
    private lateinit var prefsRepo: FakeAppPreferencesRepository
    private lateinit var prepRepo: FakePreparationRepository
    private lateinit var participantRepo: CountingParticipantRepository
    private lateinit var ensureLocalParticipant: EnsureLocalParticipantUseCase

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        routeRepo = FakeRouteRepository()
        runRepo = FakeRunRepository()
        prefsRepo = FakeAppPreferencesRepository()
        prepRepo = FakePreparationRepository()
        participantRepo = CountingParticipantRepository()
        ensureLocalParticipant = EnsureLocalParticipantUseCase(participantRepo)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        AppViewModel(
            ObserveAppStateUseCase(routeRepo, runRepo, prefsRepo, prepRepo, StartDestinationResolver()),
            ensureLocalParticipant,
        )

    @Test
    fun noContent_becomesNotReadyAtPreparation() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(AppReadiness.NOT_READY, state.readiness)
        assertEquals(Destination.Preparation, state.startDestination)
    }

    @Test
    fun routePushed_becomesReadyMainDenik() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        routeRepo.upsertRoute(Route(id = "r1", name = "A", distanceKm = 33.0), emptyList())
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(AppReadiness.READY, state.readiness)
        assertEquals(Destination.Main(TopLevelDestination.DENIK), state.startDestination)
        assertEquals("r1", state.activeRouteId)
    }

    @Test
    fun activeRunPushed_resumesRunActive() = runTest {
        val vm = viewModel()
        routeRepo.upsertRoute(Route(id = "r1", name = "A", distanceKm = 33.0), emptyList())
        runRepo.seedRun(RunSession(id = "run-1", routeId = "r1", participantId = "p1", startedAtMillis = 1_000L))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(Destination.RunActive("run-1"), state.startDestination)
        assertEquals("run-1", state.activeRunId)
        assertEquals("r1", state.activeRouteId)
    }

    @Test
    fun persistedSelection_prefersSelectedRouteOverAmbiguousNull() = runTest {
        val vm = viewModel()
        routeRepo.upsertRoute(Route(id = "r1", name = "A", distanceKm = 33.0), emptyList())
        routeRepo.upsertRoute(Route(id = "r2", name = "B", distanceKm = 22.0), emptyList())
        prefsRepo.setSelectedRouteId("r2")
        advanceUntilIdle()

        assertEquals("r2", vm.state.value.activeRouteId)
    }

    @Test
    fun ensureLocalParticipant_invokedOnce() = runTest {
        viewModel()
        advanceUntilIdle()

        assertEquals(1, participantRepo.invocations)
    }

    @Test
    fun preparationPreparing_readinessPreparing() = runTest {
        prepRepo.set(PreparationState(status = PreparationStatus.PREPARING, manifestVersion = 1))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(AppReadiness.PREPARING, vm.state.value.readiness)
        assertEquals(Destination.Preparation, vm.state.value.startDestination)
    }

    @Test
    fun preparationReady_readinessReady() = runTest {
        prepRepo.set(PreparationState(status = PreparationStatus.READY, manifestVersion = 1, readyAtMillis = 5L))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(AppReadiness.READY, vm.state.value.readiness)
    }
}
