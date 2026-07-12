package com.example.ta33.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.example.ta33.ui.theme.Ta33Theme

/**
 * Mock „odbavovací QR" — deterministický 21×21 pseudo-vzor (jako design `QRGlyph`).
 * NENÍ skenovatelný; jen vizuální placeholder do doby, než přijde reálné generování QR
 * (Etapa 2, FR-13). Barvy se berou z tokenů — [cellColor] tmavé buňky na [background].
 */
@Composable
fun QrGlyph(
    modifier: Modifier = Modifier,
    cellColor: Color = Ta33Theme.colors.scanBg,
    background: Color = MaterialTheme.colorScheme.surface,
) {
    Canvas(modifier = modifier) {
        drawRect(color = background)
        val cell = size.minDimension / QR_GRID
        for (y in 0 until QR_GRID) {
            for (x in 0 until QR_GRID) {
                if (qrCellFilled(x, y)) {
                    drawRect(
                        color = cellColor,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell, cell),
                    )
                }
            }
        }
    }
}

private const val QR_GRID = 21

/**
 * Reprodukce vzoru z design `QRGlyph`: tři „finder" rohy (levý-horní, pravý-horní, levý-dolní)
 * a jinak deterministický pseudo-šum. Čistá funkce → stabilní, testovatelný vzor.
 */
private fun qrCellFilled(x: Int, y: Int): Boolean {
    val n = QR_GRID
    val corner = (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7)
    return if (corner) {
        val cornerInner =
            (x in 1..5 && y in 1..5) ||
                (x in (n - 6)..(n - 2) && y in 1..5) ||
                (x in 1..5 && y in (n - 6)..(n - 2))
        val cornerCenter =
            (x in 2..4 && y in 2..4) ||
                (x in (n - 5)..(n - 3) && y in 2..4) ||
                (x in 2..4 && y in (n - 5)..(n - 3))
        !cornerInner || cornerCenter
    } else {
        (x * 7 + y * 13 + x * y * 3) % 3 < 1
    }
}

@Preview
@Composable
private fun QrGlyphPreview() {
    Ta33Theme {
        QrGlyph(
            modifier = Modifier
                .padding(Ta33Theme.spacing.x5)
                .size(168.dp),
        )
    }
}
