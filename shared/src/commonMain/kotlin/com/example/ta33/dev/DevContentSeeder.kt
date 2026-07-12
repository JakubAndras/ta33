package com.example.ta33.dev

import com.example.ta33.data.content.RouteCatalog
import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.Route
import com.example.ta33.domain.model.WaypointKind
import com.example.ta33.domain.repository.PreparationRepository
import com.example.ta33.domain.repository.RouteCatalogRepository
import com.example.ta33.domain.repository.RouteRepository

/**
 * DEV / TESTING ONLY - seeds the local DB from the static [RouteCatalog] (TA33): upserts the [Route]
 * + its [ControlPoint]s (from CONTROL waypoints with approximate GPS) and marks the offline package
 * READY. Idempotent (INSERT OR REPLACE). Shared by [DevSeed] (startup seed) and the Profil Sandbox
 * panel „Data akce / mapa stažena → ON" (UI-12). Returns the seeded controls so callers can chain.
 */
class DevContentSeeder(
    private val catalog: RouteCatalogRepository,
    private val routes: RouteRepository,
    private val prep: PreparationRepository,
) {
    suspend fun seed(): List<ControlPoint> {
        val routeId = RouteCatalog.TA33_ROUTE_ID
        val itinerary = catalog.getItinerary(routeId) ?: return emptyList()

        val controls = itinerary.waypoints
            .filter { it.kind == WaypointKind.CONTROL && it.location != null }
            .map { wp ->
                ControlPoint(
                    id = "$routeId-kp${wp.controlOrdinal}",
                    routeId = routeId,
                    ordinal = wp.controlOrdinal!!,
                    name = wp.name,
                    location = wp.location!!,
                )
            }
        routes.upsertRoute(
            Route(routeId, itinerary.name, distanceKm = itinerary.distanceKm, controls = controls),
            controls,
        )
        prep.markReady(manifestVersion = 1)
        return controls
    }
}
