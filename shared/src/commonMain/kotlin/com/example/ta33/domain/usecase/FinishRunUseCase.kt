package com.example.ta33.domain.usecase

import com.example.ta33.core.TimeProvider
import com.example.ta33.domain.repository.RunRepository

class FinishRunUseCase(
    private val runRepo: RunRepository,
    private val time: TimeProvider,
) {
    sealed interface Result {
        data object Finished : Result
        data object AlreadyFinished : Result
        data object NotStarted : Result
        data object FinishBeforeStart : Result
        data object UnknownRun : Result
    }

    suspend operator fun invoke(runId: String): Result {
        val run = runRepo.getRun(runId) ?: return Result.UnknownRun
        val startedAt = run.startedAtMillis ?: return Result.NotStarted
        if (run.finishedAtMillis != null) return Result.AlreadyFinished
        val now = time.nowMillis()
        if (now < startedAt) return Result.FinishBeforeStart
        runRepo.setFinished(runId, now)
        return Result.Finished
    }
}
