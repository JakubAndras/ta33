package com.example.ta33.ui.preparation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ta33.domain.model.NetworkPreference
import com.example.ta33.presentation.DownloadViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * „Příprava dat akce" (FR-11) nad sdíleným [DownloadViewModel]. Gate obrazovka pro
 * readiness NOT_READY/PREPARING; po dokončení stažení přepne na Main sám shell
 * (readiness gate v `Ta33App`), tady žádná explicitní navigace není.
 */
@Composable
fun PreparationScreen(vm: DownloadViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    PreparationContent(
        state = state,
        onStart = vm::start,
        onPause = vm::pause,
        onResume = vm::resume,
        onRetry = vm::retry,
        onToggleWifiOnly = { wifiOnly ->
            vm.setNetworkPreference(
                if (wifiOnly) NetworkPreference.WIFI_ONLY else NetworkPreference.WIFI_AND_CELLULAR,
            )
        },
    )
}
