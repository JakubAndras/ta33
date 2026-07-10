package com.example.ta33.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ta33.data.connectivity.ConnectivityMonitor
import com.example.ta33.domain.download.DownloadStatus
import com.example.ta33.domain.download.OfflinePackageProgress
import com.example.ta33.domain.model.NetworkPreference
import com.example.ta33.domain.model.NetworkType
import com.example.ta33.domain.model.PreparationState
import com.example.ta33.domain.usecase.ObservePreparationStateUseCase
import com.example.ta33.domain.usecase.PrepareOfflinePackageUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

data class DownloadUiState(
    val progress: OfflinePackageProgress = OfflinePackageProgress(),
    val networkPreference: NetworkPreference = NetworkPreference.WIFI_ONLY,
    val currentNetworkType: NetworkType = NetworkType.NONE,
    val blockedByNetwork: Boolean = false,
    val preparation: PreparationState = PreparationState(),
)

class DownloadViewModel(
    private val prepare: PrepareOfflinePackageUseCase,
    private val observePreparation: ObservePreparationStateUseCase,
    private val connectivity: ConnectivityMonitor,
) : ViewModel() {
    private val _state = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        connectivity.observe().onEach { type ->
            _state.update {
                it.copy(
                    currentNetworkType = type,
                    blockedByNetwork = !PrepareOfflinePackageUseCase.networkAllows(it.networkPreference, type),
                )
            }
            // auto-pause if preference no longer satisfied mid-download
            if (_state.value.blockedByNetwork) pause()
        }.launchIn(viewModelScope)
        observePreparation().onEach { p -> _state.update { it.copy(preparation = p) } }.launchIn(viewModelScope)
    }

    fun setNetworkPreference(pref: NetworkPreference) {
        _state.update {
            it.copy(
                networkPreference = pref,
                blockedByNetwork = !PrepareOfflinePackageUseCase.networkAllows(pref, it.currentNetworkType),
            )
        }
        // Tightening the preference mid-download must also stop a run on a now-disallowed network.
        if (_state.value.blockedByNetwork) pause()
    }

    fun start() = launchDownload()

    fun resume() = launchDownload() // resume = relaunch; use-case skips done items

    fun retry() = launchDownload()

    fun pause() {
        job?.cancel()
        job = null
        // A cancelled download flow stops emitting, so without this the UI would stay stuck on
        // DOWNLOADING after a manual/auto pause. Only downgrade an in-flight download — never
        // clobber a terminal DONE/ERROR or an IDLE/PAUSED state.
        _state.update {
            if (it.progress.overallStatus == DownloadStatus.DOWNLOADING) {
                it.copy(progress = it.progress.copy(overallStatus = DownloadStatus.PAUSED))
            } else {
                it
            }
        }
    }

    private fun launchDownload() {
        if (job?.isActive == true) return
        job = prepare.run(_state.value.networkPreference)
            .onEach { p -> _state.update { it.copy(progress = p) } }
            .launchIn(viewModelScope)
    }
}
