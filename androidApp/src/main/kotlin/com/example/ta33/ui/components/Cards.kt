package com.example.ta33.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ta33.ui.theme.Ta33Palette
import com.example.ta33.ui.theme.Ta33Theme

/** Nepravidelné pískovcové věže (Adršpašsko-teplické skály): (šířkový poměr, výškový poměr 0..1). */
private val ROCK_TOWERS = listOf(
    1.0f to 0.42f, 0.65f to 0.68f, 1.35f to 0.52f, 0.55f to 0.9f, 0.9f to 0.64f,
    1.15f to 0.46f, 0.5f to 0.82f, 1.45f to 0.6f, 0.8f to 1.0f, 0.6f to 0.55f,
    1.2f to 0.74f, 0.7f to 0.48f, 1.0f to 0.86f, 0.85f to 0.58f, 1.3f to 0.7f,
)

/**
 * TA33 event hero karta - brandová hlavička Teplicko-adršpašského pochodu: datum + odznak „33 KM",
 * wordmark „TA33" (33 oranžově), název akce, místo + start, a silueta adršpašských skalních věží
 * s jemným oranžovým „východem slunce". Slate identita + oranžový akcent. Zrcadlí iOS `IdentityCard`.
 */
@Composable
fun IdentityCard(
    date: String,
    place: String,
    sub: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(Ta33Theme.spacing.x2, Ta33Theme.radius.xl)
            .clip(Ta33Theme.radius.xl)
            .background(
                Brush.linearGradient(listOf(Ta33Palette.Slate900, Ta33Palette.Slate800)),
            ),
    ) {
        // Skalní silueta + dawn glow podél spodní hrany.
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Ta33Palette.Orange.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(w / 2f, h),
                    radius = w * 0.7f,
                ),
                blendMode = BlendMode.Plus,
            )
            val bandH = 72.dp.toPx()
            val gap = 2.5.dp.toPx()
            val cap = 5.dp.toPx()
            val totalWeight = ROCK_TOWERS.fold(0f) { acc, t -> acc + t.first }
            val usableW = w - gap * (ROCK_TOWERS.size - 1)
            val scale = usableW / totalWeight
            val path = Path().apply {
                moveTo(0f, h)
                var x = 0f
                ROCK_TOWERS.forEachIndexed { i, (wt, ht) ->
                    val tw = wt * scale
                    val topY = h - bandH * ht
                    val c = minOf(tw * 0.5f, cap)
                    lineTo(x, topY + c)
                    quadraticBezierTo(x, topY, x + c, topY)
                    lineTo(x + tw - c, topY)
                    quadraticBezierTo(x + tw, topY, x + tw, topY + c)
                    lineTo(x + tw, h)
                    x += tw
                    if (i < ROCK_TOWERS.size - 1) {
                        lineTo(x + gap, h)
                        x += gap
                    }
                }
                lineTo(w, h)
                close()
            }
            drawPath(path, color = Ta33Palette.Slate700)
        }

        Column(
            modifier = Modifier.padding(
                start = Ta33Theme.spacing.x6,
                top = Ta33Theme.spacing.x6,
                end = Ta33Theme.spacing.x6,
                bottom = Ta33Theme.spacing.x9, // místo pro horizont skalních věží
            ),
            verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x2),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = date.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Ta33Theme.colors.fgOnDarkMuted,
                )
                Text(
                    text = "33 KM",
                    style = MaterialTheme.typography.labelMedium,
                    color = Ta33Theme.colors.fgOnOrange,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Ta33Palette.Orange)
                        .padding(horizontal = Ta33Theme.spacing.x3, vertical = Ta33Theme.spacing.x1),
                )
            }
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Ta33Theme.colors.fgOnDark)) { append("TA") }
                    withStyle(SpanStyle(color = Ta33Palette.Orange)) { append("33") }
                },
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                text = "Teplicko-adršpašský pochod".uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Ta33Theme.colors.fgOnDarkMuted,
            )
            Column(verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x1)) {
                Text(
                    text = place,
                    style = MaterialTheme.typography.titleMedium,
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
