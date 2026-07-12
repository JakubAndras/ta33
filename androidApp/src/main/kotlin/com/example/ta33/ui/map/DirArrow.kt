package com.example.ta33.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ta33.domain.model.TurnDirection
import com.example.ta33.ui.theme.Ta33Theme

/**
 * Direction glyph (`DirArrow`) — a chunky arrow for a waypoint's turn direction, drawn on a [Canvas]
 * scaled from the design's 24×24 viewport (stroke 2.4, round caps/joins). Basic up/down/left/right
 * (and straight) are the up arrow rotated; `LEFT_UP` and `LEFT_RIGHT` are dedicated two-headed glyphs.
 */
@Composable
fun DirArrow(
    dir: TurnDirection,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = Ta33Theme.colors.fgStrong,
) {
    Canvas(modifier = modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val stroke = Stroke(width = 2.4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Path builder in the 24-unit viewport (scaled by u).
        fun path(build: PathScope.() -> Unit): Path = Path().apply { PathScope(this, u).build() }

        when (dir) {
            TurnDirection.LEFT_UP -> {
                drawPath(
                    path { move(4f, 12f); line(14f, 12f); move(8f, 8f); line(4f, 12f); line(8f, 16f) },
                    color, style = stroke,
                )
                drawPath(
                    path { move(18f, 20f); line(18f, 8f); move(14f, 12f); line(18f, 8f); line(22f, 12f) },
                    color, style = stroke,
                )
            }
            TurnDirection.LEFT_RIGHT -> {
                drawPath(
                    path {
                        move(3f, 12f); line(21f, 12f)
                        move(7f, 8f); line(3f, 12f); line(7f, 16f)
                        move(17f, 8f); line(21f, 12f); line(17f, 16f)
                    },
                    color, style = stroke,
                )
            }
            else -> {
                val rot = when (dir) {
                    TurnDirection.RIGHT -> 90f
                    TurnDirection.DOWN -> 180f
                    TurnDirection.LEFT -> 270f
                    else -> 0f // UP, STRAIGHT
                }
                rotate(rot) {
                    drawPath(
                        path { move(12f, 20f); line(12f, 4f); move(7f, 9f); line(12f, 4f); line(17f, 9f) },
                        color, style = stroke,
                    )
                }
            }
        }
    }
}

/** Tiny helper so arrow geometry reads like the design's SVG paths, scaled by [u]. */
private class PathScope(private val path: Path, private val u: Float) {
    fun move(x: Float, y: Float) = path.moveTo(x * u, y * u)
    fun line(x: Float, y: Float) = path.lineTo(x * u, y * u)
}

@Preview
@Composable
private fun DirArrowPreview() {
    Ta33Theme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.background(Ta33Theme.colors.creamDeep).padding(20.dp),
        ) {
            listOf(
                TurnDirection.UP, TurnDirection.RIGHT, TurnDirection.DOWN, TurnDirection.LEFT,
                TurnDirection.LEFT_UP, TurnDirection.LEFT_RIGHT, TurnDirection.STRAIGHT,
            ).forEach { DirArrow(dir = it, size = 24.dp) }
        }
    }
}
