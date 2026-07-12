package com.example.ta33.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ta33.presentation.AppViewModel
import com.example.ta33.presentation.navigation.AppReadiness
import org.koin.compose.viewmodel.koinViewModel

/**
 * Kořen Android UI: LOADING → splash; jinak vždy tabbed shell (FR-01 readiness jako zdroj pravdy).
 * Dokud data akce/mapa nejsou stažená (readiness != READY), `MainShell` ukazuje download kartu
 * v Deníku a skrývá tab Mapa; celoobrazovkový preparation gate už není.
 */
@Composable
fun Ta33App(appViewModel: AppViewModel = koinViewModel()) {
    val app by appViewModel.state.collectAsStateWithLifecycle()
    when (app.readiness) {
        AppReadiness.LOADING -> SplashView()
        else -> MainShell(
            app = app,
            onScan = { /* TODO FR-09 scan flow */ },
        )
    }
}
