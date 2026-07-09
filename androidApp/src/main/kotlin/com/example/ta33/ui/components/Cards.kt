package com.example.ta33.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.example.ta33.ui.theme.Ta33Theme

/**
 * Slate-800 „kdo a kdy" karta: datum (overline) · místo (velké UPPER) · podtitul.
 */
@Composable
fun IdentityCard(
    date: String,
    place: String,
    sub: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(Ta33Theme.spacing.x2, Ta33Theme.radius.lg),
        color = Ta33Theme.colors.identityBg,
        shape = Ta33Theme.radius.lg,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = Ta33Theme.spacing.x5,
                vertical = Ta33Theme.spacing.x6,
            ),
            verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x2),
        ) {
            Text(
                text = date.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Ta33Theme.colors.fgOnDarkMuted,
            )
            Text(
                text = place.uppercase(),
                style = MaterialTheme.typography.displaySmall,
                color = Ta33Theme.colors.fgOnDark,
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.bodyMedium,
                color = Ta33Theme.colors.fgOnDarkMuted,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Bílá „paper" karta, radius lg, jemný stín. Kontejner pro operační obsah.
 */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(Ta33Theme.spacing.x1, Ta33Theme.radius.lg),
        color = MaterialTheme.colorScheme.surface,
        shape = Ta33Theme.radius.lg,
    ) {
        Column(modifier = Modifier.padding(Ta33Theme.spacing.x4)) {
            content()
        }
    }
}

@Preview
@Composable
private fun IdentityCardPreview() {
    Ta33Theme {
        IdentityCard(
            date = "Sobota 19. 9. 2026",
            place = "Teplice n. Metují",
            sub = "Start 7:00–10:00 · prezentace u sokolovny",
            modifier = Modifier.padding(Ta33Theme.spacing.x5),
        )
    }
}

@Preview
@Composable
private fun PaperCardPreview() {
    Ta33Theme {
        PaperCard(modifier = Modifier.padding(Ta33Theme.spacing.x5)) {
            Text("Obsah karty", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
