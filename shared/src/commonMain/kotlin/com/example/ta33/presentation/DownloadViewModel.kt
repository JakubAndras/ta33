package com.example.ta33.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ta33.core.Notifier
import com.example.ta33.data.connectivity.ConnectivityMonitor
import com.example.ta33.domain.download.DownloadStatus
import com.example.ta33.domain.download.OfflinePackageProgress
import com.example.ta33.domain.model.NetworkPreference
import com.example.ta33.domain.model.NetworkType
import com.example.ta33.domain.model.PreparationState
import com.example.ta33.domain.model.PreparationStatus
import com.example.ta33.domain.usecase.ObserveNotificationsEnabledUseCase
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
    private val notifier: Notifier,
    observeNotificationsEnabled: ObserveNotificationsEnabledUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()
    private var job: Job? = null

    // FR-11b: distinguish an automatic (network-loss) pause from a user pause, so auto-resume on
    // Wi-Fi return never overrides a deliberate user pause.
    private var pausedByNetwork = false
    private var notificationsEnabled = true
    private var lastNotifiedDone = false

    init {
        connectivity.observe().onEach { type ->
            _state.update {
                it.copy(
                    currentNetworkType = type,
                    blockedByNetwork = !PrepareOfflinePackageUseCase.networkAllows(it.networkPreference, type),
                )
            }
            // auto-pause if preference no longer satisfied mid-download; else try to auto-start on Wi-Fi
            if (_state.value.blockedByNetwork) autoPause() else maybeAutoStart()
        }.launchIn(viewModelScope)
        observePreparation().onEach { p ->
            _state.update { it.copy(preparation = p) }
            maybeAutoStart()
        }.launchIn(viewModelScope)
        observeNotificationsEnabled().onEach { notificationsEnabled = it }.launchIn(viewModelScope)
    }

    /** FR-11b: on Wi-Fi, start (fresh) or resume (network-paused) the download automatically. */
    private fun maybeAutoStart() {
        val s = _state.value
        if (s.preparation.status == PreparationStatus.READY) return
        if (s.currentNetworkType != NetworkType.WIFI) return // off Wi-Fi never auto-starts
        when (s.progress.overallStatus) {
            DownloadStatus.IDLE -> start()
            DownloadStatus.PAUSED -> if (pausedByNetwork) resume()
            else -> {}
        }
    }

    fun setNetworkPreference(pref: NetworkPreference) {
        _state.update {
            it.copy(
                networkPreference = pref,
                blockedByNetwork = !PrepareOfflinePackageUseCase.networkAllows(pref, it.currentNetworkType),
            )
        }
        // Tightening the preference mid-download on a now-disallowed network is an automatic pause.
        if (_state.value.blockedByNetwork) autoPause()
    }

    fun start() = launchDownload()

    fun resume() = launchDownload() // resume = relaunch; use-case skips done items

    fun retry() = launchDownload()

    /** User-initiated pause: will NOT auto-resume when Wi-Fi is (re)available. */
    fun pause() {
        pausedByNetwork = false
        doPause()
    }

    /** Automatic pause on network loss: auto-resumes when Wi-Fi returns (see [maybeAutoStart]). */
    private fun autoPause() {
        pausedByNetwork = true
        doPause()
    }

    private fun doPause() {
        job?.cancel()
        job = null
        // A cancelled download flow stops emitting, so without this the UI would stay stuck on
        // DOWNLOADING after a manual/auto pause. Only downgrade an in-flight download - never
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
        pausedByNetwork = false // an (re)start clears the network-pause marker
        job = prepare.run(_state.value.networkPreference)
            .onEach { p ->
                _state.update { it.copy(progress = p) }
                onProgress(p.overallStatus)
            }
            .launchIn(viewModelScope)
    }

    /** FR-11b: fire the completion notification once per finished download, gated by the in-app setting. */
    private fun onProgress(status: DownloadStatus) {
        if (status == DownloadStatus.DONE) {
            if (!lastNotifiedDone && notificationsEnabled) {
                notifier.notifyDownloadComplete(
                    "Data akce stažena",
                    "Trasa, kontroly a mapa jsou připravené offline.",
                )
            }
            lastNotifiedDone = true
        } else {
            lastNotifiedDone = false
        }
    }
}
