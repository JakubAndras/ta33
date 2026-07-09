package com.example.ta33.domain.model

data class CollectedControl(
    val id: String,
    val runSessionId: String,
    val controlId: String,
    val collectedAtMillis: Long,        // wall-clock; split time = collectedAt - run.startedAt
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)
