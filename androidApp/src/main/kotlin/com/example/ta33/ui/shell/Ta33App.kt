package com.example.ta33.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ta33.presentation.AppViewModel
import com.example.ta33.presentation.navigation.AppReadiness
import com.example.ta33.ui.preparation.PreparationScreen
import org.koin.compose.viewmodel.koinViewModel

/**
 * Kořen Android UI: podle `AppViewModel.readiness` (jediný zdroj pravdy, FR-01)
 * ukáže splash / preparation gate / tabbed shell.
 */
@Composable
fun Ta33App(appViewModel: AppViewModel = koinViewModel()) {
    val app by appViewModel.state.collectAsStateWithLifecycle()
    when (app.readiness) {
        AppReadiness.LOADING -> SplashView()
        AppReadiness.NOT_READY, AppReadiness.PREPARING -> PreparationScreen()
        AppReadiness.READY -> MainShell(
            app = app,
            onScan = { /* TODO FR-09 scan flow */ },
        )
    }
}
