package com.example.ta33.domain.usecase

import com.example.ta33.core.IdGenerator
import com.example.ta33.core.TimeProvider
import com.example.ta33.domain.geo.GeoUtils
import com.example.ta33.domain.model.CollectedControl
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.repository.RouteRepository
import com.example.ta33.domain.repository.RunRepository

sealed interface CollectResult {
    data class Collected(val control: CollectedControl) : CollectResult
    data object AlreadyCollected : CollectResult
    data object OutOfRange : CollectResult
    data object UnknownControl : CollectResult
}

class CollectControlUseCase(
    private val runRepo: RunRepository,
    private val routeRepo: RouteRepository,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) {
    /**
     * Collects a control for a run. When [location] is null the geofence check is skipped
     * (GPS acquisition is a later FR); otherwise the control is only collected within its radius.
     */
    suspend operator fun invoke(
        runId: String,
        controlId: String,
        location: GeoPoint? = null,
    ): CollectResult {
        val control = routeRepo.getControl(controlId) ?: return CollectResult.UnknownControl
        if (location != null && !GeoUtils.isWithinRadius(control, location)) {
            return CollectResult.OutOfRange
        }
        val collected = CollectedControl(
            id = ids.newId(),
            runSessionId = runId,
            controlId = controlId,
            collectedAtMillis = time.nowMillis(),
        )
        val added = runRepo.addCollected(collected)
        return if (added) CollectResult.Collected(collected) else CollectResult.AlreadyCollected
    }
}
