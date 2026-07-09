package com.example.ta33.presentation

import com.example.ta33.FakeRunRepository
import com.example.ta33.FakeTicker
import com.example.ta33.MutableTimeProvider
import com.example.ta33.domain.model.QrTimingConfig
import com.example.ta33.domain.model.RunSession
import com.example.ta33.domain.qr.QrPayloadParser
import com.example.ta33.domain.usecase.FinishRunUseCase
import com.example.ta33.domain.usecase.HandleScannedQrUseCase
import com.example.ta33.domain.usecase.ObserveTimingUseCase
import com.example.ta33.domain.usecase.ScanTimingResult
import com.example.ta33.domain.usecase.StartRunUseCase
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
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class TimingViewModelTest {

    private val runId = "run-1"
    private val routeId = "r1"

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repo: FakeRunRepository,
        time: MutableTimeProvider,
        ticker: FakeTicker,
    ): TimingViewModel {
        val observe = ObserveTimingUseCase(repo, time, ticker)
        val handle = HandleScannedQrUseCase(
            parser = QrPayloadParser(),
            runs = repo,
            startRun = StartRunUseCase(repo, time),
            finishRun = FinishRunUseCase(repo, time),
            config = QrTimingConfig(),
        )
        return TimingViewModel(observe, handle)
    }

    @Test
    fun bind_surfacesElapsedFormatted() = runTest {
        val repo = FakeRunRepository().apply { seedRun(RunSession(runId, routeId, "p", startedAtMillis = 1_000L)) }
        val ticker = FakeTicker()
        val vm = viewModel(repo, MutableTimeProvider(now = 66_000L), ticker)
        vm.bind(runId, routeId)
        advanceUntilIdle()
        ticker.tick()
        advanceUntilIdle()

        assertEquals(65_000L, vm.state.value.elapsedMillis)
        assertEquals("01:05", vm.state.value.elapsedFormatted)
    }

    @Test
    fun onQrScanned_start_reflectsStarted() = runTest {
        val repo = FakeRunRepository().apply { seedRun(RunSession(runId, routeId, "p", startedAtMillis = null)) }
        val vm = viewModel(repo, MutableTimeProvider(now = 1_000L), FakeTicker())
        vm.bind(runId, routeId)
        advanceUntilIdle()
        vm.onQrScanned("TA33:START")
        advanceUntilIdle()

        assertIs<ScanTimingResult.Started>(vm.state.value.lastScan)
    }

    @Test
    fun onQrScanned_foreign_reflectsNotATimingQr() = runTest {
        val repo = FakeRunRepository().apply { seedRun(RunSession(runId, routeId, "p", startedAtMillis = 1_000L)) }
        val vm = viewModel(repo, MutableTimeProvider(now = 1_000L), FakeTicker())
        vm.bind(runId, routeId)
        advanceUntilIdle()
        vm.onQrScanned("HELLO")
        advanceUntilIdle()

        assertIs<ScanTimingResult.NotATimingQr>(vm.state.value.lastScan)
    }

    @Test
    fun onQrScanned_beforeBind_isNoOp() = runTest {
        val repo = FakeRunRepository().apply { seedRun(RunSession(runId, routeId, "p", startedAtMillis = null)) }
        val vm = viewModel(repo, MutableTimeProvider(now = 1_000L), FakeTicker())
        vm.onQrScanned("TA33:START")
        advanceUntilIdle()

        assertNull(vm.state.value.lastScan)
        assertNull(repo.getRun(runId)?.startedAtMillis)
    }
}
