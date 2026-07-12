package com.example.ta33.dev

import com.example.ta33.core.IdGenerator
import com.example.ta33.core.TimeProvider
import com.example.ta33.data.content.RouteCatalog
import com.example.ta33.domain.model.CollectedControl
import com.example.ta33.domain.repository.RouteRepository
import com.example.ta33.domain.repository.RunRepository
import com.example.ta33.domain.usecase.EnsureLocalParticipantUseCase
import kotlinx.coroutines.flow.first

/**
 * DEV / TESTING ONLY - NOT for production.
 *
 * Seeds the local DB (via [DevContentSeeder]: TA33 route+controls from the static [RouteCatalog],
 * marks the offline package READY) and additionally creates a *started* run with the first two
 * controls collected - so the app boots straight into the READY shell, on-route, with real content
 * for on-device UI testing.
 *
 * Idempotent: no-op if any route already exists. Gated by [DEV_SEED_ENABLED] and only invoked from
 * the debug app entry points (see `seedDevDataIfEmpty` in Koin.kt).
 */
class DevSeed(
    private val seeder: DevContentSeeder,
    private val routes: RouteRepository,
    private val runs: RunRepository,
    private val ensureParticipant: EnsureLocalParticipantUseCase,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) {
    suspend fun seedIfEmpty() {
        if (routes.observeRoutes().first().isNotEmpty()) return

        val controls = seeder.seed()
        if (controls.isEmpty()) return

        val participant = ensureParticipant()
        val run = runs.createRun(RouteCatalog.TA33_ROUTE_ID, participant.id)
        val now = time.nowMillis()
        runs.setStarted(run.id, now - 44 * 60_000L) // started 44 min ago
        // Collect the first two controls so the deník shows progress "2 z N".
        controls.take(2).forEachIndexed { i, control ->
            runs.addCollected(CollectedControl(ids.newId(), run.id, control.id, now - (42 - i * 12) * 60_000L))
        }
    }
}

/** Master switch for the dev seed. Flip to `false` (or remove this module) before any release. */
const val DEV_SEED_ENABLED = true
