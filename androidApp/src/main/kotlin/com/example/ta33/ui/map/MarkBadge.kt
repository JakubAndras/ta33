package com.example.ta33.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ta33.domain.model.TrailMark
import com.example.ta33.ui.theme.Ta33Theme

/**
 * KČT trail-marking badge (`MarkBadge`): the four KČT turistické značky are a white square with a
 * coloured horizontal band; `VLASTNI` is a solid TA33-orange diamond; `CYKLO` is a small yellow
 * plate with the route number.
 *
 * Colours use theme tokens where a matching one exists (modrá → info, červená → error,
 * vlastní → primary, square border → outline/slate-300). The KČT green/yellow and the cyclo plate
 * are official trail-marking colours with no design token, so they are structural literals (like the
 * map/marker geometry).
 */
@Composable
fun MarkBadge(
    mark: TrailMark,
    markNumber: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    when (mark) {
        TrailMark.CYKLO -> CykloPlate(number = markNumber, size = size, modifier = modifier)
        TrailMark.VLASTNI -> OwnDiamond(size = size, modifier = modifier)
        else -> KctSquare(band = kctBandColor(mark), size = size, modifier = modifier)
    }
}

@Composable
private fun KctSquare(band: Color, size: Dp, modifier: Modifier) {
    val shape = RoundedCornerShape(4.dp)
    Column(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Ta33Theme.colors.fgOnOrange) // white square
            .border(1.5.dp, MaterialTheme.colorScheme.outline, shape), // slate-300
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth())
        Box(modifier = Modifier.height(size * 0.38f).fillMaxWidth().background(band)) // 38% central band (design)
        Box(modifier = Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun OwnDiamond(size: Dp, modifier: Modifier) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size * 0.62f)
                .rotate(45f)
                .clip(RoundedCornerShape(3.dp))
                .background(Ta33Theme.colors.kpActiveBg), // TA33 orange
        )
    }
}

@Composable
private fun CykloPlate(number: String?, size: Dp, modifier: Modifier) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = size)
            .height(size)
            .clip(RoundedCornerShape(6.dp))
            .background(CykloFill)
            .border(1.5.dp, CykloBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.orEmpty(),
            style = Ta33Theme.typography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold),
            color = CykloText,
        )
    }
}

/** KČT band colour: modrá → info, červená → error (tokens); zelená/žlutá are trail-marking literals. */
@Composable
private fun kctBandColor(mark: TrailMark): Color = when (mark) {
    TrailMark.MODRA -> Ta33Theme.colors.info
    TrailMark.CERVENA -> Ta33Theme.colors.error
    TrailMark.ZELENA -> KctGreen
    TrailMark.ZLUTA -> KctYellow
    else -> Ta33Theme.colors.info
}

// KČT official trail-marking colours (no design token) + cyclo plate - structural literals.
private val KctGreen = Color(0xFF2E9E4F)
private val KctYellow = Color(0xFFF1C40F)
private val CykloFill = Color(0xFFF1C40F)
private val CykloBorder = Color(0xFFC99E06)
private val CykloText = Color(0xFF3A2E00)

@Preview
@Composable
private fun MarkBadgePreview() {
    Ta33Theme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.background(Ta33Theme.colors.creamDeep).padding(20.dp),
        ) {
            MarkBadge(TrailMark.MODRA, null)
            MarkBadge(TrailMark.ZELENA, null)
            MarkBadge(TrailMark.ZLUTA, null)
            MarkBadge(TrailMark.CERVENA, null)
            MarkBadge(TrailMark.VLASTNI, null)
            MarkBadge(TrailMark.CYKLO, "4036")
            MarkBadge(TrailMark.CYKLO, null)
        }
    }
}
