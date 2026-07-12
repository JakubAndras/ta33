package com.example.ta33.domain.route

/**
 * Effective active route id. An in-progress run's route wins; else the persisted selection
 * if it is still among the available routes; else the sole route; else null.
 * Pure + deterministic - shared by FR-03 (ObserveSelectedRoute) and FR-01 (AppStateReducer).
 */
object ActiveRouteResolver {
    fun resolve(
        activeRunRouteId: String?,
        selectedRouteId: String?,
        availableRouteIds: List<String>,
    ): String? =
        activeRunRouteId
            ?: selectedRouteId?.takeIf { it in availableRouteIds }
            ?: availableRouteIds.singleOrNull()
}
