package com.example.ta33.domain.model

/** A checkpoint split (mezičas) for FR-09. splitMillis == collectedAtMillis - run.startedAtMillis,
 *  the same notion as FR-04 RunLogEntry.splitMillis. */
data class Split(
    val controlId: String,
    val collectedAtMillis: Long,
    val splitMillis: Long,
)
