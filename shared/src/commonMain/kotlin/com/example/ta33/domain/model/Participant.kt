package com.example.ta33.domain.model

data class Participant(
    val id: String,
    val displayName: String? = null, // anonymous in Etapa 1; gains identity in Etapa 2
    val createdAtMillis: Long,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)
