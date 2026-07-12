package com.example.ta33.domain.model

/** One deník row: a control plus its derived state and (optional) split time. */
data class RunLogEntry(
    val control: ControlPoint,
    val state: ControlPointState,        // was RunLogEntry.State (COLLECTED/NEXT/PENDING) - replaced
    val collectedAtMillis: Long? = null, // null unless DONE
    val splitMillis: Long? = null,       // collectedAt - run.startedAt, null unless both known
)
