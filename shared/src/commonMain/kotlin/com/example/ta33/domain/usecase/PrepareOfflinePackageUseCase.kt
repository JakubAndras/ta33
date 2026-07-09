package com.example.ta33.domain.usecase

import com.example.ta33.data.connectivity.ConnectivityMonitor
import com.example.ta33.data.dto.ManifestDto
import com.example.ta33.domain.download.DownloadItemProgress
import com.example.ta33.domain.download.DownloadStatus
import com.example.ta33.domain.download.OfflinePackageProgress
import com.example.ta33.domain.download.ProgressReducer
import com.example.ta33.domain.model.NetworkPreference
import com.example.ta33.domain.model.NetworkType
import com.example.ta33.domain.repository.OfflinePackageRepository
import com.example.ta33.domain.repository.PreparationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface PrepareResult {
    data object Success : PrepareResult
    data object BlockedByNetwork : PrepareResult
    data class Failed(val itemId: String, val cause: Throwable) : PrepareResult
}

class PrepareOfflinePackageUseCase(
    private val offline: OfflinePackageRepository,
    private val prep: PreparationRepository,
    private val connectivity: ConnectivityMonitor,
) {
    /** Emits progress as it runs; terminal emission carries overallStatus DONE/ERROR. */
    fun run(preference: NetworkPreference): Flow<OfflinePackageProgress> = flow {
        if (!networkAllows(preference, connectivity.current())) {
            emit(OfflinePackageProgress(overallStatus = DownloadStatus.PAUSED))
            return@flow
        }

        val manifest = try {
            offline.fetchManifest()
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            prep.markError()
            emit(OfflinePackageProgress(overallStatus = DownloadStatus.ERROR))
            return@flow
        }
        // A newer/different manifest version invalidates prior resume bookkeeping (project-stack §10;
        // plan §5 "Manifest version newer than persisted READY → clear assets / re-run").
        val persisted = prep.getPreparationState()
        if (persisted.manifestVersion != null && persisted.manifestVersion != manifest.version) {
            prep.clearAssets()
        }
        prep.setPreparing(manifest.version)

        // Build item list = content + tilesets; seed from persisted assets for resume.
        var state = seedState(manifest, prep.loadAssets())
        emit(state)

        // content item (skip if already DONE)
        state = downloadItem(state, id = "content", skipIfDone = true) { onBytes ->
            offline.downloadAndPersistContent(manifest.content.url, onBytes)
            null
        }
        emit(state)

        if (state.overallStatus != DownloadStatus.ERROR) {
            for (t in manifest.tiles) {
                state = downloadItem(state, id = "tiles:${t.id}", skipIfDone = true) { onBytes ->
                    offline.downloadTileset(t.id, t.url, t.bytes, onBytes)
                }
                emit(state)
                if (state.overallStatus == DownloadStatus.ERROR) break
            }
        }

        if (state.overallStatus == DownloadStatus.DONE) prep.markReady(manifest.version) else prep.markError()
        emit(state)
    }

    private fun seedState(manifest: ManifestDto, persisted: List<DownloadItemProgress>): OfflinePackageProgress {
        val persistedById = persisted.associateBy { it.id }
        val items = buildList {
            add(persistedById["content"] ?: DownloadItemProgress(id = "content", label = "content"))
            manifest.tiles.forEach { t ->
                val id = "tiles:${t.id}"
                add(persistedById[id] ?: DownloadItemProgress(id = id, label = t.id, totalBytes = t.bytes))
            }
        }
        return ProgressReducer.aggregate(OfflinePackageProgress(items = items))
    }

    private suspend fun downloadItem(
        state: OfflinePackageProgress,
        id: String,
        skipIfDone: Boolean,
        block: suspend (onBytes: (Long, Long?) -> Unit) -> String?,
    ): OfflinePackageProgress {
        val existing = state.items.firstOrNull { it.id == id }
        if (skipIfDone && existing?.status == DownloadStatus.DONE) return state
        var item = (existing ?: DownloadItemProgress(id = id, label = id))
            .copy(status = DownloadStatus.DOWNLOADING, error = null)
        var next = ProgressReducer.replaceItem(state, item)
        prep.saveAsset(item, null)
        return try {
            val relativePath = block { downloaded, total ->
                item = item.copy(bytesDownloaded = downloaded, totalBytes = total ?: item.totalBytes)
            }
            item = item.copy(status = DownloadStatus.DONE)
            next = ProgressReducer.replaceItem(next, item)
            prep.saveAsset(item, relativePath)
            next
        } catch (c: CancellationException) {
            throw c
        } catch (e: Throwable) {
            item = item.copy(status = DownloadStatus.ERROR, error = e.message)
            next = ProgressReducer.replaceItem(next, item)
            prep.saveAsset(item, null)
            next
        }
    }

    companion object {
        fun networkAllows(pref: NetworkPreference, type: NetworkType): Boolean = when (pref) {
            NetworkPreference.WIFI_ONLY -> type == NetworkType.WIFI
            NetworkPreference.WIFI_AND_CELLULAR -> type == NetworkType.WIFI || type == NetworkType.CELLULAR
        }
    }
}
