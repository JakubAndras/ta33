package com.example.ta33.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.tooling.preview.Preview
import com.example.ta33.domain.model.FaqItem
import com.example.ta33.ui.theme.Ta33Theme

/**
 * Rozbalovací FAQ řádek: klikací hlavička (otázka `bodyStrong` + rotující chevron)
 * a [AnimatedVisibility] s odpovědí (`body`/`fgMuted`). Mezi řádky 1px divider.
 */
@Composable
fun FaqRow(
    item: FaqItem,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    initiallyExpanded: Boolean = false,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "faq-chevron",
    )
    Column(modifier = modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = Ta33Theme.spacing.x3),
            horizontalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.question,
                modifier = Modifier.weight(1f),
                style = Ta33Theme.typography.bodyStrong,
                color = Ta33Theme.colors.fgStrong,
            )
            Icon(
                imageVector = Ta33Icons.ChevronRight,
                contentDescription = null,
                tint = Ta33Theme.colors.fgFaint,
                modifier = Modifier
                    .size(Ta33Theme.spacing.x5)
                    .rotate(rotation),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = item.answer,
                modifier = Modifier.padding(bottom = Ta33Theme.spacing.x3),
                style = MaterialTheme.typography.bodyLarge,
                color = Ta33Theme.colors.fgMuted,
            )
        }
    }
}

@Preview
@Composable
private fun FaqRowCollapsedPreview() {
    Ta33Theme {
        PaperCard(modifier = Modifier.padding(Ta33Theme.spacing.x5)) {
            FaqRow(
                item = FaqItem(
                    id = "1",
                    question = "Jak stáhnu data akce?",
                    answer = "V Deníku klepni na Stáhnout data akce, dokud máš signál.",
                ),
                showDivider = false,
            )
        }
    }
}

@Preview
@Composable
private fun FaqRowExpandedPreview() {
    Ta33Theme {
        PaperCard(modifier = Modifier.padding(Ta33Theme.spacing.x5)) {
            FaqRow(
                item = FaqItem(
                    id = "1",
                    question = "Funguje aplikace offline?",
                    answer = "Ano. Po stažení dat akce funguje trasa, kontroly i mapa bez signálu.",
                ),
                showDivider = false,
                initiallyExpanded = true,
            )
        }
    }
}
