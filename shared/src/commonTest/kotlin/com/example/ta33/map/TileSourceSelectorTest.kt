package com.example.ta33.map

import com.example.ta33.domain.download.DownloadItemProgress
import com.example.ta33.domain.download.DownloadStatus
import com.example.ta33.domain.map.MapTileConfig
import com.example.ta33.domain.map.TileSourceSelector
import com.example.ta33.domain.model.PreparationState
import com.example.ta33.domain.model.PreparationStatus
import com.example.ta33.domain.model.TileFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TileSourceSelectorTest {

    private val config = MapTileConfig()

    private fun tile(id: String, status: DownloadStatus = DownloadStatus.DONE) =
        DownloadItemProgress(id = id, label = id, status = status)

    private fun prep(status: PreparationStatus) = PreparationState(status = status)

    @Test
    fun readyWithDoneTile_returnsCandidate() {
        val sel = TileSourceSelector.select(prep(PreparationStatus.READY), listOf(tile("tiles:adrspach")), config)
        val c = assertIs<TileSourceSelector.Selection.Candidate>(sel)
        assertEquals("adrspach", c.tilesetId)
        assertEquals("tiles/adrspach.mbtiles", c.relativePath)
        assertEquals(TileFormat.MBTILES, c.format)
    }

    @Test
    fun readyWithoutDoneTile_returnsNotDownloaded() {
        val sel = TileSourceSelector.select(
            prep(PreparationStatus.READY),
            listOf(tile("tiles:adrspach", DownloadStatus.DOWNLOADING)),
            config,
        )
        assertEquals(TileSourceSelector.Selection.NotDownloaded, sel)
    }

    @Test
    fun preparingWithoutDoneTile_returnsPreparing() {
        val sel = TileSourceSelector.select(
            prep(PreparationStatus.PREPARING),
            listOf(tile("tiles:adrspach", DownloadStatus.DOWNLOADING)),
            config,
        )
        assertEquals(TileSourceSelector.Selection.Preparing, sel)
    }

    @Test
    fun preparingWithDoneTile_returnsCandidateEarly() {
        val sel = TileSourceSelector.select(prep(PreparationStatus.PREPARING), listOf(tile("tiles:adrspach")), config)
        assertIs<TileSourceSelector.Selection.Candidate>(sel)
    }

    @Test
    fun notStarted_returnsNotDownloaded() {
        val sel = TileSourceSelector.select(prep(PreparationStatus.NOT_STARTED), emptyList(), config)
        assertEquals(TileSourceSelector.Selection.NotDownloaded, sel)
    }

    @Test
    fun error_returnsError() {
        val sel = TileSourceSelector.select(prep(PreparationStatus.ERROR), listOf(tile("tiles:adrspach")), config)
        assertIs<TileSourceSelector.Selection.Error>(sel)
    }

    @Test
    fun preferredIdMatch_picksThatTile() {
        val cfg = config.copy(preferredBasemapTilesetId = "beta")
        val sel = TileSourceSelector.select(
            prep(PreparationStatus.READY),
            listOf(tile("tiles:alpha"), tile("tiles:beta")),
            cfg,
        )
        val c = assertIs<TileSourceSelector.Selection.Candidate>(sel)
        assertEquals("beta", c.tilesetId)
    }

    @Test
    fun preferredIdMiss_readyReturnsNotDownloaded() {
        val cfg = config.copy(preferredBasemapTilesetId = "missing")
        val sel = TileSourceSelector.select(prep(PreparationStatus.READY), listOf(tile("tiles:alpha")), cfg)
        assertEquals(TileSourceSelector.Selection.NotDownloaded, sel)
    }

    @Test
    fun multipleDoneTilesNoPreferred_picksFirst() {
        val sel = TileSourceSelector.select(
            prep(PreparationStatus.READY),
            listOf(tile("tiles:alpha"), tile("tiles:beta")),
            config,
        )
        val c = assertIs<TileSourceSelector.Selection.Candidate>(sel)
        assertEquals("alpha", c.tilesetId)
    }

    @Test
    fun ignoresNonTileAssets() {
        val sel = TileSourceSelector.select(
            prep(PreparationStatus.READY),
            listOf(tile("content"), tile("tiles:adrspach")),
            config,
        )
        val c = assertIs<TileSourceSelector.Selection.Candidate>(sel)
        assertEquals("adrspach", c.tilesetId)
    }
}
