package com.example.ta33.ui.denik

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ta33.domain.model.DenikStop
import com.example.ta33.domain.model.DenikUiState
import com.example.ta33.domain.model.ElevationProfile
import com.example.ta33.domain.model.StopStatus
import com.example.ta33.domain.model.WaypointKind
import com.example.ta33.ui.components.Overline
import com.example.ta33.ui.components.PaperCard
import com.example.ta33.ui.components.Ta33Icons
import com.example.ta33.ui.format.formatClock
import com.example.ta33.ui.format.formatKm
import com.example.ta33.ui.theme.Ta33Theme
import kotlin.math.roundToInt

/** Vycentrovaný spinner na cream pozadí (splash řeší app shell). */
@Composable
fun DenikLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Deník dle kanonického `VariantPrehled`: tmavý header (TRASA/shortId/Přepnout + 3 staty),
 * timeline „Kontroly na trase" (start/kontroly/cíl s km, úsekem, mezičasem, stavem) a výškový profil.
 */
@Composable
fun DenikVariantPrehled(
    state: DenikUiState,
    onSwitch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val routeColor = routeColorOf(state.shortId)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Ta33Theme.spacing.x4),
        verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x4),
    ) {
        RouteSummaryHeader(state = state, onSwitch = onSwitch)

        SectionHeader(text = "Kontroly na trase")

        Timeline(stops = state.stops)

        state.elevation?.let { profile ->
            ElevationCard(profile = profile, routeColor = routeColor)
        }
    }
}

// ── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun RouteSummaryHeader(state: DenikUiState, onSwitch: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Ta33Theme.colors.identityBg,
        shape = Ta33Theme.radius.lg,
    ) {
        Column(modifier = Modifier.padding(horizontal = Ta33Theme.spacing.x5, vertical = Ta33Theme.spacing.x4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Overline(text = "Trasa", color = Ta33Theme.colors.fgOnDarkMuted)
                    Text(
                        text = state.shortId,
                        style = MaterialTheme.typography.displayMedium,
                        color = Ta33Theme.colors.fgOnDark,
                    )
                }
                SwitchPill(enabled = state.canSwitch, onClick = onSwitch)
            }
            Spacer(Modifier.height(Ta33Theme.spacing.x3))
            Row(horizontalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x6)) {
                HeaderStat(value = "${formatKm(state.distanceKm)} km", label = "Délka")
                HeaderStat(value = state.startTimeMillis?.let { formatClock(it) } ?: EM_DASH, label = "Čas startu")
                HeaderStat(value = state.finishTimeMillis?.let { formatClock(it) } ?: EM_DASH, label = "Finální čas")
            }
        }
    }
}

@Composable
private fun SwitchPill(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        color = Ta33Theme.colors.fgOnDark.copy(alpha = 0.12f),
        shape = Ta33Theme.radius.pill,
        modifier = Modifier
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Ta33Theme.spacing.x3, vertical = Ta33Theme.spacing.x2),
            horizontalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Přepnout",
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) Ta33Theme.colors.fgOnDark else Ta33Theme.colors.fgOnDarkMuted,
            )
            Icon(
                imageVector = Ta33Icons.Swap,
                contentDescription = null,
                tint = if (enabled) Ta33Theme.colors.fgOnDark else Ta33Theme.colors.fgOnDarkMuted,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun HeaderStat(value: String, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x1)) {
        Text(text = value, style = MaterialTheme.typography.displaySmall, color = Ta33Theme.colors.fgOnDark)
        Overline(text = label, color = Ta33Theme.colors.fgOnDarkMuted)
    }
}

// ── Timeline ────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x2)) {
        Overline(text = text)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

@Composable
private fun Timeline(stops: List<DenikStop>) {
    Column {
        stops.forEachIndexed { index, stop ->
            TimelineRow(stop = stop, hasNext = index < stops.lastIndex)
        }
    }
}

@Composable
private fun TimelineRow(stop: DenikStop, hasNext: Boolean) {
    val colors = Ta33Theme.colors
    val connectorColor = if (stop.status == StopStatus.DONE) colors.success else colors.kpLockedBg

    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        // rail: node + connector
        Column(
            modifier = Modifier.width(38.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TimelineNode(status = stop.status, kind = stop.kind, ordinal = stop.controlOrdinal)
            if (hasNext) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .padding(vertical = Ta33Theme.spacing.x1)
                        .background(connectorColor),
                )
            }
        }
        Spacer(Modifier.width(Ta33Theme.spacing.x4))
        // content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (hasNext) Ta33Theme.spacing.x5 else Ta33Theme.spacing.none),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                LabelWithStatus(stop = stop, modifier = Modifier.weight(1f, fill = false))
                Text(
                    text = "${formatKm(stop.km)} km",
                    style = Ta33Theme.typography.display3.copy(fontSize = 16.sp),
                    color = colors.fgStrong,
                )
            }
            Text(
                text = stop.name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.fgStrong,
            )
            stop.segmentKm?.let { segment ->
                Spacer(Modifier.height(Ta33Theme.spacing.x2))
                SegmentChip(segmentKm = segment)
            }
        }
    }
}

@Composable
private fun LabelWithStatus(stop: DenikStop, modifier: Modifier = Modifier) {
    val colors = Ta33Theme.colors
    val labelColor = when (stop.status) {
        StopStatus.DONE -> colors.success
        StopStatus.NEXT -> MaterialTheme.colorScheme.primary
        StopStatus.UPCOMING -> colors.fgMuted
    }
    val statusText = when {
        stop.kind == WaypointKind.START && stop.startTimeMillis != null ->
            " · ${formatClock(stop.startTimeMillis!!)}"
        stop.status == StopStatus.DONE -> " · splněno"
        stop.status == StopStatus.NEXT -> " · následující"
        else -> ""
    }
    Text(
        text = "${stop.label.uppercase()}$statusText",
        style = MaterialTheme.typography.labelSmall,
        color = labelColor,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun SegmentChip(segmentKm: Double) {
    Surface(color = Ta33Theme.colors.creamDeep, shape = Ta33Theme.radius.sm) {
        Row(
            modifier = Modifier.padding(horizontal = Ta33Theme.spacing.x3, vertical = Ta33Theme.spacing.x1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x2),
        ) {
            Text(
                text = "úsek ${formatKm1(segmentKm)} km",
                style = MaterialTheme.typography.bodyMedium,
                color = Ta33Theme.colors.fgMuted,
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(12.dp)
                    .background(Ta33Theme.colors.kpLockedBg),
            )
            Text(
                text = "mezičas —:—",
                style = MaterialTheme.typography.bodyMedium,
                color = Ta33Theme.colors.fgFaint,
            )
        }
    }
}

// ── Elevation ───────────────────────────────────────────────────────────────

@Composable
private fun ElevationCard(profile: ElevationProfile, routeColor: Color) {
    PaperCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Overline(text = "Výškový profil")
            Row(horizontalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x4)) {
                ClimbStat(icon = Ta33Icons.ArrowUpRight, meters = profile.ascentMeters)
                ClimbStat(icon = Ta33Icons.ArrowDownRight, meters = profile.descentMeters)
            }
        }
        Spacer(Modifier.height(Ta33Theme.spacing.x3))
        ElevationChart(profile = profile, routeColor = routeColor)
    }
}

@Composable
private fun ClimbStat(icon: androidx.compose.ui.graphics.vector.ImageVector, meters: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x1)) {
        Icon(imageVector = icon, contentDescription = null, tint = Ta33Theme.colors.fgMuted, modifier = Modifier.size(14.dp))
        Text(
            text = "$meters m",
            style = Ta33Theme.typography.display3.copy(fontSize = 15.sp),
            color = Ta33Theme.colors.fgStrong,
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────

private const val EM_DASH = "—"

@Composable
private fun routeColorOf(shortId: String): Color =
    if (shortId == "TA50") Ta33Theme.colors.error else MaterialTheme.colorScheme.primary

/** Km rounded to one decimal with a Czech comma, e.g. 2.6999 → "2,7". */
private fun formatKm1(km: Double): String = formatKm((km * 10).roundToInt() / 10.0)

// ── Previews ──────────────────────────────────────────────────────────────

private fun previewState(): DenikUiState = DenikUiState(
    routeId = "dev-ta33",
    shortId = "TA33",
    distanceKm = 33.2,
    startTimeMillis = 1_726_726_320_000L,
    finishTimeMillis = null,
    ascentMeters = 740,
    descentMeters = 740,
    elevation = ElevationProfile(
        pointsNormalized = listOf(0.05, 0.12, 0.35, 0.62, 0.9, 0.78, 0.55, 0.42, 0.6, 0.48, 0.3, 0.38, 0.22, 0.15, 0.08),
        lowMeters = 462, highMeters = 727, ascentMeters = 740, descentMeters = 740,
        kmTotal = 33.2, tickKm = listOf(8, 16, 24),
    ),
    stops = listOf(
        DenikStop(WaypointKind.START, "Start", "Koupaliště", 0.0, null, StopStatus.DONE, 5.9, startTimeMillis = 1_726_726_320_000L),
        DenikStop(WaypointKind.CONTROL, "Kontrola 1", "Zámek Bischofstein", 5.9, 1, StopStatus.DONE, 6.5),
        DenikStop(WaypointKind.CONTROL, "Kontrola 2", "Vlčí rokle – Pod 7 sch.", 12.4, 2, StopStatus.NEXT, 4.5),
        DenikStop(WaypointKind.CONTROL, "Kontrola 3", "Adršpach – Zámek", 16.9, 3, StopStatus.UPCOMING, 3.4),
        DenikStop(WaypointKind.FINISH, "Cíl", "Koupaliště", 33.2, null, StopStatus.UPCOMING, null, isFinish = true),
    ),
    canSwitch = true,
    loading = false,
)

@Preview(heightDp = 1100)
@Composable
private fun DenikVariantPrehledPreview() {
    Ta33Theme {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            DenikVariantPrehled(state = previewState(), onSwitch = {})
        }
    }
}

@Preview
@Composable
private fun DenikLoadingPreview() {
    Ta33Theme { DenikLoading() }
}
