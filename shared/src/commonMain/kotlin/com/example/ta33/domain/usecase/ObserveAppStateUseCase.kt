package com.example.ta33.domain.usecase

import com.example.ta33.domain.repository.AppPreferencesRepository
import com.example.ta33.domain.repository.PreparationRepository
import com.example.ta33.domain.repository.RouteRepository
import com.example.ta33.domain.repository.RunRepository
import com.example.ta33.presentation.navigation.AppStateReducer
import com.example.ta33.presentation.navigation.AppUiState
import com.example.ta33.presentation.navigation.StartDestinationResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Single source of truth for AppUiState (FR-01). Wraps the exact combine + AppStateReducer
 * that AppViewModel used, so FR-10's overview and FR-01's ViewModel share ONE derivation.
 * (Arity mirrors the FR-01 reducer: routes, activeRun, selectedRouteId, preparationState.)
 */
class ObserveAppStateUseCase(
    private val routes: RouteRepository,
    private val runs: RunRepository,
    private val prefs: AppPreferencesRepository,
    private val prep: PreparationRepository,
    private val resolver: StartDestinationResolver,
) {
    operator fun invoke(): Flow<AppUiState> =
        combine(
            routes.observeRoutes(),
            runs.observeActiveRun(),
            prefs.observeSelectedRouteId(),
            prep.observePreparationState(),
        ) { routeList, activeRun, selectedRouteId, preparation ->
            AppStateReducer.reduce(routeList, activeRun, selectedRouteId, preparation, resolver)
        }
}
