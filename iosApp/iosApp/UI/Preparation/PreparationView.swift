import SwiftUI
import Shared

/// „Příprava dat akce" (FR-11) nad sdíleným `DownloadViewModel` (přes `PreparationModel`).
/// Gate obrazovka pro readiness `.notReady`/`.preparing`: identity karta + karta s výzvou /
/// průběhem / pauzou / chybou podle `DownloadUiState`. Přechod po dokončení řeší `RootView`
/// (readiness gate) — tady žádná explicitní navigace není. Zrcadlí Android `PreparationContent` (ui-07),
/// ale nativně: `ProgressView(value:)` a `Toggle`.
struct PreparationView: View {
    @StateObject private var model = PreparationModel()

    var body: some View {
        ScrollView {
            VStack(spacing: Ta33Spacing.x5) {
                IdentityCard(
                    date: "Sobota 19. 9. 2026",
                    place: "Teplice n. Metují",
                    sub: "Start 7:00–10:00 · prezentace u sokolovny"
                )

                PaperCard {
                    VStack(alignment: .leading, spacing: Ta33Spacing.x4) {
                        statusSection
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                if model.state.blockedByNetwork {
                    WarningBanner(text: "Čeká na Wi-Fi — připoj se, ať můžeš stáhnout data")
                }
            }
            .padding(Ta33Spacing.x5)
        }
        .background(Ta33Color.cream)
        .task { await model.observe() }
    }

    @ViewBuilder private var statusSection: some View {
        switch model.state.progress.overallStatus {
        case .idle:
            idleSection
        case .downloading:
            downloadingSection
        case .paused:
            pausedSection
        case .error:
            errorSection
        default:
            doneSection
        }
    }

    // MARK: - Sections

    @ViewBuilder private var idleSection: some View {
        Text("Stáhni si trasy, kontroly a mapu, dokud máš signál. Na trase pak vše funguje offline.")
            .font(Ta33Font.body)
            .foregroundStyle(Ta33Color.fgMuted)
        Toggle(
            "Jen přes Wi-Fi",
            isOn: Binding(
                get: { model.state.networkPreference == .wifiOnly },
                set: { model.setWifiOnly($0) }
            )
        )
        .font(Ta33Font.bodyStrong)
        .foregroundStyle(Ta33Color.fgDefault)
        .tint(Ta33Color.orange)
        if !model.state.blockedByNetwork {
            PrimaryButton("Stáhnout data akce · 84 MB") { model.start() }
        }
    }

    @ViewBuilder private var downloadingSection: some View {
        OverlineLabel("Stahuji data akce")
        ProgressView(value: model.state.progress.overallFraction)
            .tint(Ta33Color.orange)
        ForEach(model.state.progress.items, id: \.id) { ItemRow(item: $0) }
        OutlineButton("Pozastavit") { model.pause() }
    }

    @ViewBuilder private var pausedSection: some View {
        ProgressView(value: model.state.progress.overallFraction)
            .tint(Ta33Color.orange)
        ForEach(model.state.progress.items, id: \.id) { ItemRow(item: $0) }
        if !model.state.blockedByNetwork {
            PrimaryButton("Pokračovat") { model.resume() }
        }
    }

    @ViewBuilder private var errorSection: some View {
        Text("Stahování selhalo")
            .font(Ta33Font.bodyStrong)
            .foregroundStyle(Ta33Color.error)
        PrimaryButton("Zkusit znovu") { model.retry() }
    }

    /// Přechodný stav — `RootView` přepne na `TabView`, jakmile se readiness stane `.ready`.
    @ViewBuilder private var doneSection: some View {
        HStack {
            Spacer()
            ProgressView().tint(Ta33Color.orange)
            Spacer()
        }
    }
}

/// Řádek balíčku: název vlevo, vpravo stav (fajfka / vykřičník / procenta).
private struct ItemRow: View {
    let item: DownloadItemProgress

    var body: some View {
        HStack(spacing: Ta33Spacing.x3) {
            Text(item.label)
                .font(Ta33Font.small)
                .foregroundStyle(Ta33Color.fgDefault)
                .frame(maxWidth: .infinity, alignment: .leading)
            switch item.status {
            case .done:
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: Ta33Spacing.x5))
                    .foregroundStyle(Ta33Color.success)
            case .error:
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: Ta33Spacing.x5))
                    .foregroundStyle(Ta33Color.error)
            default:
                Text("\(Int(item.fraction * 100)) %")
                    .font(Ta33Font.small)
                    .foregroundStyle(Ta33Color.fgMuted)
            }
        }
    }
}

/// Warning banner — warning-tint pozadí, ⚠ (SF Symbol) + text. Styl zrcadlí `OfflineBanner`,
/// ale s proměnným textem (např. „Čeká na Wi-Fi").
private struct WarningBanner: View {
    let text: String

    var body: some View {
        HStack(spacing: Ta33Spacing.x2) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: Ta33Spacing.x4))
                .foregroundStyle(Ta33Color.warning)
            Text(text)
                .font(Ta33Font.bodyStrong)
                .foregroundStyle(Ta33Color.fgDefault)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Ta33Spacing.x3)
        .background(Ta33Color.warningTint)
        .clipShape(RoundedRectangle(cornerRadius: Ta33Radius.md, style: .continuous))
    }
}

#Preview("Preparation") {
    PreparationView()
}
