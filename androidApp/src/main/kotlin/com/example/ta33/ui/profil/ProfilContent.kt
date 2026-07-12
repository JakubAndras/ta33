package com.example.ta33.ui.profil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.example.ta33.domain.model.FaqItem
import com.example.ta33.domain.model.OrganizerContact
import com.example.ta33.domain.model.OverviewProgress
import com.example.ta33.domain.model.OverviewUiState
import com.example.ta33.domain.model.PreparationStatus
import com.example.ta33.domain.model.RouteSummary
import com.example.ta33.domain.model.SettingsUiState
import com.example.ta33.BuildConfig
import com.example.ta33.presentation.ProfileMock
import com.example.ta33.presentation.SandboxUiState
import com.example.ta33.resources.Res
import com.example.ta33.resources.prehled_active_route
import com.example.ta33.resources.prehled_no_event
import com.example.ta33.resources.prehled_scanned
import com.example.ta33.resources.prehled_section_event
import com.example.ta33.resources.prehled_section_settings
import com.example.ta33.resources.profil_paid
import com.example.ta33.resources.profil_qr_hint
import com.example.ta33.resources.profil_qr_title
import com.example.ta33.resources.profil_start_number
import com.example.ta33.resources.profil_status
import com.example.ta33.resources.profil_synced
import com.example.ta33.resources.profil_synced_no
import com.example.ta33.resources.settings_contact_organizer
import com.example.ta33.resources.settings_faq
import com.example.ta33.resources.settings_notifications
import com.example.ta33.resources.settings_voice_guidance
import com.example.ta33.ui.components.FaqRow
import com.example.ta33.ui.components.KeyValueRow
import com.example.ta33.ui.components.Overline
import com.example.ta33.ui.components.PaperCard
import com.example.ta33.ui.components.QrGlyph
import com.example.ta33.ui.components.SettingRow
import com.example.ta33.ui.components.Ta33Icons
import com.example.ta33.ui.format.formatKm
import com.example.ta33.ui.theme.Ta33Palette
import com.example.ta33.ui.theme.Ta33Theme
import org.jetbrains.compose.resources.stringResource

/**
 * Stateless obsah tabu „Profil" (RD-03): identita (avatar + jméno + e-mail, mock),
 * karta startovního čísla (slate-800 + stav platby), pak podle běhu buď odbavovací QR
 * (před akcí, `!hasActiveRun`) nebo blok „Tvoje akce" (na trase, `hasActiveRun`),
 * a karta „Nastavení". Identita/číslo/platba/QR jsou Etapa-2 mock; „Tvoje akce" a nastavení
 * z FR-10 VM. Žádný ViewModel — jen stav a zapisovací callbacky.
 */
@Composable
fun ProfilContent(
    overview: OverviewUiState,
    settings: SettingsUiState,
    voiceGuidanceEnabled: Boolean,
    onToggleNotifications: (Boolean) -> Unit,
    onToggleVoiceGuidance: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    paid: Boolean = ProfileMock.paid,
    sandbox: SandboxUiState = SandboxUiState(),
    onSandboxPaid: (Boolean) -> Unit = {},
    onSandboxNaTrase: (Boolean) -> Unit = {},
    onSandboxDownloaded: (Boolean) -> Unit = {},
    onSandboxFinished: (Boolean) -> Unit = {},
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
        IdentityHead()
        StartNumberCard(paid = paid)
        if (overview.hasActiveRun) {
            EventCard(overview)
        } else {
            CheckInQrCard()
        }
        SettingsCard(
            settings = settings,
            voiceGuidanceEnabled = voiceGuidanceEnabled,
            onToggleNotifications = onToggleNotifications,
            onToggleVoiceGuidance = onToggleVoiceGuidance,
        )
        if (BuildConfig.DEBUG) {
            SandboxCard(
                sandbox = sandbox,
                onPaid = onSandboxPaid,
                onNaTrase = onSandboxNaTrase,
                onDownloaded = onSandboxDownloaded,
                onFinished = onSandboxFinished,
            )
        }
    }
}

@Composable
private fun IdentityHead() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Ta33Theme.spacing.x10)
                .clip(CircleShape)
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Ta33Palette.Orange400, Ta33Palette.Orange),
                            center = Offset(size.width * 0.3f, size.height * 0.3f),
                            radius = size.minDimension * 0.7f,
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = ProfileMock.initials,
                style = MaterialTheme.typography.displaySmall,
                color = Ta33Theme.colors.fgOnOrange,
            )
        }
        Column {
            Text(
                text = ProfileMock.displayName,
                style = MaterialTheme.typography.titleLarge,
                color = Ta33Theme.colors.fgStrong,
            )
            Text(
                text = ProfileMock.email,
                style = MaterialTheme.typography.bodyMedium,
                color = Ta33Theme.colors.fgMuted,
            )
        }
    }
}

@Composable
private fun StartNumberCard(paid: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(Ta33Theme.spacing.x2, Ta33Theme.radius.lg),
        color = Ta33Theme.colors.identityBg,
        shape = Ta33Theme.radius.lg,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Ta33Theme.spacing.x5,
                vertical = Ta33Theme.spacing.x5,
            ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x1)) {
                Overline(
                    text = stringResource(Res.string.profil_start_number),
                    color = Ta33Theme.colors.fgOnDarkMuted,
                )
                Text(
                    text = ProfileMock.startNumber.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = Ta33Theme.colors.fgOnDark,
                )
            }
            if (paid) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x1),
                ) {
                    Overline(
                        text = stringResource(Res.string.profil_status),
                        color = Ta33Theme.colors.fgOnDarkMuted,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x1),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Ta33Icons.Check,
                            contentDescription = null,
                            tint = Ta33Theme.colors.success,
                            modifier = Modifier.size(Ta33Theme.spacing.x5),
                        )
                        Text(
                            text = stringResource(Res.string.profil_paid),
                            style = MaterialTheme.typography.titleMedium,
                            color = Ta33Theme.colors.success,
                        )
                    }
                }
            }
        }
    }
}

/** Odbavovací QR (mock) — jen před akcí (`!hasActiveRun`). Placeholder vzor, není skenovatelný. */
@Composable
private fun CheckInQrCard() {
    PaperCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x4),
        ) {
            Overline(text = stringResource(Res.string.profil_qr_title))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .aspectRatio(1f)
                    .clip(Ta33Theme.radius.md)
                    .background(Ta33Theme.colors.scanBg)
                    .padding(Ta33Theme.spacing.x3),
            ) {
                QrGlyph(modifier = Modifier.fillMaxSize().clip(Ta33Theme.radius.xs))
            }
            Text(
                text = stringResource(Res.string.profil_qr_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Ta33Theme.colors.fgMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Blok „Tvoje akce" — jen na trase (`hasActiveRun`). Data z FR-10 [OverviewUiState]. */
@Composable
private fun EventCard(overview: OverviewUiState) {
    PaperCard {
        Column(verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x2)) {
            Overline(text = stringResource(Res.string.prehled_section_event))
            val route = overview.activeRoute
            KeyValueRow(
                label = stringResource(Res.string.prehled_active_route),
                value = if (route != null) {
                    "${route.name} · ${formatKm(route.distanceKm)} km"
                } else {
                    stringResource(Res.string.prehled_no_event)
                },
                showDivider = false,
            )
            overview.progress?.let { progress ->
                KeyValueRow(
                    label = stringResource(Res.string.prehled_scanned),
                    value = "${progress.collectedCount} z ${progress.totalCount} kontrol",
                )
            }
            KeyValueRow(
                label = stringResource(Res.string.profil_synced),
                value = stringResource(Res.string.profil_synced_no),
                valueColor = Ta33Theme.colors.warning,
            )
        }
    }
}

@Composable
private fun SettingsCard(
    settings: SettingsUiState,
    voiceGuidanceEnabled: Boolean,
    onToggleNotifications: (Boolean) -> Unit,
    onToggleVoiceGuidance: (Boolean) -> Unit,
) {
    PaperCard {
        Column(verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x2)) {
            Overline(text = stringResource(Res.string.prehled_section_settings))
            SettingRow(label = stringResource(Res.string.settings_notifications), showDivider = false) {
                Switch(checked = settings.notificationsEnabled, onCheckedChange = onToggleNotifications)
            }
            SettingRow(label = stringResource(Res.string.settings_voice_guidance)) {
                Switch(checked = voiceGuidanceEnabled, onCheckedChange = onToggleVoiceGuidance)
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

/** DEV / TESTING ONLY (UI-12) — přepínače reálného stavu appky. Gate `BuildConfig.DEBUG` v callerovi. */
@Composable
private fun SandboxCard(
    sandbox: SandboxUiState,
    onPaid: (Boolean) -> Unit,
    onNaTrase: (Boolean) -> Unit,
    onDownloaded: (Boolean) -> Unit,
    onFinished: (Boolean) -> Unit,
) {
    PaperCard {
        Column(verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x2)) {
            Overline(text = "🧪 Sandbox")
            SettingRow(label = "Zaplaceno", showDivider = false) {
                Switch(checked = sandbox.paid, onCheckedChange = onPaid)
            }
            SettingRow(label = "Aktivní běh (na trase)") {
                Switch(checked = sandbox.naTrase, onCheckedChange = onNaTrase)
            }
            SettingRow(label = "Data akce / mapa stažena") {
                Switch(checked = sandbox.downloaded, onCheckedChange = onDownloaded)
            }
            SettingRow(label = "Běh dokončen (Hotovo)") {
                Switch(
                    checked = sandbox.finished,
                    onCheckedChange = onFinished,
                    enabled = sandbox.runExists,
                )
            }
        }
    }
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

private val previewSettings = SettingsUiState(
    notificationsEnabled = true,
    organizerContact = previewContact,
    faq = previewFaq,
    loading = false,
)

@Preview
@Composable
private fun ProfilOnRoutePreview() {
    Ta33Theme {
        ProfilContent(
            overview = OverviewUiState(
                activeRoute = RouteSummary("a", "Trasa A", 33.0, 5),
                hasActiveRun = true,
                progress = OverviewProgress(collectedCount = 2, totalCount = 5, isComplete = false, isRunFinished = false),
                syncStatus = PreparationStatus.READY,
                loading = false,
            ),
            settings = previewSettings,
            voiceGuidanceEnabled = false,
            onToggleNotifications = {},
            onToggleVoiceGuidance = {},
        )
    }
}

@Preview
@Composable
private fun ProfilBeforePreview() {
    Ta33Theme {
        ProfilContent(
            overview = OverviewUiState(
                activeRoute = RouteSummary("a", "Trasa A", 33.0, 5),
                hasActiveRun = false,
                progress = null,
                syncStatus = PreparationStatus.READY,
                loading = false,
            ),
            settings = previewSettings,
            voiceGuidanceEnabled = true,
            onToggleNotifications = {},
            onToggleVoiceGuidance = {},
        )
    }
}
