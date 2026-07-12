package com.example.ta33.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ta33.domain.model.MapaUiState
import com.example.ta33.domain.model.RouteWaypoint
import com.example.ta33.domain.model.TrailMark
import com.example.ta33.domain.model.TurnDirection
import com.example.ta33.domain.model.WaypointKind
import com.example.ta33.presentation.MapaViewModel
import com.example.ta33.ui.components.Overline
import com.example.ta33.ui.components.Ta33Icons
import com.example.ta33.ui.format.formatKm
import com.example.ta33.ui.theme.Ta33Theme
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stateful wrapper of Mapa (RD-02): observes [MapaViewModel] (catalog + active route) and renders
 * the canonical `VariantHybrid` (schematic map + compact itinerary). Route switch → [MapaViewModel.toggle];
 * pin/row tap → [MapaViewModel.highlight] cross-highlight.
 */
@Composable
fun MapaScreen(mapaViewModel: MapaViewModel = koinViewModel()) {
    val state by mapaViewModel.state.collectAsStateWithLifecycle()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (state.loading) {
            MapaLoading()
        } else {
            MapaVariantHybrid(
                state = state,
                onSwitch = mapaViewModel::toggle,
                onHighlight = mapaViewModel::highlight,
            )
        }
    }
}

@Composable
private fun MapaLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Mapa dle kanonického `VariantHybrid`: nahoře schematická mapa (fixní výška) + route chip s
 * přepínačem, dole srolovatelný kompaktní itinerář (km + KP badge + název + směr/značka).
 */
@Composable
fun MapaVariantHybrid(
    state: MapaUiState,
    onSwitch: () -> Unit,
    onHighlight: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val routeColor = routeColorOf(state.shortId)
    val topInset = WindowInsets.safeDrawing.asPaddingValues()

    Column(modifier = modifier.fillMaxSize()) {
        // map header (fixed height) with the route chip overlaid on top
        Box(modifier = Modifier.fillMaxWidth().height(MAP_HEIGHT)) {
            SchematicMap(
                controlsCount = state.controlsCount,
                routeColor = routeColor,
                dashed = state.shortId == "TA50",
                highlightedControl = state.highlightedControl,
                onPinClick = { ordinal -> onHighlight(if (state.highlightedControl == ordinal) null else ordinal) },
                modifier = Modifier.fillMaxSize(),
            )
            RouteChip(
                state = state,
                onSwitch = onSwitch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = topInset.calculateTopPadding() + Ta33Theme.spacing.x3,
                        start = Ta33Theme.spacing.x4,
                        end = Ta33Theme.spacing.x4,
                    ),
            )
        }
        // compact itinerary
        Itinerary(
            waypoints = state.waypoints,
            highlightedControl = state.highlightedControl,
            onHighlight = onHighlight,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private val MAP_HEIGHT = 452.dp

// ── Route chip ───────────────────────────────────────────────────────────────

@Composable
private fun RouteChip(state: MapaUiState, onSwitch: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Ta33Theme.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            color = colors.fgOnOrange,
            shape = Ta33Theme.radius.pill,
            shadowElevation = Ta33Theme.spacing.x1,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Ta33Theme.spacing.x3, vertical = Ta33Theme.spacing.x2),
                horizontalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(colors.identityBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.letter,
                        style = Ta33Theme.typography.display3.copy(fontSize = 13.sp, fontWeight = FontWeight.Black),
                        color = colors.fgOnOrange,
                    )
                }
                Text(
                    text = "Trasa ${state.shortId} · ${formatKm(state.distanceKm)} km · ↑${state.ascentMeters} m",
                    style = Ta33Theme.typography.bodyStrong.copy(fontSize = 13.5.sp),
                    color = colors.fgStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Surface(
            color = colors.fgOnOrange,
            shape = Ta33Theme.radius.pill,
            shadowElevation = Ta33Theme.spacing.x1,
            modifier = Modifier
                .size(40.dp)
                .then(if (state.canSwitch) Modifier.clickable(onClick = onSwitch) else Modifier),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Ta33Icons.Swap,
                    contentDescription = "Přepnout trasu",
                    tint = if (state.canSwitch) colors.fgStrong else colors.fgFaint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ── Itinerary ────────────────────────────────────────────────────────────────

@Composable
private fun Itinerary(
    waypoints: List<RouteWaypoint>,
    highlightedControl: Int?,
    onHighlight: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = Ta33Theme.spacing.x4,
            end = Ta33Theme.spacing.x4,
            top = Ta33Theme.spacing.x3,
            bottom = Ta33Theme.spacing.x4,
        ),
        verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x2),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = Ta33Theme.spacing.x1),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Overline(text = "Itinerář")
                Text(
                    text = "Kontroly zvýrazněné",
                    style = MaterialTheme.typography.labelMedium,
                    color = Ta33Theme.colors.fgMuted,
                )
            }
        }
        items(waypoints, key = { it.index }) { wp ->
            ItineraryRow(
                waypoint = wp,
                active = wp.kind == WaypointKind.CONTROL && highlightedControl == wp.controlOrdinal,
                onClick = {
                    if (wp.kind == WaypointKind.CONTROL) {
                        onHighlight(if (highlightedControl == wp.controlOrdinal) null else wp.controlOrdinal)
                    }
                },
            )
        }
    }
}

@Composable
private fun ItineraryRow(
    waypoint: RouteWaypoint,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = Ta33Theme.colors
    val isKp = waypoint.kind == WaypointKind.CONTROL
    val isEnd = waypoint.kind == WaypointKind.START || waypoint.kind == WaypointKind.FINISH
    val isFinish = waypoint.kind == WaypointKind.FINISH

    val bg = if (active) colors.identityBg else MaterialTheme.colorScheme.surface
    val kmColor = when {
        active -> colors.fgOnOrange
        isEnd || isKp -> MaterialTheme.colorScheme.primary
        else -> colors.fgStrong
    }
    val nameColor = if (active) colors.fgOnOrange else colors.fgStrong
    val glyphColor = if (active) colors.fgOnOrange else colors.fgMuted

    Surface(
        color = bg,
        shape = Ta33Theme.radius.md,
        border = if (isKp && !active) BorderStroke(1.5.dp, colors.kpActiveBg.copy(alpha = 0.35f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isKp) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Ta33Theme.spacing.x3, vertical = Ta33Theme.spacing.x2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x3),
        ) {
            Text(
                text = formatKm(waypoint.km),
                modifier = Modifier.width(40.dp),
                style = Ta33Theme.typography.display3.copy(fontSize = 17.sp, fontWeight = FontWeight.Black),
                color = kmColor,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                maxLines = 1,
            )
            if (isKp) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.kpActiveBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = waypoint.controlOrdinal?.toString().orEmpty(),
                        style = Ta33Theme.typography.display3.copy(fontSize = 13.sp, fontWeight = FontWeight.Black),
                        color = colors.fgOnOrange,
                    )
                }
            }
            Text(
                text = waypoint.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = if (isKp || isEnd) FontWeight.Bold else FontWeight.SemiBold,
                ),
                color = nameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isFinish) {
                Icon(
                    imageVector = Ta33Icons.Star,
                    contentDescription = null,
                    tint = if (active) colors.fgOnOrange else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                DirArrow(dir = waypoint.direction, size = 20.dp, color = glyphColor)
            }
            MarkBadge(mark = waypoint.mark, markNumber = waypoint.markNumber, size = 22.dp)
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────

@Composable
private fun routeColorOf(shortId: String): Color =
    if (shortId == "TA50") Ta33Theme.colors.error else MaterialTheme.colorScheme.primary

// ── Previews ─────────────────────────────────────────────────────────────────

private fun previewState(): MapaUiState = MapaUiState(
    routeId = "dev-ta33",
    shortId = "TA33",
    letter = "A",
    distanceKm = 33.2,
    ascentMeters = 740,
    controlsCount = 5,
    waypoints = listOf(
        RouteWaypoint(0, "Koupaliště", 0.0, WaypointKind.START, null, TurnDirection.UP, TrailMark.MODRA),
        RouteWaypoint(1, "Teplice – Nádržní ul.", 1.9, WaypointKind.WAYPOINT, null, TurnDirection.RIGHT, TrailMark.ZLUTA),
        RouteWaypoint(2, "Zámek Bischofstein", 5.9, WaypointKind.CONTROL, 1, TurnDirection.UP, TrailMark.ZLUTA),
        RouteWaypoint(3, "Vlčí rokle – Pod 7 sch.", 12.4, WaypointKind.CONTROL, 2, TurnDirection.LEFT, TrailMark.ZLUTA),
        RouteWaypoint(4, "Zdoňov křižovatka", 21.6, WaypointKind.WAYPOINT, null, TurnDirection.UP, TrailMark.CYKLO, "4036"),
        RouteWaypoint(5, "Koupaliště", 33.2, WaypointKind.FINISH, null, TurnDirection.UP, TrailMark.MODRA),
    ),
    highlightedControl = 2,
    canSwitch = true,
    loading = false,
)

@Preview(heightDp = 900)
@Composable
private fun MapaVariantHybridPreview() {
    Ta33Theme {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            MapaVariantHybrid(state = previewState(), onSwitch = {}, onHighlight = {})
        }
    }
}
