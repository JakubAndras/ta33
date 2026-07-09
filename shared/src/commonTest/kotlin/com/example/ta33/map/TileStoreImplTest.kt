package com.example.ta33.map

import app.cash.turbine.turbineScope
import com.example.ta33.FakeFileStorage
import com.example.ta33.FakePreparationRepository
import com.example.ta33.data.map.TileStoreImpl
import com.example.ta33.domain.download.DownloadItemProgress
import com.example.ta33.domain.download.DownloadStatus
import com.example.ta33.domain.map.MapTileConfig
import com.example.ta33.domain.model.MapTileSourceState
import com.example.ta33.domain.model.PreparationState
import com.example.ta33.domain.model.PreparationStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class TileStoreImplTest {

    private val config = MapTileConfig()

    private fun doneTile(id: String = "tiles:adrspach") =
        DownloadItemProgress(id = id, label = id, status = DownloadStatus.DONE)

    @Test
    fun candidateWithExistingFile_resolvesReadyWithAbsolutePath() = runTest {
        val files = FakeFileStorage().apply { seed("tiles/adrspach.mbtiles", ByteArray(4)) }
        val prep = FakePreparationRepository(PreparationState(PreparationStatus.READY, 1)).apply {
            seedAsset(doneTile())
        }
        val store = TileStoreImpl(files, prep, config)

        val state = store.resolveTileSource()
        val ready = assertIs<MapTileSourceState.Ready>(state)
        assertEquals("adrspach", ready.source.tilesetId)
        assertEquals("/fake/tiles/adrspach.mbtiles", ready.source.absolutePath)
    }

    @Test
    fun candidateWithMissingFile_resolvesNotDownloaded() = runTest {
        val files = FakeFileStorage() // no file seeded
        val prep = FakePreparationRepository(PreparationState(PreparationStatus.READY, 1)).apply {
            seedAsset(doneTile())
        }
        val store = TileStoreImpl(files, prep, config)

        assertEquals(MapTileSourceState.NotDownloaded, store.resolveTileSource())
    }

    @Test
    fun observeTileSource_reEmitsWhenPreparationChanges() = runTest {
        val files = FakeFileStorage().apply { seed("tiles/adrspach.mbtiles", ByteArray(4)) }
        val prep = FakePreparationRepository(PreparationState(PreparationStatus.NOT_STARTED)).apply {
            seedAsset(doneTile())
        }
        val store = TileStoreImpl(files, prep, config)

        turbineScope {
            val states = store.observeTileSource().testIn(backgroundScope)
            runCurrent()
            assertEquals(MapTileSourceState.NotDownloaded, states.awaitItem())

            prep.set(PreparationState(PreparationStatus.READY, 1))
            val ready = assertIs<MapTileSourceState.Ready>(states.awaitItem())
            assertEquals("adrspach", ready.source.tilesetId)
            states.cancel()
        }
    }
}
