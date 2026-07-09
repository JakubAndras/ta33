package com.example.ta33.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.example.ta33.data.db.Ta33Database
import com.example.ta33.data.mapper.toDomain
import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.Route
import com.example.ta33.domain.model.RouteSummary
import com.example.ta33.domain.repository.RouteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RouteRepositoryImpl(
    private val db: Ta33Database,
) : RouteRepository {
    private val rq get() = db.routeQueries
    private val cq get() = db.controlPointQueries

    override fun observeRoutes(): Flow<List<Route>> =
        rq.selectAllRoutes().asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeRouteSummaries(): Flow<List<RouteSummary>> =
        rq.selectRouteSummaries().asFlow().mapToList(Dispatchers.Default)
            .map { rows ->
                rows.map { RouteSummary(it.id, it.name, it.distanceKm, it.controlCount.toInt()) }
            }

    override fun observeRouteWithControls(routeId: String): Flow<Route?> =
        combine(
            rq.selectRouteById(routeId).asFlow().mapToOneOrNull(Dispatchers.Default),
            cq.selectControlsForRoute(routeId).asFlow().mapToList(Dispatchers.Default),
        ) { routeRow, controlRows ->
            routeRow?.toDomain(controlRows.map { it.toDomain() })
        }

    override suspend fun getRouteWithControls(routeId: String): Route? =
        withContext(Dispatchers.Default) {
            val route = rq.selectRouteById(routeId).executeAsOneOrNull() ?: return@withContext null
            val controls = cq.selectControlsForRoute(routeId).executeAsList().map { it.toDomain() }
            route.toDomain(controls)
        }

    override suspend fun getControl(controlId: String): ControlPoint? =
        withContext(Dispatchers.Default) {
            cq.selectControlById(controlId).executeAsOneOrNull()?.toDomain()
        }

    override suspend fun upsertRoute(route: Route, controls: List<ControlPoint>) {
        withContext(Dispatchers.Default) {
            db.transaction {
                rq.upsertRoute(route.id, route.name, route.distanceKm)
                controls.forEach { control ->
                    cq.upsertControl(
                        control.id,
                        control.routeId,
                        control.ordinal.toLong(),
                        control.name,
                        control.location.latitude,
                        control.location.longitude,
                        control.radiusMeters,
                    )
                }
            }
        }
    }
}
