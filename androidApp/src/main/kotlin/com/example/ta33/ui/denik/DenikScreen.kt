package com.example.ta33.ui.denik

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ta33.presentation.DenikViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stateful wrapper Deníku (RD-01): odebírá [DenikViewModel] (katalog + běh) a vykreslí
 * kanonický `VariantPrehled`. Přepínač tras volá [DenikViewModel.toggle].
 */
@Composable
fun DenikScreen(denikViewModel: DenikViewModel = koinViewModel()) {
    val state by denikViewModel.state.collectAsStateWithLifecycle()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        if (state.loading) {
            DenikLoading()
        } else {
            DenikVariantPrehled(state = state, onSwitch = denikViewModel::toggle)
        }
    }
}
