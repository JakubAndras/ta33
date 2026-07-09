package com.example.ta33.domain.map

import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.TileFormat

/** Small config for the basemap tileset. Etapa 1 = single Adršpach/Teplice basemap. */
data class MapTileConfig(
    val preferredBasemapTilesetId: String? = null, // null → pick the single DONE tiles asset
    val tilesSubDir: String = "tiles",
    val defaultFormat: TileFormat = TileFormat.MBTILES,
    /** Fallback camera focus when no route/live position (Adršpach rock town centre). */
    val fallbackFocus: GeoPoint = GeoPoint(50.6156, 16.1122),
)
