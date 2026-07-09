package com.example.ta33.domain.model

/** Prepared for Etapa 2 server sync; in Etapa 1 everything stays PENDING and is never uploaded. */
enum class SyncStatus {
    PENDING, SYNCED, FAILED;

    companion object {
        fun fromDb(raw: String?): SyncStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}
