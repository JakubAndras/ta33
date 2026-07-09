package com.example.ta33.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ta33.data.location.LocationStream
import com.example.ta33.domain.log.ControlLogDeriver
import com.example.ta33.domain.map.MapCameraCalculator
import com.example.ta33.domain.map.MapTileConfig
import com.example.ta33.domain.map.MarkerSelector
import com.example.ta33.domain.map.OverlayMapper
import com.example.ta33.domain.map.TileStore
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.GeoPosition
import com.example.ta33.domain.model.LogUiState
import com.example.ta33.domain.model.MapOverlay
import com.example.ta33.domain.model.Trackpoint
import com.example.ta33.domain.usecase.ObserveRouteDetailUseCase
import com.example.ta33.domain.usecase.ObserveRunLogUseCase
import com.example.ta33.domain.usecase.ObserveTrackUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

class MapViewModel(
    private val tileStore: TileStore,
    private val observeRouteDetail: ObserveRouteDetailUseCase, // FR-03
    private val observeRunLog: ObserveRunLogUseCase, // FR-04
    private val observeTrack: ObserveTrackUseCase, // FR-05
    private val locationStream: LocationStream, // FR-05
    private val config: MapTileConfig,
) : ViewModel() {

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private val selectedMarkerId = MutableStateFlow<String?>(null)
    private var job: Job? = null

    /** @param runId null while browsing a route before a run has started. */
    fun bind(routeId: String, runId: String?) {
        job?.cancel()

        // Log source: real run log if a run exists, else derive states from the route's controls.
        val logFlow: Flow<LogUiState> =
            if (runId != null) {
                observeRunLog(runId, routeId)
            } else {
                observeRouteDetail(routeId).map { detail ->
                    val controls = detail?.controls ?: emptyList()
                    ControlLogDeriver.deriveLog(controls, emptyList(), null)
                }
            }

        val trackFlow: Flow<List<Trackpoint>> =
            if (runId != null) observeTrack(runId) else flowOf(emptyList())

        // Live position: start with null so the combined state isn't blocked on the first GPS fix.
        val positionFlow: Flow<GeoPosition?> =
            locationStream.positions().map<GeoPosition, GeoPosition?> { it }.onStart { emit(null) }

        job = combine(
            tileStore.observeTileSource(),
            logFlow,
            positionFlow,
            trackFlow,
            selectedMarkerId,
        ) { tile, log, position, track, selected ->
            val polyline = OverlayMapper.toRoutePolyline(log)
            MapUiState(
                tileSource = tile,
                overlay = MapOverlay(
                    routePolyline = polyline,
                    checkpointMarkers = OverlayMapper.toCheckpointMarkers(log),
                    livePosition = position,
                    breadcrumb = OverlayMapper.toBreadcrumb(track),
                ),
                camera = MapCameraCalculator.initialCamera(polyline, position, config.fallbackFocus),
                isRouteLoaded = polyline.isNotEmpty(),
                selectedMarkerId = selected,
                loading = false,
            )
        }.onEach { _state.value = it }
            .launchIn(viewModelScope)
    }

    fun onMarkerTapped(tap: GeoPoint, maxMeters: Double = 60.0) {
        val marker = MarkerSelector.selectNearest(_state.value.overlay.checkpointMarkers, tap, maxMeters)
        selectedMarkerId.value = marker?.controlId
    }

    fun clearSelection() {
        selectedMarkerId.value = null
    }
}
