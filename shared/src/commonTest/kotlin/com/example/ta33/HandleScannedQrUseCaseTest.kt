package com.example.ta33

import com.example.ta33.domain.model.QrTimingConfig
import com.example.ta33.domain.model.RunSession
import com.example.ta33.domain.qr.QrPayloadParser
import com.example.ta33.domain.usecase.FinishRunUseCase
import com.example.ta33.domain.usecase.HandleScannedQrUseCase
import com.example.ta33.domain.usecase.ScanTimingResult
import com.example.ta33.domain.usecase.StartRunUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class HandleScannedQrUseCaseTest {

    private val runId = "run-1"
    private val routeId = "r1"

    private fun useCase(
        repo: FakeRunRepository,
        time: MutableTimeProvider,
        config: QrTimingConfig = QrTimingConfig(),
    ) = HandleScannedQrUseCase(
        parser = QrPayloadParser(),
        runs = repo,
        startRun = StartRunUseCase(repo, time),
        finishRun = FinishRunUseCase(repo, time),
        config = config,
    )

    private fun repoWith(started: Long? = null, finished: Long? = null): FakeRunRepository =
        FakeRunRepository().apply {
            seedRun(RunSession(runId, routeId, "p", startedAtMillis = started, finishedAtMillis = finished))
        }

    @Test
    fun startQr_onNotStarted_startsRun() = runTest {
        val repo = repoWith()
        val result = useCase(repo, MutableTimeProvider(now = 1_000L))(runId, routeId, "TA33:START")
        val started = assertIs<ScanTimingResult.Started>(result)
        assertEquals(1_000L, started.startedAtMillis)
        assertEquals(1_000L, repo.getRun(runId)?.startedAtMillis)
    }

    @Test
    fun startQr_onAlreadyStarted_isRejected_andKeepsTime() = runTest {
        val repo = repoWith(started = 1_000L)
        val result = useCase(repo, MutableTimeProvider(now = 9_000L))(runId, routeId, "TA33:START")
        assertEquals(ScanTimingResult.AlreadyStarted, result)
        assertEquals(1_000L, repo.getRun(runId)?.startedAtMillis)
    }

    @Test
    fun finishQr_whileRunning_finishesRun() = runTest {
        val repo = repoWith(started = 1_000L)
        val result = useCase(repo, MutableTimeProvider(now = 4_000L))(runId, routeId, "TA33:FINISH")
        val finished = assertIs<ScanTimingResult.Finished>(result)
        assertEquals(4_000L, finished.finishedAtMillis)
        assertEquals(3_000L, finished.elapsedMillis)
        assertEquals(4_000L, repo.getRun(runId)?.finishedAtMillis)
    }

    @Test
    fun finishQr_beforeStart_isRejected_noWrite() = runTest {
        val repo = repoWith()
        val result = useCase(repo, MutableTimeProvider(now = 4_000L))(runId, routeId, "TA33:FINISH")
        assertEquals(ScanTimingResult.FinishBeforeStart, result)
        assertNull(repo.getRun(runId)?.finishedAtMillis)
    }

    @Test
    fun anyQr_afterFinish_isRejected() = runTest {
        val repo = repoWith(started = 1_000L, finished = 4_000L)
        val time = MutableTimeProvider(now = 9_000L)
        assertEquals(ScanTimingResult.AlreadyFinished, useCase(repo, time)(runId, routeId, "TA33:START"))
        assertEquals(ScanTimingResult.AlreadyFinished, useCase(repo, time)(runId, routeId, "TA33:FINISH"))
        assertEquals(4_000L, repo.getRun(runId)?.finishedAtMillis)
    }

    @Test
    fun foreignString_isNotATimingQr_noWrite() = runTest {
        val repo = repoWith()
        val result = useCase(repo, MutableTimeProvider(now = 1_000L))(runId, routeId, "HELLO")
        assertIs<ScanTimingResult.NotATimingQr>(result)
        assertNull(repo.getRun(runId)?.startedAtMillis)
    }

    @Test
    fun unknownRunId_isRunNotFound() = runTest {
        val repo = repoWith()
        val result = useCase(repo, MutableTimeProvider(now = 1_000L))("nope", routeId, "TA33:START")
        assertIs<ScanTimingResult.RunNotFound>(result)
    }

    @Test
    fun routeScoped_wrongRoute_isRejected_noWrite() = runTest {
        val repo = repoWith()
        val config = QrTimingConfig(routeScoped = true)
        val result = useCase(repo, MutableTimeProvider(now = 1_000L), config)(runId, routeId, "TA33:START:otherRoute")
        val wrong = assertIs<ScanTimingResult.WrongRoute>(result)
        assertEquals(routeId, wrong.expectedRouteId)
        assertEquals("otherRoute", wrong.scannedRouteId)
        assertNull(repo.getRun(runId)?.startedAtMillis)
    }
}
