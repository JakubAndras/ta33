package com.example.ta33.ui.preparation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.ta33.domain.download.DownloadItemProgress
import com.example.ta33.domain.download.DownloadStatus
import com.example.ta33.domain.download.OfflinePackageProgress
import com.example.ta33.domain.model.NetworkPreference
import com.example.ta33.presentation.DownloadUiState
import com.example.ta33.resources.Res
import com.example.ta33.resources.denik_event_date
import com.example.ta33.resources.denik_event_place
import com.example.ta33.resources.denik_event_sub
import com.example.ta33.resources.prep_download_cta
import com.example.ta33.resources.prep_downloading
import com.example.ta33.resources.prep_error
import com.example.ta33.resources.prep_intro
import com.example.ta33.resources.prep_pause
import com.example.ta33.resources.prep_resume
import com.example.ta33.resources.prep_retry
import com.example.ta33.resources.prep_waiting_wifi
import com.example.ta33.resources.prep_wifi_only
import com.example.ta33.ui.components.IdentityCard
import com.example.ta33.ui.components.OutlineButton
import com.example.ta33.ui.components.Overline
import com.example.ta33.ui.components.PaperCard
import com.example.ta33.ui.components.PrimaryButton
import com.example.ta33.ui.components.SettingRow
import com.example.ta33.ui.components.Ta33Icons
import com.example.ta33.ui.components.Ta33ProgressBar
import com.example.ta33.ui.components.WarningBanner
import com.example.ta33.ui.theme.Ta33Theme
import org.jetbrains.compose.resources.stringResource

/**
 * Bezstavová „Příprava dat akce" (FR-11): identity karta + karta s výzvou / průběhem /
 * pauzou / chybou podle [DownloadUiState]. Přechod po dokončení řeší shell (readiness gate).
 */
@Composable
fun PreparationContent(
    state: DownloadUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onToggleWifiOnly: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Ta33Theme.spacing.x5),
        verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x5),
    ) {
        IdentityCard(
            date = stringResource(Res.string.denik_event_date),
            place = stringResource(Res.string.denik_event_place),
            sub = stringResource(Res.string.denik_event_sub),
        )

        PaperCard {
            Column(verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x4)) {
                when (state.progress.overallStatus) {
                    DownloadStatus.IDLE -> IdleSection(
                        wifiOnly = state.networkPreference == NetworkPreference.WIFI_ONLY,
                        blocked = state.blockedByNetwork,
                        onToggleWifiOnly = onToggleWifiOnly,
                        onStart = onStart,
                    )
                    DownloadStatus.DOWNLOADING -> DownloadingSection(
                        progress = state.progress,
                        onPause = onPause,
                    )
                    DownloadStatus.PAUSED -> PausedSection(
                        progress = state.progress,
                        blocked = state.blockedByNetwork,
                        onResume = onResume,
                    )
                    DownloadStatus.ERROR -> ErrorSection(onRetry = onRetry)
                    DownloadStatus.DONE -> DoneSection()
                }
            }
        }

        if (state.blockedByNetwork) {
            WarningBanner(text = stringResource(Res.string.prep_waiting_wifi))
        }
    }
}

@Composable
private fun IdleSection(
    wifiOnly: Boolean,
    blocked: Boolean,
    onToggleWifiOnly: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    Text(
        text = stringResource(Res.string.prep_intro),
        style = MaterialTheme.typography.bodyLarge,
        color = Ta33Theme.colors.fgMuted,
    )
    SettingRow(
        label = stringResource(Res.string.prep_wifi_only),
        showDivider = false,
    ) {
        Switch(checked = wifiOnly, onCheckedChange = onToggleWifiOnly)
    }
    if (!blocked) {
        PrimaryButton(
            text = stringResource(Res.string.prep_download_cta),
            onClick = onStart,
        )
    }
}

@Composable
private fun DownloadingSection(
    progress: OfflinePackageProgress,
    onPause: () -> Unit,
) {
    Overline(text = stringResource(Res.string.prep_downloading))
    Ta33ProgressBar(fraction = progress.overallFraction.toFloat())
    progress.items.forEach { ItemRow(it) }
    OutlineButton(text = stringResource(Res.string.prep_pause), onClick = onPause)
}

@Composable
private fun PausedSection(
    progress: OfflinePackageProgress,
    blocked: Boolean,
    onResume: () -> Unit,
) {
    Ta33ProgressBar(fraction = progress.overallFraction.toFloat())
    progress.items.forEach { ItemRow(it) }
    if (!blocked) {
        PrimaryButton(
            text = stringResource(Res.string.prep_resume),
            onClick = onResume,
        )
    }
}

@Composable
private fun ErrorSection(onRetry: () -> Unit) {
    Text(
        text = stringResource(Res.string.prep_error),
        style = Ta33Theme.typography.bodyStrong,
        color = Ta33Theme.colors.error,
    )
    PrimaryButton(text = stringResource(Res.string.prep_retry), onClick = onRetry)
}

/** Přechodný stav — shell přepne na Main, jakmile se readiness stane READY. */
@Composable
private fun DoneSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/** Řádek balíčku: název vlevo, vpravo stav (fajfka / vykřičník / procenta). */
@Composable
private fun ItemRow(item: DownloadItemProgress) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x3),
    ) {
        Text(
            text = item.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = Ta33Theme.colors.fgDefault,
        )
        when (item.status) {
            DownloadStatus.DONE -> Icon(
                imageVector = Ta33Icons.Check,
                contentDescription = null,
                tint = Ta33Theme.colors.success,
                modifier = Modifier.size(Ta33Theme.spacing.x5),
            )
            DownloadStatus.ERROR -> Icon(
                imageVector = Ta33Icons.Zap,
                contentDescription = null,
                tint = Ta33Theme.colors.error,
                modifier = Modifier.size(Ta33Theme.spacing.x5),
            )
            else -> Text(
                text = "${(item.fraction * 100).toInt()} %",
                style = MaterialTheme.typography.bodyMedium,
                color = Ta33Theme.colors.fgMuted,
            )
        }
    }
}

// ---- Previews ---------------------------------------------------------------

private fun previewItems(
    contentStatus: DownloadStatus,
    tilesStatus: DownloadStatus,
) = listOf(
    DownloadItemProgress(
        id = "content",
        label = "Trasa a kontroly",
        status = contentStatus,
        bytesDownloaded = 800_000,
        totalBytes = 1_000_000,
    ),
    DownloadItemProgress(
        id = "tiles:adrspach",
        label = "Mapové dlaždice",
        status = tilesStatus,
        bytesDownloaded = 30_000_000,
        totalBytes = 83_000_000,
    ),
)

private fun previewState(
    status: DownloadStatus,
    fraction: Double = 0.0,
    items: List<DownloadItemProgress> = emptyList(),
    blocked: Boolean = false,
    pref: NetworkPreference = NetworkPreference.WIFI_ONLY,
) = DownloadUiState(
    progress = OfflinePackageProgress(items = items, overallStatus = status, overallFraction = fraction),
    networkPreference = pref,
    blockedByNetwork = blocked,
)

@Composable
private fun PreparationPreview(state: DownloadUiState) {
    Ta33Theme {
        PreparationContent(
            state = state,
            onStart = {},
            onPause = {},
            onResume = {},
            onRetry = {},
            onToggleWifiOnly = {},
        )
    }
}

@Preview
@Composable
private fun PreparationIdlePreview() {
    PreparationPreview(previewState(DownloadStatus.IDLE))
}

@Preview
@Composable
private fun PreparationDownloadingPreview() {
    PreparationPreview(
        previewState(
            status = DownloadStatus.DOWNLOADING,
            fraction = 0.42,
            items = previewItems(DownloadStatus.DONE, DownloadStatus.DOWNLOADING),
        ),
    )
}

@Preview
@Composable
private fun PreparationPausedPreview() {
    PreparationPreview(
        previewState(
            status = DownloadStatus.PAUSED,
            fraction = 0.42,
            items = previewItems(DownloadStatus.DONE, DownloadStatus.PAUSED),
        ),
    )
}

@Preview
@Composable
private fun PreparationErrorPreview() {
    PreparationPreview(
        previewState(
            status = DownloadStatus.ERROR,
            fraction = 0.42,
            items = previewItems(DownloadStatus.DONE, DownloadStatus.ERROR),
        ),
    )
}

@Preview
@Composable
private fun PreparationBlockedPreview() {
    PreparationPreview(previewState(status = DownloadStatus.IDLE, blocked = true))
}
