import SwiftUI
import Shared

/// Tab „Profil" (RD-03) — nativní iOS vzhled: `List(.insetGrouped)`.
/// Identita (avatar + jméno + e-mail, mock), karta startovního čísla (slate + stav platby),
/// pak podle běhu buď odbavovací QR (před akcí) nebo blok „Tvoje akce" (na trase),
/// a „Nastavení" (nativní `Toggle`, kontakt, FAQ v `DisclosureGroup`).
/// Identita/číslo/platba/QR jsou Etapa-2 mock; „Tvoje akce" + nastavení z FR-10 VM.
/// Záměrně nativní iOS chrome — ne klon Android karet.
struct ProfilView: View {
    @StateObject private var model = ProfilModel()
    @State private var voiceGuidanceEnabled = false
    #if DEBUG
    @StateObject private var sandbox = SandboxModel()
    #endif

    var body: some View {
        Group {
            // Spinner dokud nejsou hotové OBĚ VM (brání probliknutí stale stavu).
            if model.overview.loading || model.settings.loading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                content
            }
        }
        .task { await model.observeOverview() }
        .task { await model.observeSettings() }
        #if DEBUG
        .task { await sandbox.observe() }
        #endif
    }

    private var content: some View {
        List {
            Section {
                identityHead
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
            }

            Section {
                startNumberRow
                    .listRowBackground(Ta33Color.slate800)
            }

            if model.overview.hasActiveRun {
                eventSection
            } else {
                qrSection
            }

            settingsSection

            #if DEBUG
            sandboxSection
            #endif
        }
        .listStyle(.insetGrouped)
        // Horní okraj na rytmus design systému (~space-5 = 20pt). Nativní inset-grouped List má
        // vlastní horní scroll inset (~36pt) navíc k paddingu řádku/sekce; contentMargins ho
        // nahrazuje nulou, takže zůstane jen ~18pt vnitřního paddingu = cílové pásmo návrhu (16–20).
        .contentMargins(.top, 0, for: .scrollContent)
        .listSectionSpacing(Ta33Spacing.x5)
        .scrollContentBackground(.hidden)
        .background(Ta33Color.cream)
    }

    // MARK: - Identita

    private var identityHead: some View {
        HStack(spacing: Ta33Spacing.x4) {
            Circle()
                .fill(
                    RadialGradient(
                        colors: [Ta33Color.orange400, Ta33Color.orange],
                        center: UnitPoint(x: 0.3, y: 0.3),
                        startRadius: 0,
                        endRadius: Ta33Spacing.x10 / 2
                    )
                )
                .frame(width: Ta33Spacing.x10, height: Ta33Spacing.x10)
                .overlay(
                    Text(ProfileMock.shared.initials)
                        .font(Ta33Font.display2)
                        .foregroundStyle(Ta33Color.fgOnOrange)
                )
            VStack(alignment: .leading, spacing: Ta33Spacing.x1) {
                Text(ProfileMock.shared.displayName)
                    .font(Ta33Font.h2)
                    .foregroundStyle(Ta33Color.fgStrong)
                Text(ProfileMock.shared.email)
                    .font(Ta33Font.small)
                    .foregroundStyle(Ta33Color.fgMuted)
            }
            Spacer(minLength: 0)
        }
    }

    // MARK: - Startovní číslo

    private var startNumberRow: some View {
        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: Ta33Spacing.x1) {
                overline("Startovní číslo", color: Ta33Color.fgOnDarkMuted)
                Text(String(ProfileMock.shared.startNumber))
                    .font(Ta33Font.display1)
                    .foregroundStyle(Ta33Color.fgOnDark)
            }
            Spacer()
            if isPaid {
                VStack(alignment: .trailing, spacing: Ta33Spacing.x1) {
                    overline("Stav", color: Ta33Color.fgOnDarkMuted)
                    HStack(spacing: Ta33Spacing.x1) {
                        Image(systemName: "checkmark")
                            .font(Ta33Font.bodyStrong)
                        Text("Zaplaceno")
                            .font(Ta33Font.h3)
                    }
                    .foregroundStyle(Ta33Color.success)
                }
            }
        }
        .padding(.vertical, Ta33Spacing.x2)
    }

    /// Stav platby: v DEBUG čte přepínatelný Sandbox override, jinak statický `ProfileMock.paid`.
    private var isPaid: Bool {
        #if DEBUG
        return sandbox.state.paid
        #else
        return ProfileMock.shared.paid
        #endif
    }

    // MARK: - Odbavovací QR (před akcí)

    private var qrSection: some View {
        Section("Tvůj odbavovací QR") {
            VStack(spacing: Ta33Spacing.x4) {
                QrGlyphView()
                    .clipShape(RoundedRectangle(cornerRadius: Ta33Radius.xs))
                    .padding(Ta33Spacing.x3)
                    .background(Ta33Color.slate900)
                    .clipShape(RoundedRectangle(cornerRadius: Ta33Radius.md))
                    .frame(width: Ta33Spacing.x10 * 2, height: Ta33Spacing.x10 * 2)
                Text("Ukaž na prezentaci, pořadatel tě jím odbaví.")
                    .font(Ta33Font.small)
                    .foregroundStyle(Ta33Color.fgMuted)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Ta33Spacing.x2)
        }
    }

    // MARK: - Tvoje akce (na trase)

    private var eventSection: some View {
        Section("Tvoje akce") {
            if let route = model.overview.activeRoute {
                LabeledContent("Aktivní trasa", value: "\(route.name) · \(kmLabel(route.distanceKm)) km")
            } else {
                Text("Zatím žádná aktivní akce")
                    .foregroundStyle(Ta33Color.fgMuted)
            }
            if let progress = model.overview.progress {
                LabeledContent(
                    "Naskenováno",
                    value: "\(progress.collectedCount) z \(progress.totalCount) kontrol"
                )
            }
            LabeledContent("Synchronizováno") {
                Text("Ne").foregroundStyle(Ta33Color.warning)
            }
        }
    }

    // MARK: - Nastavení

    private var settingsSection: some View {
        Section("Nastavení") {
            Toggle("Notifikace", isOn: Binding(
                get: { model.settings.notificationsEnabled },
                set: { model.setNotifications($0) }
            ))
            .tint(Ta33Color.orange)

            Toggle("Hlasové pokyny", isOn: $voiceGuidanceEnabled)
                .tint(Ta33Color.orange)

            if let contact = model.settings.organizerContact {
                LabeledContent("Kontaktovat pořadatele", value: contact.name)
            }

            ForEach(model.settings.faq, id: \.id) { item in
                DisclosureGroup(item.question) {
                    Text(item.answer)
                        .foregroundStyle(Ta33Color.fgMuted)
                }
            }
        }
    }

    #if DEBUG
    // MARK: - Sandbox (DEV, jen DEBUG) — přepínače reálného stavu appky (UI-12)

    private var sandboxSection: some View {
        Section {
            sandboxToggle("Zaplaceno", isOn: sandbox.state.paid) { sandbox.setPaid($0) }
            sandboxToggle("Aktivní běh (na trase)", isOn: sandbox.state.naTrase) { sandbox.setNaTrase($0) }
            sandboxToggle("Data akce / mapa stažena", isOn: sandbox.state.downloaded) { sandbox.setDownloaded($0) }
            sandboxToggle("Běh dokončen (Hotovo)", isOn: sandbox.state.finished) { sandbox.setFinished($0) }
                .disabled(!sandbox.state.runExists)
        } header: {
            Text("🧪 Sandbox")
        } footer: {
            Text("Vývojové přepínače stavů appky. Jen v DEBUG buildu.")
        }
    }

    private func sandboxToggle(
        _ title: String,
        isOn: Bool,
        set: @escaping (Bool) -> Void
    ) -> some View {
        Toggle(title, isOn: Binding(get: { isOn }, set: set))
            .tint(Ta33Color.orange)
    }
    #endif

    private func overline(_ text: String, color: Color) -> some View {
        Text(text.uppercased())
            .font(Ta33Font.overline)
            .tracking(1.3)
            .foregroundStyle(color)
    }
}

#Preview("ProfilView") {
    ProfilView()
}
