package com.example.ta33.ui.denik

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.ControlPointState
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.LogUiState
import com.example.ta33.domain.model.RunLogEntry
import com.example.ta33.resources.Res
import com.example.ta33.resources.denik_download_cta
import com.example.ta33.resources.denik_event_date
import com.example.ta33.resources.denik_event_place
import com.example.ta33.resources.denik_event_sub
import com.example.ta33.resources.denik_finish_title
import com.example.ta33.resources.denik_group_done
import com.example.ta33.resources.denik_group_next
import com.example.ta33.resources.denik_not_downloaded_body
import com.example.ta33.resources.denik_not_downloaded_title
import com.example.ta33.resources.denik_status_done_prefix
import com.example.ta33.resources.denik_status_locked
import com.example.ta33.resources.denik_status_next
import com.example.ta33.ui.components.IdentityCard
import com.example.ta33.ui.components.KPRow
import com.example.ta33.ui.components.OfflineBanner
import com.example.ta33.ui.components.Overline
import com.example.ta33.ui.components.PaperCard
import com.example.ta33.ui.components.PrimaryButton
import com.example.ta33.ui.components.Ta33Icons
import com.example.ta33.ui.components.Ta33ProgressBar
import com.example.ta33.ui.format.formatClock
import com.example.ta33.ui.theme.Ta33Theme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Vycentrovaný spinner na cream pozadí (splash řeší app shell). */
@Composable
fun DenikLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/** Stav „obsah není stažený": identity karta + empty-state karta + CTA. */
@Composable
fun DenikBefore(
    onDownload: () -> Unit,
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x4),
            ) {
                Surface(
                    color = Ta33Theme.colors.warningTint,
                    shape = Ta33Theme.radius.pill,
                ) {
                    Box(
                        modifier = Modifier.size(Ta33Theme.spacing.x9),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Ta33Icons.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Ta33Theme.spacing.x7),
                        )
                    }
                }
                Text(
                    text = stringResource(Res.string.denik_not_downloaded_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Ta33Theme.colors.fgStrong,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(Res.string.denik_not_downloaded_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ta33Theme.colors.fgMuted,
                    textAlign = TextAlign.Center,
                )
                PrimaryButton(
                    text = stringResource(Res.string.denik_download_cta),
                    onClick = onDownload,
                )
            }
        }
    }
}

/** Stav „na trase": offline banner + progress + skupiny KP řádků. */
@Composable
fun DenikOnRoute(
    log: LogUiState,
    routeLabel: String,
    offline: Boolean,
    modifier: Modifier = Modifier,
) {
    val (done, next) = remember(log.entries) {
        log.entries.partition { it.state == ControlPointState.DONE }
    }
    val finishOrdinal = remember(log.entries) { log.entries.maxOfOrNull { it.control.ordinal } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Ta33Theme.spacing.x5),
        verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x5),
    ) {
        if (offline) OfflineBanner()

        ProgressCard(
            routeLabel = routeLabel,
            collected = log.collectedCount,
            total = log.totalCount,
        )

        KPGroup(labelRes = Res.string.denik_group_next, entries = next, finishOrdinal = finishOrdinal)
        KPGroup(labelRes = Res.string.denik_group_done, entries = done, finishOrdinal = finishOrdinal)
    }
}

@Composable
private fun KPGroup(
    labelRes: StringResource,
    entries: List<RunLogEntry>,
    finishOrdinal: Int?,
) {
    if (entries.isEmpty()) return
    Overline(text = stringResource(labelRes))
    Column(verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x3)) {
        entries.forEach { entry ->
            DenikRow(entry = entry, isFinish = entry.control.ordinal == finishOrdinal)
        }
    }
}

@Composable
private fun DenikRow(entry: RunLogEntry, isFinish: Boolean) {
    val control = entry.control
    val title = if (isFinish) {
        "${stringResource(Res.string.denik_finish_title)} · ${control.name}"
    } else {
        "KP-${control.ordinal.toString().padStart(2, '0')} · ${control.name}"
    }
    val subtitle = when (entry.state) {
        ControlPointState.DONE, ControlPointState.FINISH -> {
            val prefix = stringResource(Res.string.denik_status_done_prefix)
            entry.collectedAtMillis?.let { "$prefix · ${formatClock(it)}" } ?: prefix
        }
        ControlPointState.ACTIVE -> stringResource(Res.string.denik_status_next)
        ControlPointState.LOCKED -> stringResource(Res.string.denik_status_locked)
    }
    KPRow(
        ordinal = control.ordinal,
        title = title,
        subtitle = subtitle,
        state = entry.state,
        isFinish = isFinish,
    )
}

@Composable
private fun ProgressCard(routeLabel: String, collected: Int, total: Int) {
    PaperCard {
        Column(verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x3)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = routeLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = Ta33Theme.colors.fgStrong,
                )
                Text(
                    text = "$collected / $total",
                    style = MaterialTheme.typography.displayMedium,
                    color = Ta33Theme.colors.fgStrong,
                )
            }
            ProgressBar(collected = collected, total = total)
        }
    }
}

@Composable
private fun ProgressBar(collected: Int, total: Int) {
    val fraction = if (total == 0) 0f else collected.toFloat() / total.toFloat()
    Ta33ProgressBar(fraction = fraction)
}

// ---- Previews ---------------------------------------------------------------

private fun previewControl(ordinal: Int, name: String) = ControlPoint(
    id = "kp-$ordinal",
    routeId = "route-a",
    ordinal = ordinal,
    name = name,
    location = GeoPoint(0.0, 0.0),
)

private fun previewLog(collected: Int): LogUiState {
    val names = listOf("Start", "Sloní pramen", "Vyhlídka", "Kamenné moře", "Adršpach")
    val entries = names.mapIndexed { index, name ->
        val ordinal = index + 1
        val state = when {
            index < collected -> ControlPointState.DONE
            index == collected -> ControlPointState.ACTIVE
            else -> ControlPointState.LOCKED
        }
        RunLogEntry(
            control = previewControl(ordinal, name),
            state = state,
            collectedAtMillis = if (state == ControlPointState.DONE) 1_726_726_440_000L + index * 600_000L else null,
        )
    }
    return LogUiState(
        entries = entries,
        collectedCount = collected,
        totalCount = names.size,
        loading = false,
    )
}

@Preview
@Composable
private fun DenikLoadingPreview() {
    Ta33Theme { DenikLoading() }
}

@Preview
@Composable
private fun DenikBeforePreview() {
    Ta33Theme { DenikBefore(onDownload = {}) }
}

@Preview
@Composable
private fun DenikOnRoutePreview() {
    Ta33Theme {
        DenikOnRoute(log = previewLog(collected = 2), routeLabel = "Trasa A · 33 km", offline = true)
    }
}

@Preview
@Composable
private fun DenikOnRouteEmptyPreview() {
    Ta33Theme {
        DenikOnRoute(log = previewLog(collected = 0), routeLabel = "Trasa A · 33 km", offline = true)
    }
}
