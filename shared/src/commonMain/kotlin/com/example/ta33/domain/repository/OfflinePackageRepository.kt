package com.example.ta33.domain.repository

import com.example.ta33.data.dto.ManifestDto

interface OfflinePackageRepository {
    suspend fun fetchManifest(): ManifestDto

    /** Downloads content JSON and persists routes/controls via RouteRepository.upsertRoute. */
    suspend fun downloadAndPersistContent(url: String, onBytes: (downloaded: Long, total: Long?) -> Unit)

    /** Streams a tile file to FileStorage; resumes from existing size via HTTP Range. Returns relativePath. */
    suspend fun downloadTileset(
        tilesetId: String,
        url: String,
        expectedBytes: Long?,
        onBytes: (downloaded: Long, total: Long?) -> Unit,
    ): String
}
