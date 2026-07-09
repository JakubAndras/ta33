package com.example.ta33.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.example.ta33.core.TimeProvider
import com.example.ta33.data.db.DownloadedAsset
import com.example.ta33.data.db.Preparation
import com.example.ta33.data.db.Ta33Database
import com.example.ta33.domain.download.DownloadItemProgress
import com.example.ta33.domain.download.DownloadStatus
import com.example.ta33.domain.model.PreparationState
import com.example.ta33.domain.model.PreparationStatus
import com.example.ta33.domain.repository.PreparationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PreparationRepositoryImpl(
    private val db: Ta33Database,
    private val time: TimeProvider,
) : PreparationRepository {
    private val pq get() = db.preparationQueries
    private val aq get() = db.downloadedAssetQueries

    override fun observePreparationState(): Flow<PreparationState> =
        pq.selectPreparation().asFlow().mapToOneOrNull(Dispatchers.Default)
            .map { it?.toState() ?: PreparationState() }

    override suspend fun getPreparationState(): PreparationState =
        withContext(Dispatchers.Default) {
            pq.selectPreparation().executeAsOneOrNull()?.toState() ?: PreparationState()
        }

    override suspend fun setPreparing(manifestVersion: Int) {
        withContext(Dispatchers.Default) {
            pq.upsertPreparation(PreparationStatus.PREPARING.name, manifestVersion.toLong(), null)
        }
    }

    override suspend fun markReady(manifestVersion: Int) {
        withContext(Dispatchers.Default) {
            pq.upsertPreparation(PreparationStatus.READY.name, manifestVersion.toLong(), time.nowMillis())
        }
    }

    override suspend fun markError() {
        withContext(Dispatchers.Default) {
            val current = pq.selectPreparation().executeAsOneOrNull()
            pq.upsertPreparation(PreparationStatus.ERROR.name, current?.manifestVersion, current?.readyAtMillis)
        }
    }

    override suspend fun loadAssets(): List<DownloadItemProgress> =
        withContext(Dispatchers.Default) {
            aq.selectAllAssets().executeAsList().map { it.toProgress() }
        }

    override suspend fun saveAsset(item: DownloadItemProgress, relativePath: String?) {
        withContext(Dispatchers.Default) {
            aq.upsertAsset(
                item.id,
                relativePath,
                item.status.name,
                item.bytesDownloaded,
                item.totalBytes,
                null,
                time.nowMillis(),
            )
        }
    }

    override suspend fun clearAssets() {
        withContext(Dispatchers.Default) {
            aq.deleteAllAssets()
        }
    }

    private fun Preparation.toState() = PreparationState(
        status = PreparationStatus.fromDb(status),
        manifestVersion = manifestVersion?.toInt(),
        readyAtMillis = readyAtMillis,
    )

    private fun DownloadedAsset.toProgress() = DownloadItemProgress(
        id = itemId,
        label = itemId,
        status = DownloadStatus.fromDb(status),
        bytesDownloaded = bytesDownloaded,
        totalBytes = totalBytes,
    )
}
