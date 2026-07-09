package com.example.ta33.domain.model

/** One persisted breadcrumb point, bound to an FR-02 RunSession. */
data class Trackpoint(
    val id: String,
    val runSessionId: String,
    val location: GeoPoint,
    val accuracyMeters: Double,
    val timestampMillis: Long,
    val syncStatus: SyncStatus = SyncStatus.PENDING, // FR-02 convention; never uploaded in Etapa 1
)
