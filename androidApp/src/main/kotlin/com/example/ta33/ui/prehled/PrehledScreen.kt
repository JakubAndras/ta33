package com.example.ta33.ui.prehled

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ta33.presentation.OverviewViewModel
import com.example.ta33.presentation.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stateful wrapper tabu „Přehled": odebírá [OverviewViewModel] a [SettingsViewModel]
 * (oba startují samy v `init`) a předává jejich stav do stateless [PrehledContent].
 */
@Composable
fun PrehledScreen(
    overviewVm: OverviewViewModel = koinViewModel(),
    settingsVm: SettingsViewModel = koinViewModel(),
) {
    val overview by overviewVm.state.collectAsStateWithLifecycle()
    val settings by settingsVm.state.collectAsStateWithLifecycle()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        PrehledContent(
            overview = overview,
            settings = settings,
            onToggleNotifications = settingsVm::setNotificationsEnabled,
        )
    }
}
