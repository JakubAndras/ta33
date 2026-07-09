package com.example.ta33.ui.denik

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ta33.presentation.AppViewModel
import com.example.ta33.presentation.RouteDetailUiState
import com.example.ta33.presentation.RouteDetailViewModel
import com.example.ta33.presentation.RunLogViewModel
import com.example.ta33.presentation.navigation.AppReadiness
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stateful wrapper Deníku: odebírá [AppViewModel] (gate) a podle `readiness`
 * vykreslí loading / obsah nestažen / na trase. VM se nedostane do stateless obsahu.
 */
@Composable
fun DenikScreen(
    appViewModel: AppViewModel = koinViewModel(),
    onDownload: () -> Unit = {},
) {
    val app by appViewModel.state.collectAsStateWithLifecycle()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        val runId = app.activeRunId
        val routeId = app.activeRouteId
        when {
            app.readiness == AppReadiness.LOADING -> DenikLoading()
            app.readiness == AppReadiness.READY && runId != null && routeId != null ->
                DenikOnRouteRoute(runId = runId, routeId = routeId)
            else -> DenikBefore(onDownload = onDownload)
        }
    }
}

@Composable
private fun DenikOnRouteRoute(runId: String, routeId: String) {
    val logVm: RunLogViewModel = koinViewModel()
    val routeVm: RouteDetailViewModel = koinViewModel()
    LaunchedEffect(runId, routeId) {
        logVm.bind(runId, routeId)
        routeVm.bind(routeId)
    }
    val log by logVm.state.collectAsStateWithLifecycle()
    val route by routeVm.state.collectAsStateWithLifecycle()
    DenikOnRoute(
        log = log,
        routeLabel = routeLabelOf(route),
        offline = true, // TODO: napojit ConnectivityMonitor (app-shell)
    )
}

/** Složí label „<jméno> · <km> km" z RouteDetail; dokud detail nedorazí, vrátí placeholder. */
private fun routeLabelOf(state: RouteDetailUiState): String {
    val detail = state.detail ?: return "Trasa"
    val km = detail.distanceKm
    val kmStr = if (km % 1.0 == 0.0) km.toInt().toString() else km.toString().replace('.', ',')
    return "${detail.name} · $kmStr km"
}
