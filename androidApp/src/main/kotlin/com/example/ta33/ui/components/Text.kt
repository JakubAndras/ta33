package com.example.ta33.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.example.ta33.ui.theme.Ta33Theme

/** Uppercase tracked label (overline). Default barva `fgMuted`. */
@Composable
fun Overline(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Ta33Theme.colors.fgMuted,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

@Preview
@Composable
private fun OverlinePreview() {
    Ta33Theme {
        Overline(text = "Hotovo")
    }
}
