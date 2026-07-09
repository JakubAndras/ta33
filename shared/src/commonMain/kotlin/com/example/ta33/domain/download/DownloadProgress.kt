package com.example.ta33.domain.download

enum class DownloadStatus {
    IDLE, DOWNLOADING, PAUSED, DONE, ERROR;

    companion object {
        fun fromDb(raw: String?): DownloadStatus =
            entries.firstOrNull { it.name == raw } ?: IDLE
    }
}

data class DownloadItemProgress(
    val id: String, // "content" | "tiles:<id>"
    val label: String,
    val status: DownloadStatus = DownloadStatus.IDLE,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long? = null, // null when host omits content-length
    val error: String? = null,
) {
    val fraction: Double
        get() = when {
            status == DownloadStatus.DONE -> 1.0
            totalBytes == null || totalBytes == 0L -> 0.0
            else -> (bytesDownloaded.toDouble() / totalBytes).coerceIn(0.0, 1.0)
        }
}

data class OfflinePackageProgress(
    val items: List<DownloadItemProgress> = emptyList(),
    val overallStatus: DownloadStatus = DownloadStatus.IDLE,
    val overallFraction: Double = 0.0,
)
