package com.example.ta33.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ta33.data.location.LocationPermissionController
import com.example.ta33.data.location.LocationStream
import com.example.ta33.data.location.LocationTrackingController
import com.example.ta33.domain.model.GeoPosition
import com.example.ta33.domain.model.LocationPermissionStatus
import com.example.ta33.domain.model.Trackpoint
import com.example.ta33.domain.usecase.ObserveTrackUseCase
import com.example.ta33.domain.usecase.RecordBreadcrumbUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LiveLocationUiState(
    val permissionStatus: LocationPermissionStatus = LocationPermissionStatus.NOT_DETERMINED,
    val currentPosition: GeoPosition? = null,   // raw/live (for FR-06 dot)
    val track: List<Trackpoint> = emptyList(),  // persisted, downsampled (for FR-06 polyline)
    val isTracking: Boolean = false,
)

class LiveLocationViewModel(
    private val locationStream: LocationStream,
    private val permission: LocationPermissionController,
    private val recordBreadcrumb: RecordBreadcrumbUseCase,
    private val observeTrack: ObserveTrackUseCase,
    private val tracking: LocationTrackingController,
) : ViewModel() {
    private val _state = MutableStateFlow(LiveLocationUiState())
    val state: StateFlow<LiveLocationUiState> = _state.asStateFlow()

    private var bindJob: Job? = null
    private var recordJob: Job? = null
    private var recordingRunId: String? = null

    fun bind(runId: String) {
        bindJob?.cancel()
        bindJob = viewModelScope.launch {
            permission.observeStatus()
                .onEach { s -> _state.update { it.copy(permissionStatus = s) } }.launchIn(this)
            locationStream.positions()
                .onEach { p -> _state.update { it.copy(currentPosition = p) } }.launchIn(this)
            observeTrack(runId)
                .onEach { t -> _state.update { it.copy(track = t) } }.launchIn(this)
        }
        startRecording(runId)
    }

    fun startRecording(runId: String) {
        if (recordJob?.isActive == true && recordingRunId == runId) return
        recordJob?.cancel() // switching runs: stop recording the previous run first
        recordingRunId = runId
        tracking.start() // platform seam (foreground/background)
        _state.update { it.copy(isTracking = true) }
        recordJob = recordBreadcrumb.record(runId).launchIn(viewModelScope)
    }

    fun stopRecording() {
        recordJob?.cancel()
        recordJob = null
        recordingRunId = null
        tracking.stop()
        _state.update { it.copy(isTracking = false) }
    }
}
