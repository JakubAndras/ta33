package com.example.ta33.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ta33.presentation.navigation.TopLevelDestination
import com.example.ta33.ui.components.Ta33Icons
import com.example.ta33.ui.theme.Ta33Theme

/**
 * Plovoucí bílý pill bottom nav (Material-idiomatic, dle design systému):
 * Deník / Mapa / Přehled. Aktivní tab = slate-800 pill + světlý obsah,
 * neaktivní = jen ikona ve `fgMuted`. Sám si drží spodní okraj a safe-area inset;
 * volající zarovná horizontálně (BottomCenter).
 */
@Composable
fun Ta33BottomNav(
    selected: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = Ta33Theme.spacing.x4)
            .shadow(Ta33Theme.spacing.x2, Ta33Theme.radius.pill),
        color = MaterialTheme.colorScheme.surface,
        shape = Ta33Theme.radius.pill,
    ) {
        Row(
            modifier = Modifier.padding(Ta33Theme.spacing.x2),
            horizontalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(TopLevelDestination.DENIK, Ta33Icons.BookText, "Deník", selected, onSelect)
            NavItem(TopLevelDestination.MAPA, Ta33Icons.Map, "Mapa", selected, onSelect)
            NavItem(TopLevelDestination.PREHLED, Ta33Icons.User, "Přehled", selected, onSelect)
        }
    }
}

@Composable
private fun NavItem(
    destination: TopLevelDestination,
    icon: ImageVector,
    label: String,
    selected: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
) {
    val active = destination == selected
    Row(
        modifier = Modifier
            .clip(Ta33Theme.radius.pill)
            .clickable { onSelect(destination) }
            .background(if (active) Ta33Theme.colors.identityBg else Color.Transparent)
            .padding(horizontal = Ta33Theme.spacing.x4, vertical = Ta33Theme.spacing.x3),
        horizontalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) Ta33Theme.colors.fgOnDark else Ta33Theme.colors.fgMuted,
        )
        if (active) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Ta33Theme.colors.fgOnDark,
            )
        }
    }
}

/** Kruhový orange scan FAB s glow — vstupní bod pro QR sken (FR-09), jen při aktivním běhu. */
@Composable
fun ScanFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .size(60.dp)
            .shadow(
                elevation = Ta33Theme.spacing.x2,
                shape = CircleShape,
                ambientColor = MaterialTheme.colorScheme.primary,
                spotColor = MaterialTheme.colorScheme.primary,
            ),
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Icon(imageVector = Ta33Icons.Scan, contentDescription = "Skenovat")
    }
}

@Preview
@Composable
private fun Ta33BottomNavDenikPreview() {
    Ta33Theme { Ta33BottomNav(selected = TopLevelDestination.DENIK, onSelect = {}) }
}

@Preview
@Composable
private fun Ta33BottomNavMapaPreview() {
    Ta33Theme { Ta33BottomNav(selected = TopLevelDestination.MAPA, onSelect = {}) }
}

@Preview
@Composable
private fun Ta33BottomNavPrehledPreview() {
    Ta33Theme { Ta33BottomNav(selected = TopLevelDestination.PREHLED, onSelect = {}) }
}

@Preview
@Composable
private fun ScanFabPreview() {
    Ta33Theme { ScanFab(onClick = {}) }
}
