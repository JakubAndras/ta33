package com.example.ta33.ui.prehled

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.example.ta33.domain.model.FaqItem
import com.example.ta33.domain.model.OrganizerContact
import com.example.ta33.domain.model.OverviewProgress
import com.example.ta33.domain.model.OverviewUiState
import com.example.ta33.domain.model.PreparationStatus
import com.example.ta33.domain.model.RouteSummary
import com.example.ta33.domain.model.SettingsUiState
import com.example.ta33.resources.Res
import com.example.ta33.resources.pkg_error
import com.example.ta33.resources.pkg_not_started
import com.example.ta33.resources.pkg_preparing
import com.example.ta33.resources.pkg_ready
import com.example.ta33.resources.prehled_active_route
import com.example.ta33.resources.prehled_event_data
import com.example.ta33.resources.prehled_no_event
import com.example.ta33.resources.prehled_scanned
import com.example.ta33.resources.prehled_section_event
import com.example.ta33.resources.prehled_section_settings
import com.example.ta33.resources.settings_contact_organizer
import com.example.ta33.resources.settings_faq
import com.example.ta33.resources.settings_notifications
import com.example.ta33.ui.components.FaqRow
import com.example.ta33.ui.components.KeyValueRow
import com.example.ta33.ui.components.Overline
import com.example.ta33.ui.components.PaperCard
import com.example.ta33.ui.components.SettingRow
import com.example.ta33.ui.components.Ta33Icons
import com.example.ta33.ui.format.formatKm
import com.example.ta33.ui.theme.Ta33Theme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Stateless obsah tabu „Přehled": karta „Tvoje akce" (aktivní trasa, progress, stav dat)
 * a karta „Nastavení" (notifikace, kontakt na pořadatele, rozbalovací FAQ).
 * Žádný ViewModel — jen stav a jediný zapisovací callback.
 */
@Composable
fun PrehledContent(
    overview: OverviewUiState,
    settings: SettingsUiState,
    onToggleNotifications: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (overview.loading || settings.loading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Ta33Theme.spacing.x5),
        verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x5),
    ) {
        EventCard(overview)
        SettingsCard(settings = settings, onToggleNotifications = onToggleNotifications)
    }
}

@Composable
private fun EventCard(overview: OverviewUiState) {
    PaperCard {
        Column(verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x2)) {
            Overline(text = stringResource(Res.string.prehled_section_event))
            val route = overview.activeRoute
            if (route != null) {
                KeyValueRow(
                    label = stringResource(Res.string.prehled_active_route),
                    value = "${route.name} · ${formatKm(route.distanceKm)} km",
                    showDivider = false,
                )
            } else {
                Text(
                    text = stringResource(Res.string.prehled_no_event),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ta33Theme.colors.fgMuted,
                )
            }
            overview.progress?.let { progress ->
                KeyValueRow(
                    label = stringResource(Res.string.prehled_scanned),
                    value = "${progress.collectedCount} z ${progress.totalCount} kontrol",
                )
            }
            KeyValueRow(
                label = stringResource(Res.string.prehled_event_data),
                value = stringResource(packageStatusLabel(overview.syncStatus)),
                valueColor = packageStatusColor(overview.syncStatus),
            )
        }
    }
}

@Composable
private fun SettingsCard(
    settings: SettingsUiState,
    onToggleNotifications: (Boolean) -> Unit,
) {
    PaperCard {
        Column(verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x2)) {
            Overline(text = stringResource(Res.string.prehled_section_settings))
            SettingRow(label = stringResource(Res.string.settings_notifications), showDivider = false) {
                Switch(checked = settings.notificationsEnabled, onCheckedChange = onToggleNotifications)
            }
            settings.organizerContact?.let { contact ->
                SettingRow(label = stringResource(Res.string.settings_contact_organizer)) {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Ta33Theme.colors.fgMuted,
                    )
                    Icon(
                        imageVector = Ta33Icons.ChevronRight,
                        contentDescription = null,
                        tint = Ta33Theme.colors.fgFaint,
                        modifier = Modifier.size(Ta33Theme.spacing.x5),
                    )
                }
            }
            if (settings.faq.isNotEmpty()) {
                Overline(text = stringResource(Res.string.settings_faq))
                settings.faq.forEachIndexed { index, item ->
                    key(item.id) {
                        FaqRow(item = item, showDivider = index > 0)
                    }
                }
            }
        }
    }
}

/** Stav offline balíčku (Etapa 1) → lokalizovaný label. */
private fun packageStatusLabel(status: PreparationStatus): StringResource = when (status) {
    PreparationStatus.NOT_STARTED -> Res.string.pkg_not_started
    PreparationStatus.PREPARING -> Res.string.pkg_preparing
    PreparationStatus.READY -> Res.string.pkg_ready
    PreparationStatus.ERROR -> Res.string.pkg_error
}

@Composable
private fun packageStatusColor(status: PreparationStatus): Color = when (status) {
    PreparationStatus.READY -> Ta33Theme.colors.success
    PreparationStatus.ERROR -> Ta33Theme.colors.error
    PreparationStatus.NOT_STARTED, PreparationStatus.PREPARING -> Ta33Theme.colors.warning
}

// ---- Previews ---------------------------------------------------------------

private val previewContact = OrganizerContact(
    name = "Spolek TA33",
    email = "info@ta33.cz",
    phone = "+420 777 123 456",
)

private val previewFaq = listOf(
    FaqItem("1", "Funguje aplikace offline?", "Ano, po stažení dat akce funguje vše bez signálu."),
    FaqItem("2", "Jak sbírám kontroly?", "Namiř foťák na QR kód u kontroly."),
)

@Preview
@Composable
private fun PrehledFullPreview() {
    Ta33Theme {
        PrehledContent(
            overview = OverviewUiState(
                activeRoute = RouteSummary("a", "Trasa A", 33.2, 5),
                hasActiveRun = true,
                progress = OverviewProgress(collectedCount = 2, totalCount = 5, isComplete = false, isRunFinished = false),
                syncStatus = PreparationStatus.READY,
                loading = false,
            ),
            settings = SettingsUiState(
                notificationsEnabled = true,
                organizerContact = previewContact,
                faq = previewFaq,
                loading = false,
            ),
            onToggleNotifications = {},
        )
    }
}

@Preview
@Composable
private fun PrehledNoRunPreview() {
    Ta33Theme {
        PrehledContent(
            overview = OverviewUiState(
                activeRoute = RouteSummary("a", "Trasa A", 33.0, 5),
                hasActiveRun = false,
                progress = null,
                syncStatus = PreparationStatus.READY,
                loading = false,
            ),
            settings = SettingsUiState(
                notificationsEnabled = false,
                organizerContact = previewContact,
                faq = previewFaq,
                loading = false,
            ),
            onToggleNotifications = {},
        )
    }
}

@Preview
@Composable
private fun PrehledNoRoutePreview() {
    Ta33Theme {
        PrehledContent(
            overview = OverviewUiState(
                activeRoute = null,
                hasActiveRun = false,
                progress = null,
                syncStatus = PreparationStatus.NOT_STARTED,
                loading = false,
            ),
            settings = SettingsUiState(
                notificationsEnabled = true,
                organizerContact = previewContact,
                faq = emptyList(),
                loading = false,
            ),
            onToggleNotifications = {},
        )
    }
}

@Preview
@Composable
private fun PrehledLoadingPreview() {
    Ta33Theme {
        PrehledContent(
            overview = OverviewUiState(loading = true),
            settings = SettingsUiState(loading = true),
            onToggleNotifications = {},
        )
    }
}
