package com.example.ta33.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ta33.core.TimeProvider
import com.example.ta33.data.content.RouteCatalog
import com.example.ta33.dev.DevContentSeeder
import com.example.ta33.dev.SandboxState
import com.example.ta33.domain.model.PreparationStatus
import com.example.ta33.domain.model.RunSession
import com.example.ta33.domain.repository.PreparationRepository
import com.example.ta33.domain.repository.RouteRepository
import com.example.ta33.domain.repository.RunRepository
import com.example.ta33.domain.usecase.EnsureLocalParticipantUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * DEV / TESTING ONLY (UI-12) — backs the Profil „Sandbox" panel. Derives the state of the four
 * toggles from the real app flows and exposes actions that drive the REAL app state via repositories
 * (so flipping a toggle changes actual behaviour: readiness gate, scan capsule, Deník, Profil).
 *
 * Run lifecycle note: `selectActiveRun` = finishedAtMillis IS NULL, so a finished run is not
 * "active" — finishing therefore flips [SandboxUiState.naTrase] to false. That is faithful to the
 * real app state, not a bug. We read the latest run (regardless of finished) to reach a finished run.
 */
class SandboxViewModel(
    private val sandbox: SandboxState,
    private val runs: RunRepository,
    private val routes: RouteRepository,
    private val prep: PreparationRepository,
    private val seeder: DevContentSeeder,
    private val ensureParticipant: EnsureLocalParticipantUseCase,
    private val time: TimeProvider,
) : ViewModel() {

    val state: StateFlow<SandboxUiState> = combine(
        sandbox.paid,
        runs.observeLatestRun(),
        routes.observeRoutes(),
        prep.observePreparationState(),
    ) { paid, latest, routeList, preparation ->
        SandboxUiState(
            paid = paid,
            naTrase = latest != null && latest.finishedAtMillis == null,
            downloaded = preparation.status == PreparationStatus.READY || routeList.isNotEmpty(),
            finished = latest?.finishedAtMillis != null,
            runExists = latest != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SandboxUiState())

    fun setPaid(on: Boolean) {
        sandbox.paid.value = on
    }

    fun setNaTrase(on: Boolean) {
        viewModelScope.launch {
            if (on) ensureStartedRun() else runs.clearAllRuns()
        }
    }

    fun setFinished(on: Boolean) {
        viewModelScope.launch {
            val run = ensureStartedRun() // finishing needs a started run
            if (on) runs.setFinished(run.id, time.nowMillis()) else runs.clearFinished(run.id)
        }
    }

    fun setDownloaded(on: Boolean) {
        viewModelScope.launch {
            if (on) {
                seeder.seed()
            } else {
                runs.clearAllRuns() // a run referencing a now-gone route would be inconsistent
                routes.clearAll()
                prep.reset()
            }
        }
    }

    /** Ensures a single started, unfinished run exists (seeding content first if needed). */
    private suspend fun ensureStartedRun(): RunSession {
        runs.getLatestRun()?.let { existing ->
            var run = existing
            if (run.finishedAtMillis != null) {
                runs.clearFinished(run.id)
                run = run.copy(finishedAtMillis = null)
            }
            if (run.startedAtMillis == null) {
                val startedAt = time.nowMillis()
                runs.setStarted(run.id, startedAt)
                run = run.copy(startedAtMillis = startedAt)
            }
            return run // build the result locally; avoids a re-read that could NPE on a concurrent wipe
        }
        var routeList = routes.observeRoutes().first()
        if (routeList.isEmpty()) {
            seeder.seed()
            routeList = routes.observeRoutes().first()
        }
        val routeId = routeList.firstOrNull()?.id ?: RouteCatalog.TA33_ROUTE_ID
        val run = runs.createRun(routeId, ensureParticipant().id)
        val startedAt = time.nowMillis()
        runs.setStarted(run.id, startedAt)
        return run.copy(startedAtMillis = startedAt)
    }
}
