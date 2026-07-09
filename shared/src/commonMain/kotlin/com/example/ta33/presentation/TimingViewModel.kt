package com.example.ta33.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ta33.core.TimeFormatter
import com.example.ta33.domain.model.Split
import com.example.ta33.domain.qr.QrScanHandler
import com.example.ta33.domain.usecase.HandleScannedQrUseCase
import com.example.ta33.domain.usecase.ObserveTimingUseCase
import com.example.ta33.domain.usecase.ScanTimingResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TimingUiState(
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
    val elapsedMillis: Long = 0L,
    val elapsedFormatted: String = "00:00",
    val splits: List<Split> = emptyList(),
    val lastScan: ScanTimingResult? = null,   // consumed by UI (e.g. show "started"/"finished"/"wrong QR")
)

class TimingViewModel(
    private val observeTiming: ObserveTimingUseCase,
    private val handleScannedQr: HandleScannedQrUseCase,
) : ViewModel(), QrScanHandler {

    private val _state = MutableStateFlow(TimingUiState())
    val state: StateFlow<TimingUiState> = _state.asStateFlow()

    private var runId: String? = null
    private var routeId: String? = null
    private var bindJob: Job? = null

    fun bind(runId: String, routeId: String) {
        this.runId = runId
        this.routeId = routeId
        bindJob?.cancel()
        bindJob = observeTiming(runId)
            .onEach { snap ->
                _state.update {
                    it.copy(
                        isRunning = snap.isRunning,
                        isFinished = snap.isFinished,
                        elapsedMillis = snap.elapsedMillis,
                        elapsedFormatted = TimeFormatter.format(snap.elapsedMillis),
                        splits = snap.splits,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /** Native scanner (later phase) calls this with a decoded QR string. */
    override fun onQrScanned(raw: String) {
        val rid = runId ?: return
        val route = routeId ?: return
        viewModelScope.launch {
            val result = handleScannedQr(rid, route, raw)
            _state.update { it.copy(lastScan = result) }
        }
    }
}
