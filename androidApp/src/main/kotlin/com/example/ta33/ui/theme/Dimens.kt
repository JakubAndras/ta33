package com.example.ta33.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Spacing scale - 4px base (TA33 Design System). */
object Ta33Spacing {
    val none = 0.dp
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp // default card padding edge
    val x5 = 20.dp
    val x6 = 24.dp // card interior padding
    val x7 = 32.dp
    val x8 = 40.dp
    val x9 = 56.dp
    val x10 = 72.dp
}

/** Corner radii - bouldery, friendly (TA33 Design System). */
object Ta33Radius {
    val xs = RoundedCornerShape(6.dp)
    val sm = RoundedCornerShape(10.dp)
    val md = RoundedCornerShape(14.dp) // small chips, stat blocks
    val lg = RoundedCornerShape(20.dp) // default card
    val xl = RoundedCornerShape(24.dp) // primary surfaces
    val xxl = RoundedCornerShape(28.dp) // bottom sheet
    val pill = RoundedCornerShape(999.dp) // CTA, nav, chips
}
