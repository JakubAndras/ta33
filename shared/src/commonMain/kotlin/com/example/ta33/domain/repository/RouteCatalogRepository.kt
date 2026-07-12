package com.example.ta33.domain.repository

import com.example.ta33.domain.model.RouteItinerary
import kotlinx.coroutines.flow.Flow

/**
 * Source of rich route content (itinerary + elevation + metadata) for the redesigned screens.
 * Backed by the static [com.example.ta33.data.content.RouteCatalog] mock for now; a real FR-11
 * content pipeline (JSON/SQLDelight) is a follow-up that can replace the impl without changing VMs.
 */
interface RouteCatalogRepository {
    fun observeItineraries(): Flow<List<RouteItinerary>>       // [TA33, TA50]
    suspend fun getItinerary(routeId: String): RouteItinerary?
}
