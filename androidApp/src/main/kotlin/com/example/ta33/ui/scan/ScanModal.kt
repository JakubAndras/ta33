package com.example.ta33.ui.scan

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.example.ta33.domain.usecase.ScanTimingResult
import com.example.ta33.presentation.TimingUiState
import com.example.ta33.resources.Res
import com.example.ta33.resources.scan_already_finished
import com.example.ta33.resources.scan_already_started
import com.example.ta33.resources.scan_close
import com.example.ta33.resources.scan_finish_before_start
import com.example.ta33.resources.scan_finished
import com.example.ta33.resources.scan_hint
import com.example.ta33.resources.scan_not_timing
import com.example.ta33.resources.scan_run_not_found
import com.example.ta33.resources.scan_sim_finish
import com.example.ta33.resources.scan_sim_start
import com.example.ta33.resources.scan_started
import com.example.ta33.resources.scan_title
import com.example.ta33.resources.scan_wrong_route
import com.example.ta33.ui.components.OutlineButton
import com.example.ta33.ui.components.PrimaryButton
import com.example.ta33.ui.components.Ta33Icons
import com.example.ta33.ui.theme.Ta33Theme
import org.jetbrains.compose.resources.stringResource

/**
 * Celoobrazovkový QR sken (FR-09) - slate-900 pozadí, oranžový scan rámeček s pulzující linkou.
 * Kamera je zatím zástupná plocha; start/cíl se simuluje tlačítky, která feednou payload do
 * [com.example.ta33.presentation.TimingViewModel.onQrScanned]. Výsledek posledního skenu
 * ([TimingUiState.lastScan]) se zobrazí jako hláška pod tlačítky.
 *
 * TODO(device): nahradit zástupnou plochu CameraX PreviewView + ML Kit BarcodeScanner → onQrScanned(raw)
 */
@Composable
fun ScanModal(
    state: TimingUiState,
    onSimulateStart: () -> Unit,
    onSimulateFinish: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Ta33Theme.colors.scanBg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(Ta33Theme.spacing.x5),
        ) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
                Icon(
                    imageVector = Ta33Icons.Close,
                    contentDescription = stringResource(Res.string.scan_close),
                    tint = Ta33Theme.colors.fgOnDark,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x6, Alignment.CenterVertically),
            ) {
                ScanFrame()
                Text(
                    text = stringResource(Res.string.scan_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = Ta33Theme.colors.fgOnDark,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(Res.string.scan_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ta33Theme.colors.fgOnDarkMuted,
                    textAlign = TextAlign.Center,
                )
                scanResultMessage(state.lastScan)?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x3)) {
                PrimaryButton(text = stringResource(Res.string.scan_sim_start), onClick = onSimulateStart)
                OutlineButton(text = stringResource(Res.string.scan_sim_finish), onClick = onSimulateFinish)
            }
        }
    }
}

/** Oranžové L-rohy (reuse [Ta33Icons.Scan]) + vodorovná pulzující linka (1.6s loop, 0.7→1.0). */
@Composable
private fun ScanFrame(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scan-line")
    val alpha by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scan-line-alpha",
    )
    Box(
        modifier = modifier.size(Ta33Theme.spacing.x10 * 3),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Ta33Icons.Scan,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(Ta33Theme.spacing.x1 / 2)
                .graphicsLayer { this.alpha = alpha }
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** Mapuje výsledek skenu (FR-09) na lokalizovanou hlášku. Null = zatím nic naskenováno. */
@Composable
private fun scanResultMessage(result: ScanTimingResult?): String? = when (result) {
    null -> null
    is ScanTimingResult.Started -> stringResource(Res.string.scan_started)
    is ScanTimingResult.Finished -> stringResource(Res.string.scan_finished)
    ScanTimingResult.AlreadyStarted -> stringResource(Res.string.scan_already_started)
    ScanTimingResult.AlreadyFinished -> stringResource(Res.string.scan_already_finished)
    ScanTimingResult.FinishBeforeStart -> stringResource(Res.string.scan_finish_before_start)
    is ScanTimingResult.WrongRoute -> stringResource(Res.string.scan_wrong_route)
    is ScanTimingResult.NotATimingQr -> stringResource(Res.string.scan_not_timing)
    is ScanTimingResult.RunNotFound -> stringResource(Res.string.scan_run_not_found)
}

@Preview
@Composable
private fun ScanModalDefaultPreview() {
    Ta33Theme {
        ScanModal(
            state = TimingUiState(),
            onSimulateStart = {},
            onSimulateFinish = {},
            onClose = {},
        )
    }
}

@Preview
@Composable
private fun ScanModalStartedPreview() {
    Ta33Theme {
        ScanModal(
            state = TimingUiState(lastScan = ScanTimingResult.Started(startedAtMillis = 0L)),
            onSimulateStart = {},
            onSimulateFinish = {},
            onClose = {},
        )
    }
}

@Preview
@Composable
private fun ScanModalWrongRoutePreview() {
    Ta33Theme {
        ScanModal(
            state = TimingUiState(
                lastScan = ScanTimingResult.WrongRoute(expectedRouteId = "TA33", scannedRouteId = "TA50"),
            ),
            onSimulateStart = {},
            onSimulateFinish = {},
            onClose = {},
        )
    }
}
