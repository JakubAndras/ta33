package com.example.ta33.domain.geo

import com.example.ta33.domain.model.CollectionCandidate
import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.GeofenceConfig
import com.example.ta33.domain.model.GeoPosition

/** Cross-emission state held by the CALLER (use-case), so the evaluator stays pure/deterministic. */
data class ProximityState(
    val offeredControlId: String? = null, // currently offered candidate (post-debounce), sticky via hysteresis
    val pendingControlId: String? = null, // nearest in-range control being debounced
    val pendingStreak: Int = 0,           // consecutive in-range fixes for pendingControlId
)

/** Result of one evaluation: the next state to carry + the candidate to emit (or null). */
data class ProximityEvaluation(
    val state: ProximityState,
    val candidate: CollectionCandidate?,
)

/**
 * PURE, deterministic geofence decision (FR-08). No coroutines, no I/O, no platform APIs.
 * Reuses FR-02 GeoUtils.distanceMeters (clean haversine, already unit-tested).
 *
 * Rules (see plan §3.2):
 *  - Accuracy gate: a fix worse than config.maxAccuracyMeters cannot promote a NEW candidate;
 *    it holds the current offered one (no flip on a bad fix).
 *  - Enter: a fresh control becomes pending at distance <= radiusMeters; it is offered only after
 *    config.minConsecutiveFixes consecutive in-range fixes (debounce).
 *  - Exit hysteresis: an already-offered control stays offered until distance > radius + exitMargin.
 *  - Any-order: only "uncollected" filters candidates - ordinal is irrelevant here.
 */
class ProximityEvaluator(private val config: GeofenceConfig = GeofenceConfig()) {

    fun evaluate(
        previous: ProximityState,
        position: GeoPosition,
        controls: List<ControlPoint>,
        collectedIds: Set<String>,
    ): ProximityEvaluation {
        val uncollected = controls.filter { it.id !in collectedIds }

        fun candidateFor(control: ControlPoint, distance: Double) =
            CollectionCandidate(control, distance, position.accuracyMeters, position.location)

        // 1) Exit hysteresis first: keep a held candidate sticky until it clearly exits.
        val held = previous.offeredControlId
            ?.let { id -> uncollected.firstOrNull { it.id == id } }
            ?.let { c -> c to GeoUtils.distanceMeters(c.location, position.location) }
        if (held != null && held.second <= held.first.radiusMeters + config.exitMarginMeters) {
            // Still within the (loose) exit threshold → keep offering; reset pending.
            return ProximityEvaluation(
                state = ProximityState(offeredControlId = held.first.id, pendingControlId = null, pendingStreak = 0),
                candidate = candidateFor(held.first, held.second),
            )
        }

        // 2) Poor-accuracy gate: cannot promote a NEW candidate on an untrustworthy fix.
        //    (Held candidate already returned above; here we simply hold the previous state.)
        if (position.accuracyMeters > config.maxAccuracyMeters) {
            return ProximityEvaluation(state = previous.copy(offeredControlId = previous.offeredControlId), candidate = null)
        }

        // 3) Find the nearest uncollected control within the (tight) enter radius.
        val nearest = uncollected
            .map { it to GeoUtils.distanceMeters(it.location, position.location) }
            .filter { (c, d) -> d <= c.radiusMeters }
            .minByOrNull { it.second }

        if (nearest == null) {
            return ProximityEvaluation(state = ProximityState(), candidate = null)
        }

        val (control, distance) = nearest
        val streak = if (previous.pendingControlId == control.id) previous.pendingStreak + 1 else 1

        return if (streak >= config.minConsecutiveFixes) {
            ProximityEvaluation(
                state = ProximityState(offeredControlId = control.id, pendingControlId = null, pendingStreak = 0),
                candidate = candidateFor(control, distance),
            )
        } else {
            // Debouncing: in range but not yet offered.
            ProximityEvaluation(
                state = ProximityState(offeredControlId = null, pendingControlId = control.id, pendingStreak = streak),
                candidate = null,
            )
        }
    }
}
