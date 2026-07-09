package com.example.ta33.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ta33.domain.model.RouteDetail
import com.example.ta33.domain.usecase.ObserveRouteDetailUseCase
import com.example.ta33.domain.usecase.ObserveSelectedRouteUseCase
import com.example.ta33.domain.usecase.SelectActiveRouteUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class RouteDetailUiState(
    val detail: RouteDetail? = null,
    val isActive: Boolean = false,
    val notFound: Boolean = false,
    val loading: Boolean = true,
)

class RouteDetailViewModel(
    private val observeRouteDetail: ObserveRouteDetailUseCase,
    private val observeSelectedRoute: ObserveSelectedRouteUseCase,
    private val selectActiveRoute: SelectActiveRouteUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(RouteDetailUiState())
    val state: StateFlow<RouteDetailUiState> = _state.asStateFlow()
    private var routeId: String? = null
    private var bindJob: Job? = null

    fun bind(routeId: String) {
        this.routeId = routeId
        bindJob?.cancel()
        bindJob = combine(observeRouteDetail(routeId), observeSelectedRoute()) { detail, selected ->
            RouteDetailUiState(
                detail = detail,
                isActive = detail != null && selected == detail.routeId,
                notFound = detail == null,
                loading = false,
            )
        }.onEach { _state.value = it }.launchIn(viewModelScope)
    }

    fun makeActive() {
        routeId?.let { id -> viewModelScope.launch { selectActiveRoute(id) } }
    }
}
