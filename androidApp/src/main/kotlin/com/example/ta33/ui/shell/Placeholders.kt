package com.example.ta33.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.ta33.ui.components.Overline
import com.example.ta33.ui.theme.Ta33Theme

/** Sdílené šasi celoobrazovkových stavů: cream pozadí, safe-area inset, vycentrovaný obsah. */
@Composable
private fun CenteredScreen(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Startovní splash - vycentrované „TA33" (display1) + spinner na cream pozadí. */
@Composable
fun SplashView(modifier: Modifier = Modifier) {
    CenteredScreen(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x6),
        ) {
            Text(
                text = "TA33",
                style = MaterialTheme.typography.displayLarge,
                color = Ta33Theme.colors.fgStrong,
            )
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Stub obrazovka pro taby bez hotové implementace (Mapa / Přehled). */
@Composable
fun StubScreen(title: String, modifier: Modifier = Modifier) {
    CenteredScreen(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x2),
        ) {
            Overline(text = title)
            Text(
                text = "Připravujeme",
                style = MaterialTheme.typography.bodyLarge,
                color = Ta33Theme.colors.fgMuted,
            )
        }
    }
}

@Preview
@Composable
private fun SplashViewPreview() {
    Ta33Theme { SplashView() }
}

@Preview
@Composable
private fun StubScreenPreview() {
    Ta33Theme { StubScreen(title = "Mapa") }
}
