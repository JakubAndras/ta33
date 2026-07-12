package com.example.ta33.domain.usecase

import com.example.ta33.data.location.LocationStream          // FR-05 (shared GPS)
import com.example.ta33.domain.geo.ProximityEvaluator
import com.example.ta33.domain.geo.ProximityState
import com.example.ta33.domain.model.CollectedControl
import com.example.ta33.domain.model.CollectionCandidate
import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.GeoPosition
import com.example.ta33.domain.model.RunSession
import com.example.ta33.domain.repository.RouteRepository       // FR-03
import com.example.ta33.domain.repository.RunRepository         // FR-02
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

class ObserveCollectionCandidateUseCase(
    private val locationStream: LocationStream,   // FR-05 - REUSED shared stream, no new provider
    private val routes: RouteRepository,          // FR-03 observeRouteWithControls
    private val runs: RunRepository,              // FR-02 observeCollected / observeRun
    private val evaluator: ProximityEvaluator,
) {
    private data class Inputs(
        val position: GeoPosition,
        val controls: List<ControlPoint>,
        val collectedIds: Set<String>,
        val run: RunSession?,
    )
    private data class Acc(val state: ProximityState = ProximityState(), val candidate: CollectionCandidate? = null)

    operator fun invoke(runId: String, routeId: String): Flow<CollectionCandidate?> =
        combine(
            locationStream.positions(),                    // fast: each GPS fix
            routes.observeRouteWithControls(routeId),      // Route? (controls ordered by ordinal)
            runs.observeCollected(runId),                  // List<CollectedControl>
            runs.observeRun(runId),                        // RunSession?
        ) { position, route, collected, run ->
            Inputs(
                position = position,
                controls = route?.controls.orEmpty(),
                collectedIds = collected.map(CollectedControl::controlId).toSet(),
                run = run,
            )
        }
            .scan(Acc()) { acc, input ->
                val runActive = input.run?.startedAtMillis != null && input.run.finishedAtMillis == null
                if (!runActive || input.controls.isEmpty()) {
                    Acc(state = ProximityState(), candidate = null)   // reset hysteresis when not collecting
                } else {
                    val eval = evaluator.evaluate(acc.state, input.position, input.controls, input.collectedIds)
                    Acc(state = eval.state, candidate = eval.candidate)
                }
            }
            .map { it.candidate }
            .distinctUntilChanged()
}
