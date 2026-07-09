package com.example.ta33.domain.model

enum class TileFormat(val ext: String) {
    MBTILES("mbtiles"),
    UNKNOWN("");

    companion object {
        fun fromRaw(raw: String): TileFormat =
            entries.firstOrNull { it.ext.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}

/**
 * Describes a downloaded, on-disk offline tile source handed to the native MapLibre layer.
 *
 * Consumed only by the native map view (Android MapLibre Native / iOS MapLibre Native). Shared code
 * never opens or renders it. See the platform seam in the FR-06 plan
 * (`.claude/plans/fr-06-offline-map-data-aggregation.md`, §3.4).
 */
data class MapTileSource(
    val tilesetId: String, // e.g. "adrspach-teplice"
    val absolutePath: String, // full filesystem path: FileStorage.baseDir()/tiles/<id>.<ext>
    val format: TileFormat,
)

/** Availability of the offline basemap. `NotDownloaded` == UI shows "mapa nestažena". */
sealed interface MapTileSourceState {
    data object NotDownloaded : MapTileSourceState
    data object Preparing : MapTileSourceState
    data class Ready(val source: MapTileSource) : MapTileSourceState
    data class Error(val message: String?) : MapTileSourceState
}
