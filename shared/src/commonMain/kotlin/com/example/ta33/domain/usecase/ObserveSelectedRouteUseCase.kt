package com.example.ta33.domain.usecase

import com.example.ta33.domain.repository.AppPreferencesRepository
import com.example.ta33.domain.repository.RouteRepository
import com.example.ta33.domain.route.ActiveRouteResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Validated persisted selection (for the "selected" marker). */
class ObserveSelectedRouteUseCase(
    private val routes: RouteRepository,
    private val prefs: AppPreferencesRepository,
) {
    operator fun invoke(): Flow<String?> =
        combine(routes.observeRouteSummaries(), prefs.observeSelectedRouteId()) { summaries, selected ->
            ActiveRouteResolver.resolve(
                activeRunRouteId = null, // FR-03 marker is selection-based; run-awareness is FR-01's job
                selectedRouteId = selected,
                availableRouteIds = summaries.map { it.routeId },
            )
        }
}
