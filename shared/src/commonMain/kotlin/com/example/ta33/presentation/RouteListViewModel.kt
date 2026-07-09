package com.example.ta33.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ta33.domain.model.RouteSummary
import com.example.ta33.domain.usecase.ObserveRoutesUseCase
import com.example.ta33.domain.usecase.ObserveSelectedRouteUseCase
import com.example.ta33.domain.usecase.SelectActiveRouteUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class RouteListUiState(
    val routes: List<RouteSummary> = emptyList(),
    val selectedRouteId: String? = null,
    val loading: Boolean = true,
)

class RouteListViewModel(
    private val observeRoutes: ObserveRoutesUseCase,
    private val observeSelectedRoute: ObserveSelectedRouteUseCase,
    private val selectActiveRoute: SelectActiveRouteUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(RouteListUiState())
    val state: StateFlow<RouteListUiState> = _state.asStateFlow()

    init {
        combine(observeRoutes(), observeSelectedRoute()) { routes, selected ->
            RouteListUiState(routes = routes, selectedRouteId = selected, loading = false)
        }.onEach { _state.value = it }.launchIn(viewModelScope)
    }

    fun selectRoute(routeId: String) {
        viewModelScope.launch { selectActiveRoute(routeId) }
    }
}
