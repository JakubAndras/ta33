package com.example.ta33.data.repository

import com.example.ta33.data.content.RouteCatalog
import com.example.ta33.domain.model.RouteItinerary
import com.example.ta33.domain.repository.RouteCatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Serves the static [RouteCatalog] (mock TA33 + TA50 content). */
class StaticRouteCatalogRepository : RouteCatalogRepository {
    override fun observeItineraries(): Flow<List<RouteItinerary>> = flowOf(RouteCatalog.itineraries)

    override suspend fun getItinerary(routeId: String): RouteItinerary? = RouteCatalog.byId(routeId)
}
