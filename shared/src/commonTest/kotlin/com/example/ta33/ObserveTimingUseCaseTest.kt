package com.example.ta33

import app.cash.turbine.turbineScope
import com.example.ta33.domain.model.CollectedControl
import com.example.ta33.domain.model.RunSession
import com.example.ta33.domain.usecase.ObserveTimingUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveTimingUseCaseTest {

    private val runId = "run-1"

    @Test
    fun notStarted_emitsZeroedSnapshot() = runTest {
        val repo = FakeRunRepository().apply { seedRun(RunSession(runId, "r1", "p", startedAtMillis = null)) }
        val useCase = ObserveTimingUseCase(repo, MutableTimeProvider(now = 5_000L), FakeTicker())
        turbineScope {
            val snaps = useCase(runId).testIn(backgroundScope)
            runCurrent()

            val snap = snaps.awaitItem()
            assertEquals(0L, snap.elapsedMillis)
            assertFalse(snap.isRunning)
            snaps.expectNoEvents()
            snaps.cancel()
        }
    }

    @Test
    fun activeRun_ticksAdvanceElapsed_noRepoWrite() = runTest {
        val repo = FakeRunRepository().apply { seedRun(RunSession(runId, "r1", "p", startedAtMillis = 1_000L)) }
        val time = MutableTimeProvider(now = 1_000L)
        val ticker = FakeTicker()
        val useCase = ObserveTimingUseCase(repo, time, ticker)
        turbineScope {
            val snaps = useCase(runId).testIn(backgroundScope)
            runCurrent()

            time.now = 4_000L
            ticker.tick()
            assertEquals(3_000L, snaps.awaitItem().elapsedMillis)
            time.now = 6_000L
            ticker.tick()
            assertEquals(5_000L, snaps.awaitItem().elapsedMillis)
            snaps.cancel()
        }
        // Nothing persisted per tick: run timestamps unchanged.
        assertEquals(1_000L, repo.getRun(runId)?.startedAtMillis)
        assertEquals(null, repo.getRun(runId)?.finishedAtMillis)
    }

    @Test
    fun collectedList_reflectedAsSortedSplits() = runTest {
        val repo = FakeRunRepository().apply { seedRun(RunSession(runId, "r1", "p", startedAtMillis = 1_000L)) }
        val ticker = FakeTicker()
        val useCase = ObserveTimingUseCase(repo, MutableTimeProvider(now = 1_000L), ticker)
        turbineScope {
            val snaps = useCase(runId).testIn(backgroundScope)
            runCurrent()

            ticker.tick()
            assertTrue(snaps.awaitItem().splits.isEmpty())

            repo.addCollected(CollectedControl("cc2", runId, "c2", collectedAtMillis = 3_000L))
            repo.addCollected(CollectedControl("cc1", runId, "c1", collectedAtMillis = 2_000L))
            runCurrent()
            ticker.tick()

            val last = snaps.awaitItem()
            assertEquals(listOf("c1", "c2"), last.splits.map { it.controlId })
            assertEquals(listOf(1_000L, 2_000L), last.splits.map { it.splitMillis })
            snaps.cancel()
        }
    }

    @Test
    fun finish_emitsFrozenSnapshot_andTickerStops() = runTest {
        val repo = FakeRunRepository().apply { seedRun(RunSession(runId, "r1", "p", startedAtMillis = 1_000L)) }
        val time = MutableTimeProvider(now = 3_000L)
        val ticker = FakeTicker()
        val useCase = ObserveTimingUseCase(repo, time, ticker)
        turbineScope {
            val snaps = useCase(runId).testIn(backgroundScope)
            runCurrent()

            ticker.tick()
            assertEquals(2_000L, snaps.awaitItem().elapsedMillis) // 3_000 - 1_000, running

            repo.setFinished(runId, 5_000L)
            val frozen = snaps.awaitItem()
            assertTrue(frozen.isFinished)
            assertEquals(4_000L, frozen.elapsedMillis) // 5_000 - 1_000, frozen

            // Ticker no longer drives new values once finished.
            time.now = 99_000L
            ticker.tick()
            snaps.expectNoEvents()
            snaps.cancel()
        }
    }
}
