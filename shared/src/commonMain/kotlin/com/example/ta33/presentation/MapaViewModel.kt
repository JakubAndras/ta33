package com.example.ta33.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ta33.domain.model.MapaUiState
import com.example.ta33.domain.model.RouteItinerary
import com.example.ta33.domain.repository.RouteCatalogRepository
import com.example.ta33.domain.usecase.ObserveAppStateUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Composes the route itinerary catalog (RD-00) with the app's active route ([ObserveAppStateUseCase])
 * into [MapaUiState] for the `VariantHybrid` design (schematic map + compact itinerary).
 *
 * The displayed route defaults to the active route (or the first catalog route) but can be switched
 * with [bindSelected]/[toggle] without changing the active run. [highlight] drives the cross-highlight
 * between a tapped pin and its itinerary row; switching routes clears any highlight.
 *
 * Distinct from the FR-06 [MapViewModel] (reserved for the future real MapLibre map + live GPS).
 */
class MapaViewModel(
    private val observeApp: ObserveAppStateUseCase,
    private val catalog: RouteCatalogRepository,
) : ViewModel() {

    private val selectedRouteId = MutableStateFlow<String?>(null)
    private val highlightedControl = MutableStateFlow<Int?>(null)
    private var itineraries: List<RouteItinerary> = emptyList()

    private val _state = MutableStateFlow(MapaUiState())
    val state: StateFlow<MapaUiState> = _state.asStateFlow()

    init {
        val itinerariesFlow = catalog.observeItineraries().onEach { itineraries = it }

        // The displayed route defaults to the active route; a manual selection overrides it.
        val displayedRouteId = combine(observeApp(), itinerariesFlow, selectedRouteId) { app, itins, selected ->
            selected ?: app.activeRouteId ?: itins.firstOrNull()?.routeId
        }.distinctUntilChanged()

        // A cross-highlight belongs to one displayed route: clear it whenever that route changes -
        // via bindSelected/toggle OR an external activeRouteId change (a highlight can't survive a
        // route swap because ordinals then point at a different control).
        displayedRouteId
            .onEach { highlightedControl.value = null }
            .launchIn(viewModelScope)

        combine(displayedRouteId, itinerariesFlow, highlightedControl) { routeId, itins, highlighted ->
            val itinerary = itins.firstOrNull { it.routeId == routeId }
            val canSwitch = itins.size > 1
            if (itinerary == null) {
                MapaUiState(canSwitch = canSwitch, loading = false)
            } else {
                MapaUiState(
                    routeId = itinerary.routeId,
                    shortId = itinerary.shortId,
                    letter = itinerary.letter,
                    distanceKm = itinerary.distanceKm,
                    ascentMeters = itinerary.ascentMeters,
                    controlsCount = itinerary.controlsCount,
                    waypoints = itinerary.waypoints,
                    highlightedControl = highlighted,
                    canSwitch = canSwitch,
                    loading = false,
                )
            }
        }
            .onEach { _state.value = it }
            .launchIn(viewModelScope)
    }

    /** Switch the displayed route to [routeId] (does not touch the active run); clears any highlight. */
    fun bindSelected(routeId: String) {
        selectedRouteId.value = routeId
    }

    /** Přepnout pill: flip to the other available route. No-op with a single route. */
    fun toggle() {
        val current = _state.value.routeId ?: return
        val next = itineraries.firstOrNull { it.routeId != current } ?: return
        bindSelected(next.routeId)
    }

    /** Cross-highlight a control (by ordinal) between its pin and itinerary row; null clears it. */
    fun highlight(controlOrdinal: Int?) {
        highlightedControl.value = controlOrdinal
    }
}
