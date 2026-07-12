package com.example.ta33.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Raw color tokens - 1:1 z TA33 Design System (`colors_and_type.css`).
 * Nepoužívej tyhle přímo v UI; ber je přes [Ta33Theme] / [Ta33Colors].
 */
internal object Ta33Palette {
    // Brand - orange (sandstone)
    val Orange50 = Color(0xFFFFF1E2)
    val Orange100 = Color(0xFFFCDEC1)
    val Orange200 = Color(0xFFFCC081)
    val Orange300 = Color(0xFFFBA452)
    val Orange400 = Color(0xFFF8852A)
    val Orange = Color(0xFFF76A0E) // PRIMARY
    val Orange600 = Color(0xFFC45607)
    val Orange700 = Color(0xFF974303)

    // Dark - slate
    val Slate900 = Color(0xFF15202B)
    val Slate800 = Color(0xFF1C2A36)
    val Slate700 = Color(0xFF2A3A48)
    val Slate600 = Color(0xFF3E5160)
    val Slate500 = Color(0xFF5A6C7A)
    val Slate400 = Color(0xFF8492A0)
    val Slate300 = Color(0xFFB6BFC8)
    val Slate200 = Color(0xFFD2D9DE)
    val Slate100 = Color(0xFFE6EAEC)
    val Slate50 = Color(0xFFF1F3F4)

    // Surfaces
    val Cream = Color(0xFFF7F2EA) // main app background
    val CreamDeep = Color(0xFFEFE7D7)
    val Paper = Color(0xFFFFFFFF) // card surface

    // Map
    val MapTile = Color(0xFFDEE7DC)
    val MapGrid = Color(0xFFC9D3C5)

    // Sky (login / splash)
    val SkyTop = Color(0xFF4F87A4)
    val SkyBottom = Color(0xFF79A4BC)

    // Semantic
    val Success = Color(0xFF1FA85A)
    val SuccessTint = Color(0xFFD9F3E3)
    val Warning = Color(0xFFE8A92A)
    val WarningTint = Color(0xFFFBE9C2)
    val Error = Color(0xFFD63A2F)
    val ErrorTint = Color(0xFFF8D9D5)
    val Info = Color(0xFF2E6FB5)
    val InfoTint = Color(0xFFD6E5F4)

    val White = Color(0xFFFFFFFF)
}

/**
 * Sémantické barevné tokeny TA33. Dostupné v UI přes `Ta33Theme.colors`.
 * Doplňuje Material3 [androidx.compose.material3.ColorScheme] o věci, které
 * Material nemá (control-point stavy, tinty, map/sky, foreground role).
 */
data class Ta33Colors(
    // Foreground role
    val fgStrong: Color,
    val fgDefault: Color,
    val fgMuted: Color,
    val fgFaint: Color,
    val fgOnDark: Color,
    val fgOnDarkMuted: Color,
    val fgOnOrange: Color,
    val fgLink: Color,
    // Semantic
    val success: Color,
    val successTint: Color,
    val warning: Color,
    val warningTint: Color,
    val error: Color,
    val errorTint: Color,
    val info: Color,
    val infoTint: Color,
    // Control-point states (atomic concept of the app)
    val kpLockedBg: Color,
    val kpLockedFg: Color,
    val kpActiveBg: Color,
    val kpActiveFg: Color,
    val kpDoneBg: Color,
    val kpDoneFg: Color,
    val kpFinishBg: Color,
    val kpFinishFg: Color,
    // Surfaces / map / sky
    val identityBg: Color, // tmavá "kdo a kdy" karta (date/place, startovní číslo)
    val scanBg: Color,     // celoobrazovkové pozadí scan modalu (slate-900)
    val creamDeep: Color,
    val mapTile: Color,
    val mapGrid: Color,
    val skyTop: Color,
    val skyBottom: Color,
)

internal val Ta33LightColors = Ta33Colors(
    fgStrong = Ta33Palette.Slate900,
    fgDefault = Ta33Palette.Slate800,
    fgMuted = Ta33Palette.Slate500,
    fgFaint = Ta33Palette.Slate400,
    fgOnDark = Ta33Palette.Cream,
    fgOnDarkMuted = Ta33Palette.Slate300,
    fgOnOrange = Ta33Palette.White,
    fgLink = Ta33Palette.Orange700,
    success = Ta33Palette.Success,
    successTint = Ta33Palette.SuccessTint,
    warning = Ta33Palette.Warning,
    warningTint = Ta33Palette.WarningTint,
    error = Ta33Palette.Error,
    errorTint = Ta33Palette.ErrorTint,
    info = Ta33Palette.Info,
    infoTint = Ta33Palette.InfoTint,
    kpLockedBg = Ta33Palette.Slate200,
    kpLockedFg = Ta33Palette.Slate500,
    kpActiveBg = Ta33Palette.Orange,
    kpActiveFg = Ta33Palette.White,
    kpDoneBg = Ta33Palette.Success,
    kpDoneFg = Ta33Palette.White,
    kpFinishBg = Ta33Palette.Slate800,
    kpFinishFg = Ta33Palette.White,
    identityBg = Ta33Palette.Slate800,
    scanBg = Ta33Palette.Slate900,
    creamDeep = Ta33Palette.CreamDeep,
    mapTile = Ta33Palette.MapTile,
    mapGrid = Ta33Palette.MapGrid,
    skyTop = Ta33Palette.SkyTop,
    skyBottom = Ta33Palette.SkyBottom,
)
