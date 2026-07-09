package com.example.ta33.domain.map

import com.example.ta33.domain.model.MapTileSourceState
import kotlinx.coroutines.flow.Flow

/** Read seam over FR-11 preparation + files: turns them into an observable offline-basemap state. */
interface TileStore {
    fun observeTileSource(): Flow<MapTileSourceState>
    suspend fun resolveTileSource(): MapTileSourceState
}
