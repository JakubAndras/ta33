package com.example.ta33.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ta33.domain.model.CollectedControl
import com.example.ta33.domain.model.DenikStop
import com.example.ta33.domain.model.DenikUiState
import com.example.ta33.domain.model.RouteItinerary
import com.example.ta33.domain.model.RunSession
import com.example.ta33.domain.model.StopStatus
import com.example.ta33.domain.model.WaypointKind
import com.example.ta33.domain.repository.RouteCatalogRepository
import com.example.ta33.domain.repository.RunRepository
import com.example.ta33.domain.usecase.ObserveAppStateUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Composes the route itinerary catalog (RD-00) with the run state ([RunRepository] +
 * [ObserveAppStateUseCase] active run/route) into [DenikUiState] for the `VariantPrehled` design.
 *
 * The displayed route defaults to the active route but can be switched with [toggle]/[bindSelected]
 * without changing the active run. Per-control status is derived only while the displayed route is
 * the active run's route; otherwise every stop is UPCOMING (a plain preview of the other route).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DenikViewModel(
    private val observeApp: ObserveAppStateUseCase,
    private val runRepo: RunRepository,
    private val catalog: RouteCatalogRepository,
) : ViewModel() {

    private val selectedRouteId = MutableStateFlow<String?>(null)
    private var itineraries: List<RouteItinerary> = emptyList()

    private val _state = MutableStateFlow(DenikUiState())
    val state: StateFlow<DenikUiState> = _state.asStateFlow()

    init {
        combine(
            observeApp(),
            catalog.observeItineraries(),
            selectedRouteId,
        ) { app, itins, selected -> Selection(app.activeRouteId, app.activeRunId, itins, selected) }
            .onEach { itineraries = it.itineraries }
            .flatMapLatest { sel ->
                val routeId = sel.selected ?: sel.activeRouteId ?: sel.itineraries.firstOrNull()?.routeId
                val itinerary = sel.itineraries.firstOrNull { it.routeId == routeId }
                val canSwitch = sel.itineraries.size > 1
                val runId = sel.activeRunId
                val showRun = itinerary != null && routeId == sel.activeRouteId && runId != null
                when {
                    itinerary == null -> flowOf(DenikUiState(canSwitch = canSwitch, loading = false))
                    showRun -> combine(
                        runRepo.observeRun(runId!!),
                        runRepo.observeCollected(runId),
                    ) { run, collected -> build(itinerary, run, collected, canSwitch) }
                    else -> flowOf(build(itinerary, run = null, collected = emptyList(), canSwitch = canSwitch))
                }
            }
            .onEach { _state.value = it }
            .launchIn(viewModelScope)
    }

    /** Switch the displayed route to [routeId] (does not touch the active run). */
    fun bindSelected(routeId: String) {
        selectedRouteId.value = routeId
    }

    /** Přepnout pill: flip to the other available route. No-op with a single route. */
    fun toggle() {
        val current = _state.value.routeId ?: return
        val next = itineraries.firstOrNull { it.routeId != current } ?: return
        bindSelected(next.routeId)
    }

    private fun build(
        itinerary: RouteItinerary,
        run: RunSession?,
        collected: List<CollectedControl>,
        canSwitch: Boolean,
    ): DenikUiState {
        val collectedIds = collected.mapTo(HashSet()) { it.controlId }
        val started = run?.startedAtMillis != null

        // First uncollected control (by ordinal) is the "next" node — only once the run has started
        // (consistent with the START stop, which is DONE only when started).
        val nextControlOrdinal = if (started) {
            itinerary.waypoints
                .filter { it.kind == WaypointKind.CONTROL }
                .firstOrNull { controlId(itinerary.routeId, it.controlOrdinal) !in collectedIds }
                ?.controlOrdinal
        } else {
            null
        }

        val stopWaypoints = itinerary.waypoints.filter {
            it.kind == WaypointKind.START || it.kind == WaypointKind.CONTROL || it.kind == WaypointKind.FINISH
        }

        val stops = stopWaypoints.mapIndexed { index, wp ->
            val next = stopWaypoints.getOrNull(index + 1)
            val status = when (wp.kind) {
                WaypointKind.START -> if (started) StopStatus.DONE else StopStatus.UPCOMING
                WaypointKind.FINISH -> if (run?.finishedAtMillis != null) StopStatus.DONE else StopStatus.UPCOMING
                WaypointKind.CONTROL -> when {
                    controlId(itinerary.routeId, wp.controlOrdinal) in collectedIds -> StopStatus.DONE
                    wp.controlOrdinal == nextControlOrdinal -> StopStatus.NEXT
                    else -> StopStatus.UPCOMING
                }
                WaypointKind.WAYPOINT -> StopStatus.UPCOMING
            }
            DenikStop(
                kind = wp.kind,
                label = when (wp.kind) {
                    WaypointKind.START -> "Start"
                    WaypointKind.FINISH -> "Cíl"
                    else -> "Kontrola ${wp.controlOrdinal}"
                },
                name = wp.name,
                km = wp.km,
                controlOrdinal = wp.controlOrdinal,
                status = status,
                segmentKm = next?.let { it.km - wp.km },
                startTimeMillis = if (wp.kind == WaypointKind.START) run?.startedAtMillis else null,
                isFinish = wp.kind == WaypointKind.FINISH,
            )
        }

        return DenikUiState(
            routeId = itinerary.routeId,
            shortId = itinerary.shortId,
            distanceKm = itinerary.distanceKm,
            startTimeMillis = run?.startedAtMillis,
            finishTimeMillis = run?.finishedAtMillis,
            ascentMeters = itinerary.ascentMeters,
            descentMeters = itinerary.descentMeters,
            elevation = itinerary.elevation,
            stops = stops,
            canSwitch = canSwitch,
            loading = false,
        )
    }

    /** Control id scheme shared with the run pipeline (see DevSeed / ControlPoint). */
    private fun controlId(routeId: String, ordinal: Int?): String = "$routeId-kp$ordinal"

    private data class Selection(
        val activeRouteId: String?,
        val activeRunId: String?,
        val itineraries: List<RouteItinerary>,
        val selected: String?,
    )
}
