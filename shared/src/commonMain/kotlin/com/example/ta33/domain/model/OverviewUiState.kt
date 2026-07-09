package com.example.ta33.domain.model

import com.example.ta33.presentation.navigation.AppReadiness
import com.example.ta33.presentation.navigation.ContentAvailability

/** Slim progress projection for the overview (raw counts; no label/plurals — UI concern). */
data class OverviewProgress(
    val collectedCount: Int,
    val totalCount: Int,
    val isComplete: Boolean,
    val isRunFinished: Boolean,
)

/** Aggregated Přehled state (FR-10). Pure read model returned by OverviewComposer.compose(...). */
data class OverviewUiState(
    val readiness: AppReadiness = AppReadiness.LOADING,
    val contentAvailability: ContentAvailability = ContentAvailability.UNKNOWN,
    val activeRoute: RouteSummary? = null,       // reuses FR-03 RouteSummary; null if none/stale
    val hasActiveRun: Boolean = false,
    val progress: OverviewProgress? = null,      // null when no active run (not "0 z N")
    val syncStatus: PreparationStatus = PreparationStatus.NOT_STARTED, // offline package (Etapa 1)
    val manifestVersion: Int? = null,
    val readyAtMillis: Long? = null,
    val loading: Boolean = true,                 // VM initial; composer emits false
)
