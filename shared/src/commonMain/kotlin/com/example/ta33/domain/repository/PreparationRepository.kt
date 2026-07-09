package com.example.ta33.domain.repository

import com.example.ta33.domain.download.DownloadItemProgress
import com.example.ta33.domain.model.PreparationState
import kotlinx.coroutines.flow.Flow

interface PreparationRepository {
    fun observePreparationState(): Flow<PreparationState>
    suspend fun getPreparationState(): PreparationState
    suspend fun setPreparing(manifestVersion: Int)
    suspend fun markReady(manifestVersion: Int)
    suspend fun markError()

    // resume bookkeeping
    suspend fun loadAssets(): List<DownloadItemProgress>
    suspend fun saveAsset(item: DownloadItemProgress, relativePath: String?)
    suspend fun clearAssets()
}
