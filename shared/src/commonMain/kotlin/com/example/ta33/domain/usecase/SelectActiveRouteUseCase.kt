package com.example.ta33.domain.usecase

import com.example.ta33.domain.repository.AppPreferencesRepository
import com.example.ta33.domain.repository.RouteRepository

sealed interface SelectRouteResult {
    data object Selected : SelectRouteResult
    data object UnknownRoute : SelectRouteResult
}

class SelectActiveRouteUseCase(
    private val routes: RouteRepository,
    private val prefs: AppPreferencesRepository,
) {
    suspend operator fun invoke(routeId: String): SelectRouteResult {
        if (routes.getRouteWithControls(routeId) == null) return SelectRouteResult.UnknownRoute // FR-02 one-shot
        prefs.setSelectedRouteId(routeId)
        return SelectRouteResult.Selected
    }
}
