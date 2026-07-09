package com.example.ta33.domain.model

enum class PreparationStatus {
    NOT_STARTED, PREPARING, READY, ERROR;

    companion object {
        fun fromDb(raw: String?): PreparationStatus =
            entries.firstOrNull { it.name == raw } ?: NOT_STARTED
    }
}

data class PreparationState(
    val status: PreparationStatus = PreparationStatus.NOT_STARTED,
    val manifestVersion: Int? = null,
    val readyAtMillis: Long? = null,
)
