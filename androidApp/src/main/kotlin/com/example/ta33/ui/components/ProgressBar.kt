package com.example.ta33.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ta33.ui.theme.Ta33Theme

/**
 * Vodorovný pill progress bar — track `surfaceVariant`, fill `primary`, výška 10.
 * `fraction` se ořízne do 0..1.
 */
@Composable
fun Ta33ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = Ta33Theme.radius.pill,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary, Ta33Theme.radius.pill),
        )
    }
}

@Preview
@Composable
private fun Ta33ProgressBarPreview() {
    Ta33Theme {
        Ta33ProgressBar(fraction = 0.6f, modifier = Modifier.padding(Ta33Theme.spacing.x5))
    }
}
