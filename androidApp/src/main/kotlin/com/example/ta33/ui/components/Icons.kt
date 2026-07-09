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

    val BookText: ImageVector = strokeIcon("book-text") {
        // obálka knihy
        moveTo(5f, 4f)
        lineTo(19f, 4f)
        lineTo(19f, 20f)
        lineTo(5f, 20f)
        close()
        // řádky textu
        moveTo(8f, 8f)
        lineTo(16f, 8f)
        moveTo(8f, 12f)
        lineTo(16f, 12f)
        moveTo(8f, 16f)
        lineTo(13f, 16f)
    }

    val Map: ImageVector = strokeIcon("map") {
        // složená mapa
        moveTo(3f, 6f)
        lineTo(9f, 3f)
        lineTo(15f, 6f)
        lineTo(21f, 3f)
        lineTo(21f, 18f)
        lineTo(15f, 21f)
        lineTo(9f, 18f)
        lineTo(3f, 21f)
        close()
        // přehyby
        moveTo(9f, 3f)
        lineTo(9f, 18f)
        moveTo(15f, 6f)
        lineTo(15f, 21f)
    }

    val User: ImageVector = strokeIcon("user") {
        // hlava (kruh složený ze dvou půloblouků)
        moveTo(16f, 8f)
        arcTo(4f, 4f, 0f, false, true, 8f, 8f)
        arcTo(4f, 4f, 0f, false, true, 16f, 8f)
        close()
        // ramena
        moveTo(5f, 20f)
        arcTo(7f, 7f, 0f, false, true, 19f, 20f)
    }

    val Scan: ImageVector = strokeIcon("scan") {
        // levý horní roh
        moveTo(4f, 8f)
        lineTo(4f, 4f)
        lineTo(8f, 4f)
        // pravý horní roh
        moveTo(16f, 4f)
        lineTo(20f, 4f)
        lineTo(20f, 8f)
        // pravý dolní roh
        moveTo(20f, 16f)
        lineTo(20f, 20f)
        lineTo(16f, 20f)
        // levý dolní roh
        moveTo(8f, 20f)
        lineTo(4f, 20f)
        lineTo(4f, 16f)
    }

    val ChevronRight: ImageVector = strokeIcon("chevron-right") {
        moveTo(9f, 6f)
        lineTo(15f, 12f)
        lineTo(9f, 18f)
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
