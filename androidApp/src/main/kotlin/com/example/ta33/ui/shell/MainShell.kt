package com.example.ta33.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ta33.domain.model.CollectionCandidate
import com.example.ta33.domain.model.QrTimingConfig
import com.example.ta33.presentation.ControlCollectionViewModel
import com.example.ta33.presentation.CollectionOutcome
import com.example.ta33.presentation.TimingViewModel
import com.example.ta33.presentation.navigation.AppUiState
import com.example.ta33.presentation.navigation.TopLevelDestination
import com.example.ta33.ui.denik.DenikScreen
import com.example.ta33.ui.prehled.PrehledScreen
import com.example.ta33.ui.scan.CollectionOfferSheet
import com.example.ta33.ui.scan.ScanModal
import com.example.ta33.ui.scan.SplneniScreen
import com.example.ta33.ui.theme.Ta33Theme
import androidx.compose.runtime.LaunchedEffect
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Tabbed shell (READY stav): tři taby přepínané stavově (bez back-stacku, nav contract).
 * Bottom nav je plovoucí pill nad obsahem; obsah dostane spodní padding, aby ho pill nepřekrýval.
 * Scan FAB se ukáže jen při aktivním běhu (`activeRunId != null`) a otevře [ScanModal].
 * Nad taby žijí i nabídka sběru kontroly (FR-08) a zelená obrazovka Splnění.
 */
@Composable
fun MainShell(
    app: AppUiState,
    onScan: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(TopLevelDestination.DENIK) }
    var showScan by rememberSaveable { mutableStateOf(false) }
    val runId = app.activeRunId
    val routeId = app.activeRouteId
    // Když aktivní běh skončí, zavři případný otevřený scan modal (jinak by se sám znovu
    // otevřel při dalším běhu, protože `showScan` přežije rememberSaveable).
    LaunchedEffect(runId) { if (runId == null) showScan = false }
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButton = {
                if (runId != null) {
                    ScanFab(
                        onClick = { onScan(); showScan = true },
                        modifier = Modifier.navigationBarsPadding(),
                    )
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = Ta33Theme.spacing.x10 + Ta33Theme.spacing.x4),
                ) {
                    when (tab) {
                        TopLevelDestination.DENIK -> DenikScreen(onDownload = onDownload)
                        TopLevelDestination.MAPA -> StubScreen(title = "Mapa")
                        TopLevelDestination.PREHLED -> PrehledScreen()
                    }
                }
                Ta33BottomNav(
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        if (runId != null && routeId != null) {
            ActiveRunOverlays(
                runId = runId,
                routeId = routeId,
                showScan = showScan,
                onCloseScan = { showScan = false },
            )
        }
    }
}

/**
 * Overlaye vázané na aktivní běh: QR sken (FR-09) + nabídka sběru a Splnění (FR-08).
 * ViewModely se bindují jednou na `runId/routeId`; Splnění se lokálně zavře `dismiss` flagem
 * (VM `lastResult` sám nevyčistí — vyčistí ho až další candidate).
 */
@Composable
private fun BoxScope.ActiveRunOverlays(
    runId: String,
    routeId: String,
    showScan: Boolean,
    onCloseScan: () -> Unit,
) {
    val timing: TimingViewModel = koinViewModel()
    val collection: ControlCollectionViewModel = koinViewModel()
    val qrConfig: QrTimingConfig = koinInject()
    LaunchedEffect(runId, routeId) {
        timing.bind(runId, routeId)
        collection.bind(runId, routeId)
    }
    val tState by timing.state.collectAsStateWithLifecycle()
    val cState by collection.state.collectAsStateWithLifecycle()

    // Poslední nabízený candidate si držíme, ať po jeho vyčištění umíme vykreslit Splnění.
    var lastCandidate by remember { mutableStateOf<CollectionCandidate?>(null) }
    LaunchedEffect(cState.candidate) { cState.candidate?.let { lastCandidate = it } }

    var dismissedControlId by rememberSaveable { mutableStateOf<String?>(null) }

    if (showScan) {
        ScanModal(
            state = tState,
            onSimulateStart = { timing.onQrScanned(qrConfig.payload(qrConfig.startKeyword, routeId)) },
            onSimulateFinish = { timing.onQrScanned(qrConfig.payload(qrConfig.finishKeyword, routeId)) },
            onClose = onCloseScan,
        )
    }

    cState.candidate?.let { candidate ->
        CollectionOfferSheet(
            candidate = candidate,
            isCollecting = cState.isCollecting,
            onCollect = collection::confirm,
            lastResult = cState.lastResult,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    horizontal = Ta33Theme.spacing.x5,
                    vertical = Ta33Theme.spacing.x10 + Ta33Theme.spacing.x4,
                ),
        )
    }

    // Splnění ukážeme jen pro candidate sebraný v TOMTO mountu (`lastCandidate`). Tím se
    // nezobrazí zastaralý `lastResult` z předchozího běhu (VM ho sám nevyčistí) a po rotaci
    // se úspěch nechová jako trvalý overlay.
    val justCollected = cState.lastResult as? CollectionOutcome.JustCollected
    val collectedControl = lastCandidate?.control
    if (justCollected != null && collectedControl != null && justCollected.controlId != dismissedControlId) {
        SplneniScreen(
            controlName = collectedControl.name,
            ordinal = collectedControl.ordinal,
            elapsedFormatted = tState.elapsedFormatted,
            onClose = { dismissedControlId = justCollected.controlId },
        )
    }
}

/** Simulační QR payload sestavený z aktivního formátu ([QrTimingConfig]) + aktivní `routeId`. */
private fun QrTimingConfig.payload(keyword: String, routeId: String): String =
    listOf(scheme, keyword, routeId).joinToString(separator)
