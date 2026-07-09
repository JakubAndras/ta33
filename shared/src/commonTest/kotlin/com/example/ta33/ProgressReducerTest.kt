package com.example.ta33

import com.example.ta33.domain.download.DownloadItemProgress
import com.example.ta33.domain.download.DownloadStatus
import com.example.ta33.domain.download.OfflinePackageProgress
import com.example.ta33.domain.download.ProgressReducer
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressReducerTest {

    private fun item(
        id: String,
        status: DownloadStatus,
        bytes: Long = 0,
        total: Long? = null,
    ) = DownloadItemProgress(id = id, label = id, status = status, bytesDownloaded = bytes, totalBytes = total)

    @Test
    fun emptyList_isIdleZero() {
        val state = ProgressReducer.aggregate(OfflinePackageProgress())
        assertEquals(DownloadStatus.IDLE, state.overallStatus)
        assertEquals(0.0, state.overallFraction)
    }

    @Test
    fun anyError_overallError() {
        val state = OfflinePackageProgress(
            items = listOf(
                item("a", DownloadStatus.DONE, 10, 10),
                item("b", DownloadStatus.ERROR),
            ),
        )
        assertEquals(DownloadStatus.ERROR, ProgressReducer.aggregate(state).overallStatus)
    }

    @Test
    fun allDone_overallDone() {
        val state = OfflinePackageProgress(
            items = listOf(
                item("a", DownloadStatus.DONE, 10, 10),
                item("b", DownloadStatus.DONE, 20, 20),
            ),
        )
        val agg = ProgressReducer.aggregate(state)
        assertEquals(DownloadStatus.DONE, agg.overallStatus)
        assertEquals(1.0, agg.overallFraction)
    }

    @Test
    fun anyDownloading_overallDownloading() {
        val state = OfflinePackageProgress(
            items = listOf(
                item("a", DownloadStatus.DONE, 10, 10),
                item("b", DownloadStatus.DOWNLOADING, 5, 10),
            ),
        )
        assertEquals(DownloadStatus.DOWNLOADING, ProgressReducer.aggregate(state).overallStatus)
    }

    @Test
    fun paused_whenNoDownloadingButPausedPresent() {
        val state = OfflinePackageProgress(
            items = listOf(
                item("a", DownloadStatus.PAUSED),
                item("b", DownloadStatus.IDLE),
            ),
        )
        assertEquals(DownloadStatus.PAUSED, ProgressReducer.aggregate(state).overallStatus)
    }

    @Test
    fun byteWeightedFraction_whenAllTotalsKnown() {
        val state = OfflinePackageProgress(
            items = listOf(
                item("a", DownloadStatus.DONE, 100, 100),
                item("b", DownloadStatus.DOWNLOADING, 50, 100),
            ),
        )
        // (100 + 50) / (100 + 100) = 0.75
        assertEquals(0.75, ProgressReducer.aggregate(state).overallFraction)
    }

    @Test
    fun itemAverageFraction_whenATotalIsNull() {
        val state = OfflinePackageProgress(
            items = listOf(
                item("a", DownloadStatus.DONE, 100, 100), // fraction 1.0
                item("b", DownloadStatus.DOWNLOADING, 50, null), // fraction 0.0 (unknown total)
            ),
        )
        // average of (1.0, 0.0) = 0.5
        assertEquals(0.5, ProgressReducer.aggregate(state).overallFraction)
    }

    @Test
    fun replaceItem_updatesExistingAndAppendsNew() {
        var state = OfflinePackageProgress(items = listOf(item("a", DownloadStatus.IDLE)))
        state = ProgressReducer.replaceItem(state, item("a", DownloadStatus.DONE, 10, 10))
        assertEquals(1, state.items.size)
        assertEquals(DownloadStatus.DONE, state.items.first().status)

        state = ProgressReducer.replaceItem(state, item("b", DownloadStatus.DOWNLOADING, 5, 10))
        assertEquals(2, state.items.size)
        assertEquals(DownloadStatus.DOWNLOADING, state.overallStatus)
    }
}
