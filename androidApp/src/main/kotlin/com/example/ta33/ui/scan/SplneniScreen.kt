package com.example.ta33.ui.scan

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.example.ta33.resources.Res
import com.example.ta33.resources.splneni_continue
import com.example.ta33.resources.splneni_title
import com.example.ta33.ui.components.Overline
import com.example.ta33.ui.components.PrimaryButton
import com.example.ta33.ui.components.Ta33Icons
import com.example.ta33.ui.theme.Ta33Theme
import org.jetbrains.compose.resources.stringResource

/**
 * Celoobrazovková zelená obrazovka úspěchu (FR-08 `JustCollected`): fajfka v kruhu,
 * název kontroly a čas. „Pokračovat na trase" zavře overlay ([onClose]).
 */
@Composable
fun SplneniScreen(
    controlName: String,
    ordinal: Int?,
    elapsedFormatted: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Ta33Theme.colors.success,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(Ta33Theme.spacing.x5),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x5, Alignment.CenterVertically),
            ) {
                CheckBadge()
                Overline(
                    text = "KP · ${stringResource(Res.string.splneni_title)}",
                    color = Ta33Theme.colors.fgOnDark,
                )
                Text(
                    text = if (ordinal != null) "KP-$ordinal · $controlName" else controlName,
                    style = MaterialTheme.typography.displaySmall,
                    color = Ta33Theme.colors.fgOnDark,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = elapsedFormatted,
                    style = MaterialTheme.typography.displayLarge,
                    color = Ta33Theme.colors.fgOnDark,
                    textAlign = TextAlign.Center,
                )
            }
            PrimaryButton(text = stringResource(Res.string.splneni_continue), onClick = onClose)
        }
    }
}

/** Bílý kruh s fajfkou; jemné scale-in (dur-slow, bez springů). */
@Composable
private fun CheckBadge(modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.6f,
        animationSpec = tween(durationMillis = 320),
        label = "splneni-check-scale",
    )
    LaunchedEffect(Unit) { shown = true }
    Box(
        modifier = modifier
            .scale(scale)
            .size(Ta33Theme.spacing.x10 + Ta33Theme.spacing.x8)
            .clip(CircleShape)
            .background(Ta33Theme.colors.fgOnDark),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Ta33Icons.Check,
            contentDescription = null,
            tint = Ta33Theme.colors.success,
            modifier = Modifier.size(Ta33Theme.spacing.x8),
        )
    }
}

@Preview
@Composable
private fun SplneniScreenPreview() {
    Ta33Theme {
        SplneniScreen(
            controlName = "Sloní pramen",
            ordinal = 2,
            elapsedFormatted = "01:44",
            onClose = {},
        )
    }
}
