import SwiftUI
import Shared

/// Tab „Přehled" (FR-10) — nativní iOS settings vzhled: `List(.insetGrouped)` se dvěma sekcemi.
/// „Tvoje akce" (aktivní trasa, naskenováno jen při běhu, stav dat akce) a „Nastavení"
/// (nativní `Toggle` notifikací → `setNotificationsEnabled`, kontakt na pořadatele, FAQ v `DisclosureGroup`).
/// Ne klon Android karet — záměrně nativní iOS chrome. Etapa 2 prvky (identita/číslo/QR/platba) vynechány.
struct PrehledView: View {
    @StateObject private var model = PrehledModel()

    var body: some View {
        Group {
            // Spinner dokud nejsou hotové OBĚ VM (brání probliknutí stale stavu).
            if model.overview.loading || model.settings.loading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List {
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
                        LabeledContent("Data akce") {
                            Text(packageStatusLabel(model.overview.syncStatus))
                                .foregroundStyle(packageStatusColor(model.overview.syncStatus))
                        }
                    }

                    Section("Nastavení") {
                        Toggle("Notifikace", isOn: Binding(
                            get: { model.settings.notificationsEnabled },
                            set: { model.setNotifications($0) }
                        ))
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
                .listStyle(.insetGrouped)
            }
        }
        .task { await model.observeOverview() }
        .task { await model.observeSettings() }
    }
}

#Preview("PrehledView") {
    PrehledView()
}
