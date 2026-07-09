package com.example.ta33.domain.usecase

import com.example.ta33.domain.model.QrTimingConfig
import com.example.ta33.domain.qr.QrParseResult
import com.example.ta33.domain.qr.QrPayloadParser
import com.example.ta33.domain.repository.RunRepository   // FR-02

/** Outcome of handling one scanned QR (FR-09). */
sealed interface ScanTimingResult {
    data class Started(val startedAtMillis: Long) : ScanTimingResult
    data class Finished(val finishedAtMillis: Long, val elapsedMillis: Long) : ScanTimingResult
    data object AlreadyStarted : ScanTimingResult      // start QR after already started
    data object AlreadyFinished : ScanTimingResult     // any timing QR after finish
    data object FinishBeforeStart : ScanTimingResult   // finish QR before start
    data class WrongRoute(val expectedRouteId: String, val scannedRouteId: String?) : ScanTimingResult
    data class NotATimingQr(val raw: String) : ScanTimingResult  // foreign / malformed
    data class RunNotFound(val runId: String) : ScanTimingResult
}

class HandleScannedQrUseCase(
    private val parser: QrPayloadParser,
    private val runs: RunRepository,             // FR-02: getRun/observeRun
    private val startRun: StartRunUseCase,       // FR-02: writes startedAtMillis
    private val finishRun: FinishRunUseCase,     // FR-02: writes finishedAtMillis (guards)
    private val config: QrTimingConfig,
) {
    suspend operator fun invoke(runId: String, routeId: String, raw: String): ScanTimingResult {
        val parsed = parser.parse(raw, config)
        val run = runs.getRun(runId) ?: return ScanTimingResult.RunNotFound(runId)

        return when (parsed) {
            is QrParseResult.Unrecognized -> ScanTimingResult.NotATimingQr(raw)

            is QrParseResult.StartQr -> {
                config.wrongRouteOrNull(routeId, parsed.routeId)?.let { return it }
                when {
                    run.finishedAtMillis != null -> ScanTimingResult.AlreadyFinished
                    run.startedAtMillis != null  -> ScanTimingResult.AlreadyStarted
                    else -> {
                        startRun(runId)  // FR-02 writes startedAtMillis = TimeProvider.nowMillis()
                        val started = runs.getRun(runId)?.startedAtMillis
                        started?.let { ScanTimingResult.Started(it) } ?: ScanTimingResult.RunNotFound(runId)
                    }
                }
            }

            is QrParseResult.FinishQr -> {
                config.wrongRouteOrNull(routeId, parsed.routeId)?.let { return it }
                when {
                    run.finishedAtMillis != null -> ScanTimingResult.AlreadyFinished
                    run.startedAtMillis == null  -> ScanTimingResult.FinishBeforeStart
                    else -> {
                        finishRun(runId)  // FR-02 writes finishedAtMillis (guards finish >= start)
                        val fresh = runs.getRun(runId)
                        val finished = fresh?.finishedAtMillis
                        val start = fresh?.startedAtMillis
                        if (finished != null && start != null)
                            ScanTimingResult.Finished(finished, (finished - start).coerceAtLeast(0L))
                        else ScanTimingResult.RunNotFound(runId)
                    }
                }
            }
        }
    }
}

/** Route-scope guard helper (only enforced when config.routeScoped). */
private fun QrTimingConfig.wrongRouteOrNull(expectedRouteId: String, scannedRouteId: String?): ScanTimingResult.WrongRoute? =
    if (routeScoped && scannedRouteId != null && scannedRouteId != expectedRouteId)
        ScanTimingResult.WrongRoute(expectedRouteId, scannedRouteId) else null
