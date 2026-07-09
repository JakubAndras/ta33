package com.example.ta33.domain.overview

import com.example.ta33.domain.model.LogUiState
import com.example.ta33.domain.model.OverviewProgress
import com.example.ta33.domain.model.OverviewUiState
import com.example.ta33.domain.model.PreparationState
import com.example.ta33.domain.model.RouteSummary
import com.example.ta33.presentation.navigation.AppUiState

/**
 * PURE, deterministic overview composition (FR-10). No repositories, no coroutines.
 * Composes FR-01 app state + FR-03 route summaries + FR-04 log + FR-11 preparation.
 */
object OverviewComposer {
    fun compose(
        app: AppUiState,
        routeSummaries: List<RouteSummary>,
        log: LogUiState?,               // null when no active run
        preparation: PreparationState,
    ): OverviewUiState {
        val activeRoute = app.activeRouteId
            ?.let { id -> routeSummaries.firstOrNull { it.routeId == id } } // null if stale/missing
        val progress = log?.let {
            OverviewProgress(
                collectedCount = it.collectedCount,
                totalCount = it.totalCount,
                isComplete = it.isComplete,
                isRunFinished = it.isRunFinished,
            )
        }
        return OverviewUiState(
            readiness = app.readiness,
            contentAvailability = app.contentAvailability,
            activeRoute = activeRoute,
            hasActiveRun = app.activeRunId != null,
            progress = progress,
            syncStatus = preparation.status,
            manifestVersion = preparation.manifestVersion,
            readyAtMillis = preparation.readyAtMillis,
            loading = false,
        )
    }
}
