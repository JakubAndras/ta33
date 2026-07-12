package com.example.ta33.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Typografie z TA33 Design System.
 *
 * TODO(fonty): design používá **Big Shoulders Display** (display/button) a **Inter** (UI/body).
 * Zatím se používá systémový [FontFamily.Default] jako fallback - až se přidají TTF do
 * `androidApp/src/main/res/font/`, stačí nahradit tyhle dvě konstanty a zbytek škály zůstane.
 */
object Ta33Type {
    val Display: FontFamily = FontFamily.Default // ← Big Shoulders Display
    val Body: FontFamily = FontFamily.Default // ← Inter

    // Display (chunky, uppercase) - nadpisové numerické prvky
    val display1 = TextStyle(
        fontFamily = Display, fontSize = 40.sp, lineHeight = 40.sp,
        fontWeight = FontWeight.Black, letterSpacing = 0.01.em,
    )
    val display2 = TextStyle(
        fontFamily = Display, fontSize = 32.sp, lineHeight = 34.sp,
        fontWeight = FontWeight.Black, letterSpacing = 0.01.em,
    )
    val display3 = TextStyle(
        fontFamily = Display, fontSize = 22.sp, lineHeight = 24.sp,
        fontWeight = FontWeight.ExtraBold,
    )

    // Headings (Inter)
    val h1 = TextStyle(fontFamily = Body, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
    val h2 = TextStyle(fontFamily = Body, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
    val h3 = TextStyle(fontFamily = Body, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)

    // Body / supporting
    val body = TextStyle(fontFamily = Body, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)
    val bodyStrong = TextStyle(fontFamily = Body, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val small = TextStyle(fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val caption = TextStyle(fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
    val overline = TextStyle(
        fontFamily = Body, fontSize = 13.sp, lineHeight = 16.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 0.10.em,
    )
    val button = TextStyle(
        fontFamily = Display, fontSize = 16.sp, lineHeight = 20.sp,
        fontWeight = FontWeight.ExtraBold, letterSpacing = 0.04.em,
    )
}
