package com.example.ta33.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ta33.ui.theme.Ta33Theme

/**
 * Primární orange pill CTA - full-width, min-height 56, button typo (UPPER),
 * jemný orange glow (aproximace `shadow-cta-glow`).
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Ta33Theme.spacing.x9)
            .shadow(
                elevation = Ta33Theme.spacing.x2,
                shape = Ta33Theme.radius.pill,
                ambientColor = MaterialTheme.colorScheme.primary,
                spotColor = MaterialTheme.colorScheme.primary,
            ),
        shape = Ta33Theme.radius.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(text = text.uppercase(), style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Sekundární akce - orange 2px border, transparentní fill, orange text.
 */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Ta33Theme.spacing.x9),
        shape = Ta33Theme.radius.pill,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Text(text = text.uppercase(), style = MaterialTheme.typography.labelLarge)
    }
}

@Preview
@Composable
private fun ButtonsPreview() {
    Ta33Theme {
        PrimaryButton(text = "Stáhnout data akce · 84 MB", onClick = {})
    }
}

@Preview
@Composable
private fun OutlineButtonPreview() {
    Ta33Theme {
        OutlineButton(text = "Stáhnout dlaždice", onClick = {})
    }
}
