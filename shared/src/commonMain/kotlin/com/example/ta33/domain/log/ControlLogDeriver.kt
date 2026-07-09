package com.example.ta33.domain.log

import com.example.ta33.domain.model.CollectedControl
import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.ControlPointState
import com.example.ta33.domain.model.LogUiState
import com.example.ta33.domain.model.RunLogEntry
import com.example.ta33.domain.model.RunSession

/**
 * PURE, deterministic deník derivation. No repositories, no coroutines, no platform APIs.
 * Single source of truth for the control-state model (FR-04).
 */
object ControlLogDeriver {

    /**
     * @param controls  route controls (expected ordered by ordinal; sorted defensively here)
     * @param collected collected controls for this run (FR-08 output; may be empty / out of order)
     * @param run       the run session, or null if not started (FR-02/FR-09 timing consumed only)
     */
    fun deriveLog(
        controls: List<ControlPoint>,
        collected: List<CollectedControl>,
        run: RunSession?,
    ): LogUiState {
        val ordered = controls.sortedWith(compareBy({ it.ordinal }, { it.id })) // defensive, stable
        val collectedByControl = collected.associateBy { it.controlId }         // last-wins if dup
        val startedAt = run?.startedAtMillis

        // First uncollected control by ordinal = ACTIVE; later uncollected = LOCKED.
        val firstUncollectedId = ordered.firstOrNull { it.id !in collectedByControl }?.id

        val entries = ordered.map { control ->
            val hit = collectedByControl[control.id]
            val state = when {
                hit != null -> ControlPointState.DONE
                control.id == firstUncollectedId -> ControlPointState.ACTIVE
                else -> ControlPointState.LOCKED
            }
            val collectedAt = hit?.collectedAtMillis
            RunLogEntry(
                control = control,
                state = state,
                collectedAtMillis = collectedAt,
                splitMillis = if (collectedAt != null && startedAt != null) collectedAt - startedAt else null,
            )
        }

        val total = ordered.size
        val collectedCount = ordered.count { it.id in collectedByControl }
        val isComplete = total > 0 && collectedCount == total
        val isRunFinished = run?.finishedAtMillis != null
        val nextControl = ordered.firstOrNull { it.id == firstUncollectedId }

        val finishState = when {
            isRunFinished -> ControlPointState.FINISH
            isComplete -> ControlPointState.ACTIVE // all collected, head to finish (scan QR)
            else -> ControlPointState.LOCKED
        }

        return LogUiState(
            entries = entries,
            collectedCount = collectedCount,
            totalCount = total,
            nextControl = nextControl,
            finishState = finishState,
            isComplete = isComplete,
            isRunFinished = isRunFinished,
            loading = false,
        )
    }
}
