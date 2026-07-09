package com.example.ta33.presentation.navigation

import com.example.ta33.domain.model.PreparationState
import com.example.ta33.domain.model.PreparationStatus
import com.example.ta33.domain.model.Route
import com.example.ta33.domain.model.RunSession
import com.example.ta33.domain.route.ActiveRouteResolver

object AppStateReducer {
    fun reduce(
        routes: List<Route>,
        activeRun: RunSession?,
        selectedRouteId: String?,
        preparation: PreparationState,
        resolver: StartDestinationResolver,
    ): AppUiState {
        val routesPresent = routes.isNotEmpty()
        // Persisted Preparation flag (FR-11) is the readiness source of truth; falls back to the
        // routes-present proxy when preparation was never run / errored (e.g. side-loaded content).
        val readiness = when (preparation.status) {
            PreparationStatus.READY -> AppReadiness.READY
            PreparationStatus.PREPARING -> AppReadiness.PREPARING
            PreparationStatus.ERROR, PreparationStatus.NOT_STARTED ->
                if (routesPresent) AppReadiness.READY else AppReadiness.NOT_READY
        }
        val availability = when {
            preparation.status == PreparationStatus.READY -> ContentAvailability.PRESENT
            routesPresent -> ContentAvailability.PRESENT
            else -> ContentAvailability.ABSENT
        }
        val activeRouteId = ActiveRouteResolver.resolve(
            activeRunRouteId = activeRun?.routeId,
            selectedRouteId = selectedRouteId,
            availableRouteIds = routes.map { it.id },
        )
        val activeRunId = activeRun?.id
        return AppUiState(
            readiness = readiness,
            contentAvailability = availability,
            activeRouteId = activeRouteId,
            activeRunId = activeRunId,
            startDestination = resolver.resolve(readiness, activeRunId),
        )
    }
}
