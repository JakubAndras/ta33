package com.example.ta33.ui.denik

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ta33.domain.model.StopStatus
import com.example.ta33.domain.model.WaypointKind
import com.example.ta33.ui.components.Ta33Icons
import com.example.ta33.ui.theme.Ta33Theme

/**
 * Timeline node (38dp rounded square) for the "Kontroly na trase" rail:
 * - START/FINISH → slate-800 with a map/star icon.
 * - CONTROL done → success, next → orange (+ warm glow), upcoming → paper with a slate-200 outline.
 * The control ordinal is shown as the label (colour per status), faithful to `VariantPrehled`.
 */
@Composable
fun TimelineNode(
    status: StopStatus,
    kind: WaypointKind,
    ordinal: Int?,
    modifier: Modifier = Modifier,
) {
    val colors = Ta33Theme.colors
    val isEnd = kind == WaypointKind.START || kind == WaypointKind.FINISH
    val shape = Ta33Theme.radius.md

    val background = when {
        isEnd -> colors.identityBg
        status == StopStatus.DONE -> colors.success
        status == StopStatus.NEXT -> colors.kpActiveBg
        else -> MaterialTheme.colorScheme.surface // upcoming = paper
    }
    val contentColor = if (status == StopStatus.UPCOMING && !isEnd) colors.fgFaint else colors.fgOnOrange

    val glow = if (status == StopStatus.NEXT && !isEnd) {
        Modifier.shadow(
            elevation = Ta33Theme.spacing.x2,
            shape = shape,
            ambientColor = colors.kpActiveBg,
            spotColor = colors.kpActiveBg,
        )
    } else {
        Modifier
    }
    val outline = if (status == StopStatus.UPCOMING && !isEnd) {
        Modifier.border(2.dp, colors.kpLockedBg, shape)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(38.dp)
            .then(glow)
            .clip(shape)
            .background(background)
            .then(outline),
        contentAlignment = Alignment.Center,
    ) {
        when {
            kind == WaypointKind.FINISH -> Icon(
                imageVector = Ta33Icons.Star,
                contentDescription = null,
                tint = colors.fgOnOrange,
                modifier = Modifier.size(18.dp),
            )
            kind == WaypointKind.START -> Icon(
                imageVector = Ta33Icons.Map,
                contentDescription = null,
                tint = colors.fgOnOrange,
                modifier = Modifier.size(18.dp),
            )
            ordinal != null -> Text(
                text = ordinal.toString(),
                style = Ta33Theme.typography.display3.copy(fontSize = 18.sp, fontWeight = FontWeight.Black),
                color = contentColor,
            )
        }
    }
}

@Preview
@Composable
private fun TimelineNodePreview() {
    Ta33Theme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .background(Ta33Theme.colors.creamDeep)
                .padding(20.dp),
        ) {
            TimelineNode(StopStatus.DONE, WaypointKind.START, null)
            TimelineNode(StopStatus.DONE, WaypointKind.CONTROL, 1)
            TimelineNode(StopStatus.NEXT, WaypointKind.CONTROL, 2)
            TimelineNode(StopStatus.UPCOMING, WaypointKind.CONTROL, 3)
            TimelineNode(StopStatus.UPCOMING, WaypointKind.FINISH, null)
        }
    }
}
