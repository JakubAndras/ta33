package com.example.ta33.domain.usecase

import com.example.ta33.domain.model.LogUiState
import com.example.ta33.domain.model.OverviewUiState
import com.example.ta33.domain.overview.OverviewComposer
import com.example.ta33.domain.repository.RouteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ObserveOverviewUseCase(
    private val observeAppState: ObserveAppStateUseCase,             // FR-01 (Seam A)
    private val routes: RouteRepository,                            // FR-03 observeRouteSummaries
    private val observeRunLog: ObserveRunLogUseCase,                // FR-04
    private val observePreparation: ObservePreparationStateUseCase, // FR-11
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<OverviewUiState> =
        combine(
            observeAppState(),
            routes.observeRouteSummaries(),
            observePreparation(),
        ) { app, summaries, prep -> Triple(app, summaries, prep) }
            .flatMapLatest { (app, summaries, prep) ->
                val runId = app.activeRunId
                val routeId = app.activeRouteId
                val logFlow: Flow<LogUiState?> =
                    if (runId != null && routeId != null) {
                        observeRunLog(runId, routeId)
                    } else {
                        flowOf(null)
                    }
                logFlow.map { log -> OverviewComposer.compose(app, summaries, log, prep) }
            }
}
