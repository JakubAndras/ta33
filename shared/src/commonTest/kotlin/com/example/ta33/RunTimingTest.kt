package com.example.ta33

import com.example.ta33.domain.model.RunSession
import com.example.ta33.domain.usecase.FinishRunUseCase
import com.example.ta33.domain.usecase.StartRunUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RunTimingTest {

    private val runId = "run-1"

    private fun seededRepo(run: RunSession): FakeRunRepository =
        FakeRunRepository().apply { seedRun(run) }

    private fun newRun(started: Long? = null, finished: Long? = null) =
        RunSession(
            id = runId,
            routeId = "r1",
            participantId = "p1",
            startedAtMillis = started,
            finishedAtMillis = finished,
        )

    @Test
    fun startRun_setsTimestamp() = runTest {
        val repo = seededRepo(newRun())
        val time = MutableTimeProvider(now = 1_000L)
        StartRunUseCase(repo, time)(runId)
        assertEquals(1_000L, repo.getRun(runId)?.startedAtMillis)
    }

    @Test
    fun startRun_twice_keepsOriginalTime() = runTest {
        val repo = seededRepo(newRun())
        val time = MutableTimeProvider(now = 1_000L)
        val start = StartRunUseCase(repo, time)
        start(runId)
        time.now = 5_000L
        start(runId)
        assertEquals(1_000L, repo.getRun(runId)?.startedAtMillis)
    }

    @Test
    fun finish_beforeStart_isRejected_andRunStaysOpen() = runTest {
        val repo = seededRepo(newRun(started = 2_000L))
        val time = MutableTimeProvider(now = 1_000L) // now < startedAt
        val result = FinishRunUseCase(repo, time)(runId)
        assertEquals(FinishRunUseCase.Result.FinishBeforeStart, result)
        assertNull(repo.getRun(runId)?.finishedAtMillis)
    }

    @Test
    fun finish_whenNotStarted_isRejected() = runTest {
        val repo = seededRepo(newRun())
        val result = FinishRunUseCase(repo, MutableTimeProvider(now = 1_000L))(runId)
        assertEquals(FinishRunUseCase.Result.NotStarted, result)
    }

    @Test
    fun finish_twice_isRejected_andKeepsOriginalFinishTime() = runTest {
        val repo = seededRepo(newRun(started = 1_000L))
        val time = MutableTimeProvider(now = 1_300L)
        val finish = FinishRunUseCase(repo, time)
        assertEquals(FinishRunUseCase.Result.Finished, finish(runId))
        time.now = 5_000L
        assertEquals(FinishRunUseCase.Result.AlreadyFinished, finish(runId))
        assertEquals(1_300L, repo.getRun(runId)?.finishedAtMillis)
    }

    @Test
    fun finish_afterStart_succeeds_andSplitIsDifference() = runTest {
        val repo = seededRepo(newRun(started = 1_000L))
        val time = MutableTimeProvider(now = 1_300L)
        val result = FinishRunUseCase(repo, time)(runId)
        assertEquals(FinishRunUseCase.Result.Finished, result)
        val run = repo.getRun(runId)!!
        assertEquals(1_300L, run.finishedAtMillis)
        // split time = finishedAt - startedAt
        assertEquals(300L, run.finishedAtMillis!! - run.startedAtMillis!!)
    }
}
