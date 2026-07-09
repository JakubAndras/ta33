package com.example.ta33.data.map

import com.example.ta33.data.file.FileStorage
import com.example.ta33.domain.map.MapTileConfig
import com.example.ta33.domain.map.TileSourceSelector
import com.example.ta33.domain.map.TileStore
import com.example.ta33.domain.model.MapTileSource
import com.example.ta33.domain.model.MapTileSourceState
import com.example.ta33.domain.model.PreparationState
import com.example.ta33.domain.repository.PreparationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TileStoreImpl(
    private val fileStorage: FileStorage, // FR-11
    private val preparationRepository: PreparationRepository, // FR-11
    private val config: MapTileConfig,
) : TileStore {

    override fun observeTileSource(): Flow<MapTileSourceState> =
        preparationRepository.observePreparationState().map { prep -> resolveFor(prep) }

    override suspend fun resolveTileSource(): MapTileSourceState =
        resolveFor(preparationRepository.getPreparationState())

    private suspend fun resolveFor(prep: PreparationState): MapTileSourceState =
        try {
            val assets = preparationRepository.loadAssets()
            when (val sel = TileSourceSelector.select(prep, assets, config)) {
                TileSourceSelector.Selection.NotDownloaded -> MapTileSourceState.NotDownloaded
                TileSourceSelector.Selection.Preparing -> MapTileSourceState.Preparing
                is TileSourceSelector.Selection.Error -> MapTileSourceState.Error(sel.message)
                is TileSourceSelector.Selection.Candidate -> {
                    // DB says DONE — confirm the bytes are physically present.
                    if (fileStorage.exists(sel.relativePath)) {
                        val abs = fileStorage.baseDir().trimEnd('/') + "/" + sel.relativePath
                        MapTileSourceState.Ready(MapTileSource(sel.tilesetId, abs, sel.format))
                    } else {
                        // DONE row but missing file → treat as needs (re)download.
                        MapTileSourceState.NotDownloaded
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MapTileSourceState.Error(e.message)
        }
}
