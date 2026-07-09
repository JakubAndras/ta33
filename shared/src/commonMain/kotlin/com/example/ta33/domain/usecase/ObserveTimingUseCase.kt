package com.example.ta33.domain.usecase

import com.example.ta33.core.SplitCalculator
import com.example.ta33.core.Stopwatch
import com.example.ta33.core.Ticker
import com.example.ta33.core.TimeProvider          // FR-02
import com.example.ta33.domain.model.TimingSnapshot
import com.example.ta33.domain.repository.RunRepository  // FR-02
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ObserveTimingUseCase(
    private val runs: RunRepository,   // FR-02 observeRun / observeCollected
    private val time: TimeProvider,    // FR-02
    private val ticker: Ticker,
    private val tickPeriodMillis: Long = 1_000L,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(runId: String): Flow<TimingSnapshot> =
        combine(
            runs.observeRun(runId),        // RunSession?
            runs.observeCollected(runId),  // List<CollectedControl>
        ) { run, collected -> run to collected }
            .flatMapLatest { (run, collected) ->
                val start = run?.startedAtMillis
                val finish = run?.finishedAtMillis
                fun snapshot(now: Long) = TimingSnapshot(
                    isRunning = start != null && finish == null,
                    isFinished = finish != null,
                    elapsedMillis = Stopwatch.elapsed(now, start, finish),
                    splits = SplitCalculator.splits(start, collected),
                )
                if (start != null && finish == null) {
                    ticker.ticks(tickPeriodMillis).map { snapshot(time.nowMillis()) }  // live, ticking
                } else {
                    flowOf(snapshot(time.nowMillis()))  // zeroed (pre-start) or frozen (finished)
                }
            }
            .distinctUntilChanged()
}
