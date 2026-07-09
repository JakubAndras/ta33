package com.example.ta33.domain.model

/**
 * Full deník state (FR-04). Pure read model returned by ControlLogDeriver.deriveLog(...)
 * and exposed by RunLogViewModel as StateFlow<LogUiState>. No UI/formatting concerns.
 */
data class LogUiState(
    val entries: List<RunLogEntry> = emptyList(), // one per control, ordered by ordinal
    val collectedCount: Int = 0,                  // e.g. 2
    val totalCount: Int = 0,                      // e.g. 5
    val nextControl: ControlPoint? = null,        // first uncollected by ordinal, null if all done
    val finishState: ControlPointState = ControlPointState.LOCKED,
    val isComplete: Boolean = false,              // proof of completion: all controls collected
    val isRunFinished: Boolean = false,           // finish QR scanned (FR-09), consumed only
    val loading: Boolean = true,                  // VM sets false after first emission
) {
    /** Dev convenience only. Final pluralization/units are a UI/localization concern (deferred). */
    val progressLabel: String get() = "$collectedCount z $totalCount" // e.g. "2 z 5"
}
