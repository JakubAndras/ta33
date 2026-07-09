package com.example.ta33.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Minimalistické Lucide-style ikony (2px stroke, rounded joins, 24px grid, `currentColor`).
 * Definované ručně jako [ImageVector], protože `material-icons-*` není v závislostech projektu.
 * Barvu řeší `Icon(tint = …)` (ColorFilter přebije zdrojovou barvu vektoru).
 */
object Ta33Icons {

    val Check: ImageVector = strokeIcon("check") {
        moveTo(20f, 6f)
        lineTo(9f, 17f)
        lineTo(4f, 12f)
    }

    val Download: ImageVector = strokeIcon("download") {
        // šipka dolů
        moveTo(12f, 4f)
        lineTo(12f, 15f)
        // hrot
        moveTo(7f, 10f)
        lineTo(12f, 15f)
        lineTo(17f, 10f)
        // vanička
        moveTo(4f, 16f)
        lineTo(4f, 20f)
        lineTo(20f, 20f)
        lineTo(20f, 16f)
    }

    val Star: ImageVector = fillIcon("star") {
        moveTo(12f, 2f)
        lineToRelative(3.09f, 6.26f)
        lineTo(22f, 9.27f)
        lineToRelative(-5f, 4.87f)
        lineToRelative(1.18f, 6.88f)
        lineTo(12f, 17.77f)
        lineToRelative(-6.18f, 3.25f)
        lineTo(7f, 14.14f)
        lineTo(2f, 9.27f)
        lineToRelative(6.91f, -1.01f)
        close()
    }

    val Zap: ImageVector = fillIcon("zap") {
        moveTo(13f, 2f)
        lineTo(4f, 14f)
        lineTo(11f, 14f)
        lineTo(10f, 22f)
        lineTo(20f, 10f)
        lineTo(13f, 10f)
        close()
    }
}

private fun strokeIcon(name: String, pathData: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathData,
        )
    }.build()

private fun fillIcon(name: String, pathData: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathBuilder = pathData,
        )
    }.build()
