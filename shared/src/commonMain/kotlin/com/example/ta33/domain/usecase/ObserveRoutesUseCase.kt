package com.example.ta33.domain.usecase

import com.example.ta33.domain.model.RouteSummary
import com.example.ta33.domain.repository.RouteRepository
import kotlinx.coroutines.flow.Flow

class ObserveRoutesUseCase(private val routes: RouteRepository) {
    operator fun invoke(): Flow<List<RouteSummary>> = routes.observeRouteSummaries()
}
