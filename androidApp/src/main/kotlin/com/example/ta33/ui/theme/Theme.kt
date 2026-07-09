package com.example.ta33.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

private val Ta33ColorScheme = lightColorScheme(
    primary = Ta33Palette.Orange,
    onPrimary = Ta33Palette.White,
    primaryContainer = Ta33Palette.Orange50,
    onPrimaryContainer = Ta33Palette.Orange700,
    secondary = Ta33Palette.Slate600,
    onSecondary = Ta33Palette.White,
    secondaryContainer = Ta33Palette.Slate100,
    onSecondaryContainer = Ta33Palette.Slate800,
    background = Ta33Palette.Cream,
    onBackground = Ta33Palette.Slate900,
    surface = Ta33Palette.Paper,
    onSurface = Ta33Palette.Slate800,
    surfaceVariant = Ta33Palette.Slate100,
    onSurfaceVariant = Ta33Palette.Slate500,
    error = Ta33Palette.Error,
    onError = Ta33Palette.White,
    errorContainer = Ta33Palette.ErrorTint,
    onErrorContainer = Ta33Palette.Error,
    outline = Ta33Palette.Slate300,
    outlineVariant = Ta33Palette.Slate200,
)

private val Ta33MaterialTypography = Typography(
    displayLarge = Ta33Type.display1,
    displayMedium = Ta33Type.display2,
    displaySmall = Ta33Type.display3,
    headlineSmall = Ta33Type.h1,
    titleLarge = Ta33Type.h2,
    titleMedium = Ta33Type.h3,
    bodyLarge = Ta33Type.body,
    bodyMedium = Ta33Type.small,
    labelLarge = Ta33Type.button,
    labelMedium = Ta33Type.caption,
    labelSmall = Ta33Type.overline,
)

private val Ta33Shapes = Shapes(
    extraSmall = Ta33Radius.xs,
    small = Ta33Radius.sm,
    medium = Ta33Radius.md,
    large = Ta33Radius.lg,
    extraLarge = Ta33Radius.xl,
)

private val LocalTa33Colors = staticCompositionLocalOf { Ta33LightColors }

/**
 * Kořenový theme aplikace. Obaluje Material3 (barvy/typo/shapes namapované z TA33
 * design tokenů) a navíc vystavuje TA33-specifické tokeny přes [Ta33Theme].
 */
@Composable
fun Ta33Theme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTa33Colors provides Ta33LightColors) {
        MaterialTheme(
            colorScheme = Ta33ColorScheme,
            typography = Ta33MaterialTypography,
            shapes = Ta33Shapes,
            content = content,
        )
    }
}

/** Přístup k TA33 tokenům z UI (obdoba `MaterialTheme`). */
object Ta33Theme {
    val colors: Ta33Colors
        @Composable @ReadOnlyComposable get() = LocalTa33Colors.current

    val typography: Ta33Type get() = Ta33Type

    val spacing: Ta33Spacing get() = Ta33Spacing

    val radius: Ta33Radius get() = Ta33Radius
}
