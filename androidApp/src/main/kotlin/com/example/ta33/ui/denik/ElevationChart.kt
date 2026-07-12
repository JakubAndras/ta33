package com.example.ta33.ui.denik

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.example.ta33.domain.model.ElevationProfile
import com.example.ta33.ui.theme.Ta33Theme

/**
 * Elevation profile chart (`ElevProfile`): filled area + line in the route colour, low/peak
 * callouts (leader + ring + "462 / 727" + "m n. m.") and a km axis with gridlines. Drawn on a
 * [Canvas] scaled from the design's 324×172 viewport so proportions match the mockup — including
 * the text, whose size tracks the viewport (fixed sp would over-scale and clip the top callout).
 */
@Composable
fun ElevationChart(
    profile: ElevationProfile,
    routeColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = Ta33Theme.colors
    val measurer = rememberTextMeasurer()

    // DrawScope is not composable — capture every colour/style up front. Font sizes are applied
    // inside the Canvas so they scale with the viewport height.
    val gridColor = MaterialTheme.colorScheme.surfaceVariant // slate-100 divider
    val leaderColor = MaterialTheme.colorScheme.outline       // slate-300
    val paperColor = MaterialTheme.colorScheme.surface        // ring inner fill

    val numBase = Ta33Theme.typography.display3.copy(fontWeight = FontWeight.Black, color = colors.fgStrong)
    val unitBase = Ta33Theme.typography.caption.copy(color = colors.fgMuted)
    val axisBase = Ta33Theme.typography.display3.copy(fontWeight = FontWeight.Black, color = colors.fgFaint)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(324f / 172f),
    ) {
        val ux = size.width / 324f
        val uy = size.height / 172f
        fun px(x: Float) = x * ux
        fun py(y: Float) = y * uy

        val pts = profile.pointsNormalized.map { it.toFloat() }
        val n = pts.size
        if (n < 2) return@Canvas

        val numStyle = numBase.copy(fontSize = (15f * uy).toSp())
        val unitStyle = unitBase.copy(fontSize = (8f * uy).toSp())
        val axisStyle = axisBase.copy(fontSize = (12f * uy).toSp())

        val x0 = 12f
        val x1 = 312f
        val top = 66f
        val bot = 130f
        val base = 142f
        fun vx(i: Int) = x0 + i * ((x1 - x0) / (n - 1))
        fun vy(v: Float) = bot - v * (bot - top)

        val kmTotal = profile.kmTotal.toFloat().coerceAtLeast(0.0001f)
        fun tx(km: Int) = x0 + (km / kmTotal) * (x1 - x0)

        // gridlines at km ticks
        profile.tickKm.forEach { km ->
            drawLine(
                color = gridColor,
                start = Offset(px(tx(km)), py(54f)),
                end = Offset(px(tx(km)), py(base)),
                strokeWidth = ux,
            )
        }

        // area fill
        val area = Path().apply {
            moveTo(px(x0), py(base))
            pts.forEachIndexed { i, v -> lineTo(px(vx(i)), py(vy(v))) }
            lineTo(px(x1), py(base))
            close()
        }
        drawPath(area, color = routeColor, alpha = 0.12f)

        // line
        val line = Path().apply {
            pts.forEachIndexed { i, v ->
                if (i == 0) moveTo(px(vx(i)), py(vy(v))) else lineTo(px(vx(i)), py(vy(v)))
            }
        }
        drawPath(
            line,
            color = routeColor,
            style = Stroke(width = 2.4f * ux, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // callouts: low at index 0, high at the peak index (mirrors the design's ElevProfile)
        val peakIdx = pts.indices.maxByOrNull { pts[it] } ?: 0
        drawCallout(measurer, px(vx(0)), py(vy(pts[0])), py(34f), profile.lowMeters, routeColor, leaderColor, paperColor, numStyle, unitStyle, ux)
        drawCallout(measurer, px(vx(peakIdx)), py(vy(pts[peakIdx])), py(20f), profile.highMeters, routeColor, leaderColor, paperColor, numStyle, unitStyle, ux)

        // km axis labels
        profile.tickKm.forEach { km ->
            val laid = measurer.measure("$km km", style = axisStyle)
            drawText(laid, topLeft = Offset(px(tx(km)) - laid.size.width / 2f, py(158f)))
        }
    }
}

private fun DrawScope.drawCallout(
    measurer: TextMeasurer,
    x: Float,
    yPoint: Float,
    yLabel: Float,
    meters: Int,
    routeColor: Color,
    leaderColor: Color,
    paperColor: Color,
    numStyle: TextStyle,
    unitStyle: TextStyle,
    unit: Float,
) {
    drawLine(
        color = leaderColor,
        start = Offset(x, yPoint),
        end = Offset(x, yLabel + 6f * unit),
        strokeWidth = unit,
    )
    drawCircle(color = paperColor, radius = 4f * unit, center = Offset(x, yPoint))
    drawCircle(
        color = routeColor,
        radius = 4f * unit,
        center = Offset(x, yPoint),
        style = Stroke(width = 2f * unit),
    )
    val num = measurer.measure(meters.toString(), style = numStyle.copy(textAlign = TextAlign.Center))
    drawText(num, topLeft = Offset(x - num.size.width / 2f, (yLabel - num.size.height).coerceAtLeast(0f)))
    val unitText = measurer.measure("m n. m.", style = unitStyle.copy(textAlign = TextAlign.Center))
    drawText(unitText, topLeft = Offset(x - unitText.size.width / 2f, yLabel))
}

@Preview
@Composable
private fun ElevationChartPreview() {
    Ta33Theme {
        ElevationChart(
            profile = ElevationProfile(
                pointsNormalized = listOf(0.05, 0.12, 0.35, 0.62, 0.9, 0.78, 0.55, 0.42, 0.6, 0.48, 0.3, 0.38, 0.22, 0.15, 0.08),
                lowMeters = 462, highMeters = 727, ascentMeters = 740, descentMeters = 740,
                kmTotal = 33.2, tickKm = listOf(8, 16, 24),
            ),
            routeColor = MaterialTheme.colorScheme.primary,
        )
    }
}
