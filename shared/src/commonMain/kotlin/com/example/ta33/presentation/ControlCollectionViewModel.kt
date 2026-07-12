package com.example.ta33.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ta33.data.location.LocationPermissionController   // FR-05 (status only)
import com.example.ta33.domain.model.CollectionCandidate
import com.example.ta33.domain.model.LocationPermissionStatus
import com.example.ta33.domain.usecase.CollectControlUseCase          // FR-02 (reused)
import com.example.ta33.domain.usecase.CollectResult
import com.example.ta33.domain.usecase.ObserveCollectionCandidateUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Outcome of the last confirm() - the "green screen" (later UI) renders JustCollected. */
sealed interface CollectionOutcome {
    data class JustCollected(val controlId: String) : CollectionOutcome
    data object AlreadyCollected : CollectionOutcome
    data object OutOfRange : CollectionOutcome
    data object UnknownControl : CollectionOutcome
}

data class ControlCollectionUiState(
    val permissionStatus: LocationPermissionStatus = LocationPermissionStatus.NOT_DETERMINED,
    val candidate: CollectionCandidate? = null,   // the control the app OFFERS to collect
    val isCollecting: Boolean = false,            // confirm() in flight
    val lastResult: CollectionOutcome? = null,    // consumed by UI; cleared on next candidate
)

class ControlCollectionViewModel(
    private val observeCandidate: ObserveCollectionCandidateUseCase,
    private val collectControl: CollectControlUseCase,         // FR-02, reused
    private val permission: LocationPermissionController,      // FR-05, status only
) : ViewModel() {

    private val _state = MutableStateFlow(ControlCollectionUiState())
    val state: StateFlow<ControlCollectionUiState> = _state.asStateFlow()

    private var runId: String? = null
    private var bindJob: Job? = null

    fun bind(runId: String, routeId: String) {
        this.runId = runId
        bindJob?.cancel()
        bindJob = viewModelScope.launch {
            permission.observeStatus()
                .onEach { s -> _state.update { it.copy(permissionStatus = s) } }.launchIn(this)
            observeCandidate(runId, routeId)
                .onEach { c -> _state.update { it.copy(candidate = c, lastResult = if (c != null) null else it.lastResult) } }
                .launchIn(this)
        }
    }

    /** Explicit user action ("odklikne"). Writes the current candidate via FR-02. NO auto-collect. */
    fun confirm() {
        val rid = runId ?: return
        val candidate = _state.value.candidate ?: return
        if (_state.value.isCollecting) return
        _state.update { it.copy(isCollecting = true) }
        viewModelScope.launch {
            val outcome = when (val r = collectControl(rid, candidate.control.id, candidate.atLocation)) {
                is CollectResult.Collected -> CollectionOutcome.JustCollected(r.control.controlId)
                CollectResult.AlreadyCollected -> CollectionOutcome.AlreadyCollected
                CollectResult.OutOfRange -> CollectionOutcome.OutOfRange
                CollectResult.UnknownControl -> CollectionOutcome.UnknownControl
            }
            _state.update { it.copy(isCollecting = false, lastResult = outcome) }
            // No manual candidate clear needed: observeCollected re-emits → candidate flips to null,
            // and FR-04's deník recomputes automatically.
        }
    }
}
