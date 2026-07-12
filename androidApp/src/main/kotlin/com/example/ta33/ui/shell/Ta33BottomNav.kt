package com.example.ta33.ui.shell

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ta33.presentation.navigation.TopLevelDestination
import com.example.ta33.ui.components.Ta33Icons
import com.example.ta33.ui.theme.Ta33Theme

/**
 * Android-native spodní lišta: standardní Material 3 [NavigationBar] (docked, edge-to-edge),
 * Deník / Mapa / Profil. Vizuál se drží platformy (Material), brand řeší jen tint výběru:
 * aktivní ikona/štítek = brand orange, pilulka indikátoru = orange tint (`primaryContainer`),
 * neaktivní = `fgMuted`. Safe-area inset (gesture bar) si `NavigationBar` řeší sám.
 *
 * (iOS strana používá nativní `TabView` s Liquid Glass - sdílíme model, ne vzhled.)
 */
@Composable
fun Ta33BottomNav(
    selected: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        NavItem(TopLevelDestination.DENIK, Ta33Icons.BookText, "Deník", selected, onSelect)
        NavItem(TopLevelDestination.MAPA, Ta33Icons.Map, "Mapa", selected, onSelect)
        NavItem(TopLevelDestination.PREHLED, Ta33Icons.User, "Profil", selected, onSelect)
    }
}

@Composable
private fun RowScope.NavItem(
    destination: TopLevelDestination,
    icon: ImageVector,
    label: String,
    selected: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationBarItem(
        selected = destination == selected,
        onClick = { onSelect(destination) },
        icon = { Icon(imageVector = icon, contentDescription = label) },
        label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = Ta33Theme.colors.fgMuted,
            unselectedTextColor = Ta33Theme.colors.fgMuted,
        ),
    )
}

/** Kruhový orange scan FAB s glow - vstupní bod pro QR sken (FR-09), jen při aktivním běhu. */
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
