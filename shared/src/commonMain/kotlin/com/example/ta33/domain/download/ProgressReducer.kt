package com.example.ta33.domain.download

object ProgressReducer {
    fun replaceItem(state: OfflinePackageProgress, item: DownloadItemProgress): OfflinePackageProgress {
        val hasItem = state.items.any { it.id == item.id }
        val items =
            if (hasItem) state.items.map { if (it.id == item.id) item else it } else state.items + item
        return aggregate(state.copy(items = items))
    }

    fun aggregate(state: OfflinePackageProgress): OfflinePackageProgress {
        val items = state.items
        val overall = when {
            items.isEmpty() -> DownloadStatus.IDLE
            items.any { it.status == DownloadStatus.ERROR } -> DownloadStatus.ERROR
            items.all { it.status == DownloadStatus.DONE } -> DownloadStatus.DONE
            items.any { it.status == DownloadStatus.DOWNLOADING } -> DownloadStatus.DOWNLOADING
            items.any { it.status == DownloadStatus.PAUSED } -> DownloadStatus.PAUSED
            else -> DownloadStatus.IDLE
        }
        // Byte-weighted when every item knows its total; else item-count average of per-item fractions.
        val fraction = if (items.isNotEmpty() && items.all { it.totalBytes != null }) {
            val total = items.sumOf { it.totalBytes ?: 0L }
            if (total == 0L) {
                items.count { it.status == DownloadStatus.DONE }.toDouble() / items.size
            } else {
                items.sumOf { it.bytesDownloaded }.toDouble() / total
            }
        } else if (items.isNotEmpty()) {
            items.sumOf { it.fraction } / items.size
        } else {
            0.0
        }
        return state.copy(overallStatus = overall, overallFraction = fraction.coerceIn(0.0, 1.0))
    }
}
