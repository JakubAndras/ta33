package com.example.ta33.domain.map

import com.example.ta33.domain.download.DownloadItemProgress
import com.example.ta33.domain.download.DownloadStatus
import com.example.ta33.domain.model.PreparationState
import com.example.ta33.domain.model.PreparationStatus
import com.example.ta33.domain.model.TileFormat

/** Pure selection: which tileset (if any) should be used, and what state applies. */
object TileSourceSelector {

    /** Result carries only the *relative* path + id; the impl resolves absolute path + file existence. */
    sealed interface Selection {
        data object NotDownloaded : Selection
        data object Preparing : Selection
        data class Candidate(
            val tilesetId: String,
            val relativePath: String, // "tiles/<id>.<ext>"
            val format: TileFormat,
        ) : Selection
        data class Error(val message: String?) : Selection
    }

    fun select(
        preparation: PreparationState,
        assets: List<DownloadItemProgress>,
        config: MapTileConfig,
    ): Selection {
        val tileAssets = assets.filter { it.id.startsWith("tiles:") }
        val doneTiles = tileAssets.filter { it.status == DownloadStatus.DONE }

        return when (preparation.status) {
            PreparationStatus.ERROR -> Selection.Error(null)
            PreparationStatus.NOT_STARTED -> Selection.NotDownloaded
            PreparationStatus.PREPARING ->
                pickCandidate(doneTiles, config) ?: Selection.Preparing
            PreparationStatus.READY ->
                pickCandidate(doneTiles, config) ?: Selection.NotDownloaded
        }
    }

    private fun pickCandidate(
        doneTiles: List<DownloadItemProgress>,
        config: MapTileConfig,
    ): Selection.Candidate? {
        val chosen = when (val preferred = config.preferredBasemapTilesetId) {
            null -> doneTiles.singleOrNull() ?: doneTiles.firstOrNull()
            else -> doneTiles.firstOrNull { it.id == "tiles:$preferred" }
        } ?: return null
        val tilesetId = chosen.id.removePrefix("tiles:")
        val ext = config.defaultFormat.ext
        return Selection.Candidate(
            tilesetId = tilesetId,
            relativePath = "${config.tilesSubDir}/$tilesetId.$ext",
            format = config.defaultFormat,
        )
    }
}
