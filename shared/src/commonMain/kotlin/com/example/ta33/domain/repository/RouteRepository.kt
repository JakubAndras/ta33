package com.example.ta33.domain.repository

import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.Route
import com.example.ta33.domain.model.RouteSummary
import kotlinx.coroutines.flow.Flow

interface RouteRepository {
    fun observeRoutes(): Flow<List<Route>>                    // metadata only (FR-03 list)
    fun observeRouteSummaries(): Flow<List<RouteSummary>>       // Seam A (list)
    fun observeRouteWithControls(routeId: String): Flow<Route?> // Seam B (reactive detail)
    suspend fun getRouteWithControls(routeId: String): Route? // aggregate (FR-03 detail)
    suspend fun getControl(controlId: String): ControlPoint?

    // Local seeding hook; the actual content source (JSON download) is FR-11.
    suspend fun upsertRoute(route: Route, controls: List<ControlPoint>)
}
