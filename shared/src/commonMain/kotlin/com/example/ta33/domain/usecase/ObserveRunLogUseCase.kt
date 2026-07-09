package com.example.ta33.domain.usecase

import com.example.ta33.domain.log.ControlLogDeriver
import com.example.ta33.domain.model.LogUiState
import com.example.ta33.domain.repository.RouteRepository
import com.example.ta33.domain.repository.RunRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveRunLogUseCase(
    private val routes: RouteRepository,
    private val runs: RunRepository,
) {
    operator fun invoke(runId: String, routeId: String): Flow<LogUiState> =
        combine(
            routes.observeRouteWithControls(routeId), // FR-03 seam: Route? with controls by ordinal
            runs.observeRun(runId), // FR-02
            runs.observeCollected(runId), // FR-02
        ) { route, run, collected ->
            ControlLogDeriver.deriveLog(
                controls = route?.controls.orEmpty(),
                collected = collected,
                run = run,
            )
        }
}
