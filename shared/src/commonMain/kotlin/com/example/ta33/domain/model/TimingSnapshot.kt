package com.example.ta33.domain.model

data class TimingSnapshot(
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
    val elapsedMillis: Long = 0L,
    val splits: List<Split> = emptyList(),
)
