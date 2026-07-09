package com.example.ta33

import com.example.ta33.domain.model.LogUiState
import com.example.ta33.domain.model.PreparationState
import com.example.ta33.domain.model.PreparationStatus
import com.example.ta33.domain.model.RouteSummary
import com.example.ta33.domain.overview.OverviewComposer
import com.example.ta33.presentation.navigation.AppReadiness
import com.example.ta33.presentation.navigation.AppUiState
import com.example.ta33.presentation.navigation.ContentAvailability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OverviewComposerTest {

    private fun summary(id: String, name: String = id) =
        RouteSummary(routeId = id, name = name, distanceKm = 33.0, controlCount = 5)

    @Test
    fun notReady_emptyState() {
        val app = AppUiState(
            readiness = AppReadiness.NOT_READY,
            contentAvailability = ContentAvailability.ABSENT,
        )
        val state = OverviewComposer.compose(app, emptyList(), null, PreparationState())

        assertNull(state.activeRoute)
        assertNull(state.progress)
        assertFalse(state.hasActiveRun)
        assertEquals(PreparationStatus.NOT_STARTED, state.syncStatus)
        assertFalse(state.loading)
    }

    @Test
    fun contentPresent_noActiveRun() {
        val app = AppUiState(
            readiness = AppReadiness.READY,
            contentAvailability = ContentAvailability.PRESENT,
            activeRouteId = "r1",
            activeRunId = null,
        )
        val prep = PreparationState(PreparationStatus.READY, manifestVersion = 3, readyAtMillis = 42L)
        val state = OverviewComposer.compose(app, listOf(summary("r1"), summary("r2")), null, prep)

        assertEquals("r1", state.activeRoute?.routeId)
        assertFalse(state.hasActiveRun)
        assertNull(state.progress)
        assertEquals(PreparationStatus.READY, state.syncStatus)
        assertEquals(3, state.manifestVersion)
        assertEquals(42L, state.readyAtMillis)
    }

    @Test
    fun activeRun_partialProgress() {
        val app = AppUiState(
            readiness = AppReadiness.READY,
            contentAvailability = ContentAvailability.PRESENT,
            activeRouteId = "r1",
            activeRunId = "run1",
        )
        val log = LogUiState(collectedCount = 2, totalCount = 5, isComplete = false, isRunFinished = false)
        val state = OverviewComposer.compose(app, listOf(summary("r1")), log, PreparationState(PreparationStatus.READY))

        val progress = assertNotNull(state.progress)
        assertTrue(state.hasActiveRun)
        assertEquals(2, progress.collectedCount)
        assertEquals(5, progress.totalCount)
        assertFalse(progress.isComplete)
        assertFalse(progress.isRunFinished)
    }

    @Test
    fun activeRun_completeAndFinished() {
        val app = AppUiState(activeRouteId = "r1", activeRunId = "run1")
        val log = LogUiState(collectedCount = 5, totalCount = 5, isComplete = true, isRunFinished = true)
        val state = OverviewComposer.compose(app, listOf(summary("r1")), log, PreparationState())

        val progress = assertNotNull(state.progress)
        assertTrue(progress.isComplete)
        assertTrue(progress.isRunFinished)
    }

    @Test
    fun staleActiveRouteId_yieldsNullActiveRoute() {
        val app = AppUiState(activeRouteId = "gone", activeRunId = null)
        val state = OverviewComposer.compose(app, listOf(summary("r1"), summary("r2")), null, PreparationState())

        assertNull(state.activeRoute)
    }

    @Test
    fun prepStatuses_passThrough() {
        val app = AppUiState(activeRouteId = "r1")
        val summaries = listOf(summary("r1"))

        val preparing = OverviewComposer.compose(
            app, summaries, null, PreparationState(PreparationStatus.PREPARING, manifestVersion = 2),
        )
        assertEquals(PreparationStatus.PREPARING, preparing.syncStatus)
        assertEquals(2, preparing.manifestVersion)

        val error = OverviewComposer.compose(app, summaries, null, PreparationState(PreparationStatus.ERROR))
        assertEquals(PreparationStatus.ERROR, error.syncStatus)
    }
}
