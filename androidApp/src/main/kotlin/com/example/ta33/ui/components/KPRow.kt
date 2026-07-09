package com.example.ta33.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.example.ta33.domain.model.ControlPointState
import com.example.ta33.ui.theme.Ta33Theme

/**
 * Jeden řádek deníku: 56dp stavový swatch + titul (bodyStrong) + podtitul.
 * Barvy swatche a chování řádku odvozeny z [state] (a [isFinish]) přes `Ta33Theme.colors`.
 */
@Composable
fun KPRow(
    ordinal: Int,
    title: String,
    subtitle: String,
    state: ControlPointState,
    modifier: Modifier = Modifier,
    isFinish: Boolean = false,
) {
    val isLocked = state == ControlPointState.LOCKED
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isLocked) 0.55f else 1f)
            .shadow(Ta33Theme.spacing.x1, Ta33Theme.radius.lg),
        color = MaterialTheme.colorScheme.surface,
        shape = Ta33Theme.radius.lg,
    ) {
        Row(
            modifier = Modifier.padding(Ta33Theme.spacing.x3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x3),
        ) {
            Swatch(ordinal = ordinal, state = state, isFinish = isFinish)
            Column(
                modifier = Modifier.padding(vertical = Ta33Theme.spacing.x1),
                verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x1),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ta33Theme.colors.fgStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ta33Theme.colors.fgMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Swatch(
    ordinal: Int,
    state: ControlPointState,
    isFinish: Boolean,
) {
    val colors = Ta33Theme.colors
    val bg: Color
    val fg: Color
    val glow: Boolean
    when (state) {
        ControlPointState.DONE -> {
            bg = colors.kpDoneBg; fg = colors.kpDoneFg; glow = false
        }
        ControlPointState.ACTIVE -> {
            bg = colors.kpActiveBg; fg = colors.kpActiveFg; glow = true
        }
        ControlPointState.FINISH -> {
            bg = colors.kpFinishBg; fg = colors.kpFinishFg; glow = false
        }
        ControlPointState.LOCKED -> {
            bg = colors.kpLockedBg; fg = colors.kpLockedFg; glow = false
        }
    }
    val shape = Ta33Theme.radius.md
    Surface(
        modifier = Modifier
            .size(Ta33Theme.spacing.x9)
            .then(
                if (glow) {
                    Modifier.shadow(
                        elevation = Ta33Theme.spacing.x2,
                        shape = shape,
                        ambientColor = colors.kpActiveBg,
                        spotColor = colors.kpActiveBg,
                    )
                } else {
                    Modifier
                },
            ),
        color = bg,
        shape = shape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                state == ControlPointState.DONE -> Icon(
                    imageVector = Ta33Icons.Check,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(Ta33Theme.spacing.x6),
                )
                isFinish || state == ControlPointState.FINISH -> Icon(
                    imageVector = Ta33Icons.Star,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(Ta33Theme.spacing.x6),
                )
                else -> Text(
                    text = ordinal.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = fg,
                )
            }
        }
    }
}

@Preview
@Composable
private fun KPRowStatesPreview() {
    Ta33Theme {
        Column(
            modifier = Modifier.padding(Ta33Theme.spacing.x5),
            verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x3),
        ) {
            KPRow(
                ordinal = 1,
                title = "KP-01 · Start",
                subtitle = "Splněno · 08:14",
                state = ControlPointState.DONE,
            )
            KPRow(
                ordinal = 2,
                title = "KP-02 · Sloní pramen",
                subtitle = "Další",
                state = ControlPointState.ACTIVE,
            )
            KPRow(
                ordinal = 3,
                title = "KP-03 · Vyhlídka",
                subtitle = "Zamčeno",
                state = ControlPointState.LOCKED,
            )
            KPRow(
                ordinal = 5,
                title = "Cíl · Adršpach",
                subtitle = "Zamčeno",
                state = ControlPointState.LOCKED,
                isFinish = true,
            )
            KPRow(
                ordinal = 5,
                title = "Cíl · Adršpach",
                subtitle = "Splněno · 11:02",
                state = ControlPointState.FINISH,
                isFinish = true,
            )
        }
    }
}
