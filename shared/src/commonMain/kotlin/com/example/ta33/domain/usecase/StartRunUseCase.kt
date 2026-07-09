package com.example.ta33.domain.usecase

import com.example.ta33.core.TimeProvider
import com.example.ta33.domain.repository.RunRepository

class StartRunUseCase(
    private val runRepo: RunRepository,
    private val time: TimeProvider,
) {
    /** Sets the start timestamp. No-op if the run is already started (keeps the original start time). */
    suspend operator fun invoke(runId: String) {
        val run = runRepo.getRun(runId) ?: return
        if (run.startedAtMillis != null) return
        runRepo.setStarted(runId, time.nowMillis())
    }
}
