package com.example.ta33.domain.usecase

import com.example.ta33.domain.model.RouteDetail
import com.example.ta33.domain.repository.RouteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveRouteDetailUseCase(private val routes: RouteRepository) {
    operator fun invoke(routeId: String): Flow<RouteDetail?> =
        routes.observeRouteWithControls(routeId).map { route ->
            route?.let {
                RouteDetail(
                    routeId = it.id,
                    name = it.name,
                    distanceKm = it.distanceKm,
                    controls = it.controls.sortedBy { c -> c.ordinal }, // defensive ordering
                )
            }
        }
}
