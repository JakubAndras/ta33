package com.example.ta33.domain.geo

import com.example.ta33.domain.model.BreadcrumbConfig
import com.example.ta33.domain.model.GeoPosition

sealed interface BreadcrumbDecision {
    data class Keep(val position: GeoPosition) : BreadcrumbDecision

    enum class DropReason { POOR_ACCURACY, TOO_CLOSE, TOO_SOON }

    data class Drop(val reason: DropReason) : BreadcrumbDecision
}

/** Pure, stateless downsample. Caller holds `lastKept`. Reuses FR-02 GeoUtils (no re-implemented math). */
class BreadcrumbThrottle(private val config: BreadcrumbConfig = BreadcrumbConfig()) {
    fun decide(lastKept: GeoPosition?, candidate: GeoPosition): BreadcrumbDecision {
        if (candidate.accuracyMeters > config.maxAccuracyMeters) {
            return BreadcrumbDecision.Drop(BreadcrumbDecision.DropReason.POOR_ACCURACY)
        }
        if (lastKept == null) return BreadcrumbDecision.Keep(candidate) // first good fix
        val elapsed = candidate.timestampMillis - lastKept.timestampMillis
        if (elapsed < config.minTimeMillis) {
            return BreadcrumbDecision.Drop(BreadcrumbDecision.DropReason.TOO_SOON)
        }
        val moved = GeoUtils.distanceMeters(lastKept.location, candidate.location)
        if (moved < config.minDistanceMeters) {
            return BreadcrumbDecision.Drop(BreadcrumbDecision.DropReason.TOO_CLOSE)
        }
        return BreadcrumbDecision.Keep(candidate)
    }
}
