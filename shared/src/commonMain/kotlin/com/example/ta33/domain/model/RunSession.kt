package com.example.ta33.domain.model

data class RunSession(
    val id: String,
    val routeId: String,
    val participantId: String,
    val startedAtMillis: Long? = null,  // set by QR start (FR-09)
    val finishedAtMillis: Long? = null, // set by QR finish (FR-09)
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)
