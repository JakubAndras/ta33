package com.example.ta33.ui.profil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ta33.BuildConfig
import com.example.ta33.presentation.OverviewViewModel
import com.example.ta33.presentation.SandboxViewModel
import com.example.ta33.presentation.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stateful wrapper tabu „Profil": odebírá [OverviewViewModel] a [SettingsViewModel]
 * (oba startují samy v `init`) a předává jejich stav do stateless [ProfilContent].
 * „Hlasové pokyny" jsou lokální UI stav (mock, bez logiky - Etapa 2).
 * V DEBUG buildu navíc odebírá [SandboxViewModel] pro dev přepínače stavů (UI-12).
 */
@Composable
fun ProfilScreen(
    overviewVm: OverviewViewModel = koinViewModel(),
    settingsVm: SettingsViewModel = koinViewModel(),
) {
    val overview by overviewVm.state.collectAsStateWithLifecycle()
    val settings by settingsVm.state.collectAsStateWithLifecycle()
    var voiceGuidanceEnabled by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        // DEV Sandbox VM je čistě DEBUG - v release ho ani neresolvujeme/nesubscribujeme (UI-12).
        if (BuildConfig.DEBUG) {
            val sandboxVm: SandboxViewModel = koinViewModel()
            val sandbox by sandboxVm.state.collectAsStateWithLifecycle()
            ProfilContent(
                overview = overview,
                settings = settings,
                voiceGuidanceEnabled = voiceGuidanceEnabled,
                onToggleNotifications = settingsVm::setNotificationsEnabled,
                onToggleVoiceGuidance = { voiceGuidanceEnabled = it },
                paid = sandbox.paid,
                sandbox = sandbox,
                onSandboxPaid = sandboxVm::setPaid,
                onSandboxNaTrase = sandboxVm::setNaTrase,
                onSandboxDownloaded = sandboxVm::setDownloaded,
                onSandboxFinished = sandboxVm::setFinished,
            )
        } else {
            ProfilContent(
                overview = overview,
                settings = settings,
                voiceGuidanceEnabled = voiceGuidanceEnabled,
                onToggleNotifications = settingsVm::setNotificationsEnabled,
                onToggleVoiceGuidance = { voiceGuidanceEnabled = it },
            )
        }
    }
}
