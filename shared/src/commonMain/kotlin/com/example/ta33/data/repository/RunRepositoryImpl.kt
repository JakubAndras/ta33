package com.example.ta33.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.example.ta33.core.IdGenerator
import com.example.ta33.data.db.Ta33Database
import com.example.ta33.data.mapper.toDomain
import com.example.ta33.domain.model.CollectedControl
import com.example.ta33.domain.model.RunSession
import com.example.ta33.domain.repository.RunRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RunRepositoryImpl(
    private val db: Ta33Database,
    private val ids: IdGenerator,
) : RunRepository {
    private val q get() = db.runSessionQueries
    private val cq get() = db.collectedControlQueries
    private val tq get() = db.trackpointQueries

    override suspend fun createRun(routeId: String, participantId: String): RunSession =
        withContext(Dispatchers.Default) {
            val run = RunSession(ids.newId(), routeId, participantId)
            q.insertRun(run.id, routeId, participantId, null, null, run.syncStatus.name)
            run
        }

    override suspend fun getRun(runId: String): RunSession? =
        withContext(Dispatchers.Default) {
            q.selectRunById(runId).executeAsOneOrNull()?.toDomain()
        }

    override fun observeRun(runId: String): Flow<RunSession?> =
        q.selectRunById(runId).asFlow().mapToOneOrNull(Dispatchers.Default)
            .map { it?.toDomain() }

    override fun observeActiveRun(): Flow<RunSession?> =
        q.selectActiveRun().asFlow().mapToOneOrNull(Dispatchers.Default)
            .map { it?.toDomain() }

    override suspend fun getActiveRun(): RunSession? =
        withContext(Dispatchers.Default) {
            q.selectActiveRun().executeAsOneOrNull()?.toDomain()
        }

    override fun observeLatestRun(): Flow<RunSession?> =
        q.selectLatestRun().asFlow().mapToOneOrNull(Dispatchers.Default)
            .map { it?.toDomain() }

    override suspend fun getLatestRun(): RunSession? =
        withContext(Dispatchers.Default) {
            q.selectLatestRun().executeAsOneOrNull()?.toDomain()
        }

    override suspend fun setStarted(runId: String, startedAtMillis: Long) {
        withContext(Dispatchers.Default) {
            q.setStarted(startedAtMillis, runId)
        }
    }

    override suspend fun setFinished(runId: String, finishedAtMillis: Long) {
        withContext(Dispatchers.Default) {
            q.setFinished(finishedAtMillis, runId)
        }
    }

    override suspend fun clearFinished(runId: String) {
        withContext(Dispatchers.Default) {
            q.clearFinished(runId)
        }
    }

    override suspend fun clearAllRuns() {
        withContext(Dispatchers.Default) {
            db.transaction {
                cq.deleteAllCollected()
                tq.deleteAllTrackpoints()
                q.deleteAllRuns()
            }
        }
    }

    override suspend fun addCollected(collected: CollectedControl): Boolean =
        withContext(Dispatchers.Default) {
            db.transactionWithResult {
                val before = cq.countForRun(collected.runSessionId).executeAsOne()
                cq.insertCollected(
                    collected.id,
                    collected.runSessionId,
                    collected.controlId,
                    collected.collectedAtMillis,
                    collected.syncStatus.name,
                )
                cq.countForRun(collected.runSessionId).executeAsOne() > before
            }
        }

    override fun observeCollected(runId: String): Flow<List<CollectedControl>> =
        cq.selectForRun(runId).asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }
}
