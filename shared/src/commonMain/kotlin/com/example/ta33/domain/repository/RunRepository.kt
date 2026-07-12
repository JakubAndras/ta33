package com.example.ta33.domain.repository

import com.example.ta33.domain.model.CollectedControl
import com.example.ta33.domain.model.RunSession
import kotlinx.coroutines.flow.Flow

interface RunRepository {
    suspend fun createRun(routeId: String, participantId: String): RunSession
    suspend fun getRun(runId: String): RunSession?
    fun observeRun(runId: String): Flow<RunSession?>
    fun observeActiveRun(): Flow<RunSession?>
    suspend fun getActiveRun(): RunSession?

    /** Latest run regardless of finished state (DEV Sandbox needs to reach a finished run). */
    fun observeLatestRun(): Flow<RunSession?>
    suspend fun getLatestRun(): RunSession?

    suspend fun setStarted(runId: String, startedAtMillis: Long)
    suspend fun setFinished(runId: String, finishedAtMillis: Long)

    /** DEV Sandbox: clears a run's finish timestamp (un-finish → back on-route). */
    suspend fun clearFinished(runId: String)

    /** DEV Sandbox: wipes all runs + their collected controls + trackpoints (reset to pre-event). */
    suspend fun clearAllRuns()
    suspend fun addCollected(collected: CollectedControl): Boolean // false if already collected
    fun observeCollected(runId: String): Flow<List<CollectedControl>>
}
