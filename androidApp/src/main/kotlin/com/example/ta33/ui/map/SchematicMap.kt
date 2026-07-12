package com.example.ta33.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ta33.ui.theme.Ta33Theme
import kotlin.math.min

/**
 * Schematic route map (`SchematicMap`): a stylised stand-in for the real map — parchment→sage
 * gradient base, faint contour lines + forest/water blobs, the bold route loop (white underlay +
 * route colour, dashed for TA50), a start/cíl marker and clickable control pins (rotated teardrop
 * with the control number). Pins cross-highlight with the itinerary via [highlightedControl].
 *
 * Geometry (loop path, contours, pin coordinates) is ported 1:1 from the design's 360×460 viewport
 * as structural literals. Colours use theme tokens; blobs/marker use map/foreground tokens.
 *
 * NOTE: This is the schematic design map. A real MapLibre map + live GPS (FR-06) is a follow-up.
 */
@Composable
fun SchematicMap(
    controlsCount: Int,
    routeColor: Color,
    dashed: Boolean,
    highlightedControl: Int?,
    onPinClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Ta33Theme.colors
    val pins = if (controlsCount >= 6) PINS_6 else PINS_5

    val gradient = Brush.linearGradient(listOf(colors.creamDeep, colors.mapTile))
    BoxWithConstraints(modifier = modifier.background(gradient)) {
        val boxW = maxWidth
        val boxH = maxHeight

        Canvas(modifier = Modifier.fillMaxSize()) {
            val sx = size.width / VIEW_W
            val sy = size.height / VIEW_H
            val s = min(sx, sy)
            val map = MapPath(sx, sy)

            val contourStroke = Stroke(width = 1.2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)

            // faint contour lines
            CONTOURS.forEach { spec ->
                drawPath(map.build(spec), color = colors.mapGrid, alpha = 0.7f, style = contourStroke)
            }
            // forest + water blobs
            FOREST_BLOBS.forEach { spec ->
                drawPath(map.build(spec), color = colors.mapGrid, alpha = 0.5f)
            }
            drawPath(map.build(WATER_BLOB), color = colors.infoTint, alpha = 0.5f)

            // route loop: white underlay + route colour (dashed for TA50)
            val loop = map.build(LOOP_PATH)
            drawPath(
                loop,
                color = colors.fgOnOrange,
                alpha = 0.85f,
                style = Stroke(width = 9f * s, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawPath(
                loop,
                color = routeColor,
                style = Stroke(
                    width = 5f * s, cap = StrokeCap.Round, join = StrokeJoin.Round,
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(14f * s, 8f * s)) else null,
                ),
            )

            // start / cíl marker (bottom-centre of the loop)
            val marker = Offset(MARKER_X * sx, MARKER_Y * sy)
            drawCircle(color = colors.fgOnOrange, radius = 9f * s, center = marker)
            drawCircle(color = colors.identityBg, radius = 9f * s, center = marker, style = Stroke(width = 3f * s))
            drawCircle(color = colors.identityBg, radius = 3.4f * s, center = marker)
        }

        // clickable control pins
        pins.forEach { pin ->
            val cx = boxW * (pin.x / VIEW_W)
            val cy = boxH * (pin.y / VIEW_H)
            ControlPin(
                number = pin.n,
                active = highlightedControl == pin.n,
                modifier = Modifier.offset(x = cx - PIN_SIZE / 2, y = cy - PIN_SIZE),
                onClick = { onPinClick(pin.n) },
            )
        }
    }
}

private val PIN_SIZE = 30.dp

@Composable
private fun ControlPin(
    number: Int,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Ta33Theme.colors
    // teardrop: three round corners + a sharp bottom-left, rotated 45° so the point faces down.
    val shape = RoundedCornerShape(
        topStart = CornerSize(50),
        topEnd = CornerSize(50),
        bottomEnd = CornerSize(50),
        bottomStart = CornerSize(2.dp),
    )
    val scale = if (active) 1.18f else 1f
    Box(
        modifier = modifier
            .size(PIN_SIZE)
            .clickable(onClick = onClick)
            .graphicsLayer { rotationZ = 45f; scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (active) colors.identityBg else colors.kpActiveBg)
            .border(2.5.dp, colors.fgOnOrange, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            modifier = Modifier.graphicsLayer { rotationZ = -45f },
            style = Ta33Theme.typography.display3.copy(fontSize = 15.sp, fontWeight = FontWeight.Black),
            color = colors.fgOnOrange,
        )
    }
}

// ── Geometry (ported 1:1 from the design's 360×460 viewport) ─────────────────────────────────────

private const val VIEW_W = 360f
private const val VIEW_H = 460f
private const val MARKER_X = 196f
private const val MARKER_Y = 300f

private data class Pin(val x: Float, val y: Float, val n: Int)

private val PINS_5 = listOf(
    Pin(150f, 150f, 1), Pin(120f, 250f, 2), Pin(96f, 118f, 3), Pin(150f, 310f, 4), Pin(244f, 312f, 5),
)
private val PINS_6 = listOf(
    Pin(150f, 150f, 1), Pin(120f, 250f, 2), Pin(96f, 118f, 3),
    Pin(262f, 150f, 4), Pin(236f, 118f, 5), Pin(244f, 312f, 6),
)

/** Builds scaled [Path]s from compact command specs (M/C/Q/Z) in the 360×460 viewport. */
private class MapPath(private val sx: Float, private val sy: Float) {
    fun build(spec: List<Cmd>): Path = Path().apply {
        spec.forEach { c ->
            when (c) {
                is Cmd.M -> moveTo(c.x * sx, c.y * sy)
                is Cmd.C -> cubicTo(c.x1 * sx, c.y1 * sy, c.x2 * sx, c.y2 * sy, c.x * sx, c.y * sy)
                is Cmd.Q -> quadraticTo(c.x1 * sx, c.y1 * sy, c.x * sx, c.y * sy)
                Cmd.Z -> close()
            }
        }
    }
}

private sealed interface Cmd {
    data class M(val x: Float, val y: Float) : Cmd
    data class C(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float) : Cmd
    data class Q(val x1: Float, val y1: Float, val x: Float, val y: Float) : Cmd
    data object Z : Cmd
}

private val LOOP_PATH = listOf(
    Cmd.M(196f, 300f),
    Cmd.C(150f, 250f, 120f, 200f, 150f, 150f),
    Cmd.C(175f, 110f, 120f, 96f, 96f, 118f),
    Cmd.C(70f, 142f, 96f, 176f, 132f, 176f),
    Cmd.C(176f, 176f, 150f, 230f, 120f, 250f),
    Cmd.C(96f, 268f, 120f, 300f, 150f, 310f),
    Cmd.C(130f, 340f, 160f, 372f, 196f, 356f),
    Cmd.C(232f, 372f, 262f, 340f, 244f, 312f),
    Cmd.C(288f, 300f, 300f, 250f, 270f, 224f),
    Cmd.C(306f, 196f, 300f, 150f, 262f, 150f),
    Cmd.C(300f, 120f, 262f, 96f, 236f, 118f),
    Cmd.C(262f, 156f, 226f, 176f, 210f, 210f),
    Cmd.C(244f, 240f, 232f, 280f, 196f, 300f),
    Cmd.Z,
)

private val CONTOURS = listOf(
    listOf(Cmd.M(-20f, 90f), Cmd.C(60f, 60f, 120f, 120f, 200f, 96f), Cmd.C(280f, 72f, 340f, 120f, 400f, 96f)),
    listOf(Cmd.M(-20f, 150f), Cmd.C(70f, 120f, 140f, 180f, 210f, 150f), Cmd.C(290f, 120f, 340f, 170f, 400f, 150f)),
    listOf(Cmd.M(-20f, 220f), Cmd.C(60f, 190f, 130f, 250f, 220f, 220f), Cmd.C(300f, 195f, 350f, 240f, 400f, 220f)),
    listOf(Cmd.M(-20f, 300f), Cmd.C(80f, 275f, 150f, 330f, 230f, 300f), Cmd.C(310f, 275f, 360f, 320f, 400f, 300f)),
    listOf(Cmd.M(-20f, 380f), Cmd.C(70f, 355f, 140f, 410f, 220f, 380f), Cmd.C(300f, 355f, 360f, 400f, 400f, 380f)),
)

private val FOREST_BLOBS = listOf(
    listOf(Cmd.M(40f, 60f), Cmd.Q(70f, 40f, 100f, 62f), Cmd.Q(120f, 90f, 90f, 108f), Cmd.Q(55f, 112f, 40f, 90f), Cmd.Z),
    listOf(
        Cmd.M(285f, 340f), Cmd.Q(320f, 330f, 330f, 360f), Cmd.Q(322f, 392f, 292f, 388f),
        Cmd.Q(272f, 366f, 285f, 340f), Cmd.Z,
    ),
)
private val WATER_BLOB = listOf(
    Cmd.M(300f, 70f), Cmd.Q(330f, 74f, 328f, 100f), Cmd.Q(312f, 118f, 292f, 106f), Cmd.Q(286f, 82f, 300f, 70f), Cmd.Z,
)

@Preview(widthDp = 360, heightDp = 452)
@Composable
private fun SchematicMapPreview() {
    Ta33Theme {
        SchematicMap(
            controlsCount = 5,
            routeColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            dashed = false,
            highlightedControl = 2,
            onPinClick = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
