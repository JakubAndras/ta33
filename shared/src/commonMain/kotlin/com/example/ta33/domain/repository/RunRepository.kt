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
    suspend fun setStarted(runId: String, startedAtMillis: Long)
    suspend fun setFinished(runId: String, finishedAtMillis: Long)
    suspend fun addCollected(collected: CollectedControl): Boolean // false if already collected
    fun observeCollected(runId: String): Flow<List<CollectedControl>>
}
