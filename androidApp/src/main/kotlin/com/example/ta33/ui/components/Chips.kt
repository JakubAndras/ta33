package com.example.ta33.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.ta33.ui.theme.Ta33Theme

/**
 * `slate-100` chip: velké display číslo + malý overline label. Do flex řady (Mapa/Profil).
 */
@Composable
fun StatChip(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = Ta33Theme.radius.md,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = Ta33Theme.spacing.x4,
                vertical = Ta33Theme.spacing.x3,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x1),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall,
                color = Ta33Theme.colors.fgStrong,
            )
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Ta33Theme.colors.fgMuted,
            )
        }
    }
}

@Preview
@Composable
private fun StatChipPreview() {
    Ta33Theme {
        StatChip(value = "14,1", label = "km ujito", modifier = Modifier.padding(Ta33Theme.spacing.x5))
    }
}
