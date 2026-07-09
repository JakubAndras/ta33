package com.example.ta33.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.example.ta33.ui.theme.Ta33Theme

/**
 * Řádek klíč–hodnota (label vlevo `bodyStrong`/`fgStrong`, hodnota vpravo `body`).
 * Mezi řádky 1px `slate-100` divider (`showDivider`); u prvního řádku vypnout.
 */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Ta33Theme.colors.fgMuted,
    showDivider: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Ta33Theme.spacing.x3),
            horizontalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = Ta33Theme.typography.bodyStrong,
                color = Ta33Theme.colors.fgStrong,
            )
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = valueColor,
                textAlign = TextAlign.End,
            )
        }
    }
}

/**
 * Řádek nastavení: label vlevo (`bodyStrong`/`fgStrong`) + `trailing` slot vpravo
 * (např. [Switch] nebo chevron ikona). Mezi řádky 1px divider (`showDivider`).
 */
@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    trailing: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Ta33Theme.spacing.x3),
            horizontalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = Ta33Theme.typography.bodyStrong,
                color = Ta33Theme.colors.fgStrong,
            )
            trailing()
        }
    }
}

@Preview
@Composable
private fun RowsPreview() {
    Ta33Theme {
        PaperCard(modifier = Modifier.padding(Ta33Theme.spacing.x5)) {
            KeyValueRow(label = "Aktivní trasa", value = "Trasa A · 33,2 km", showDivider = false)
            KeyValueRow(
                label = "Data akce",
                value = "Staženo",
                valueColor = Ta33Theme.colors.success,
            )
            SettingRow(label = "Notifikace") {
                Switch(checked = true, onCheckedChange = {})
            }
            SettingRow(label = "Kontaktovat pořadatele") {
                Text(
                    text = "Spolek TA33",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ta33Theme.colors.fgMuted,
                )
                Icon(
                    imageVector = Ta33Icons.ChevronRight,
                    contentDescription = null,
                    tint = Ta33Theme.colors.fgFaint,
                    modifier = Modifier.size(Ta33Theme.spacing.x5),
                )
            }
        }
    }
}
