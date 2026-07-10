import SwiftUI
import Shared

/// Vycentrovaný nativní spinner na cream pozadí (splash řeší app shell).
struct DenikLoadingView: View {
    var body: some View {
        ZStack {
            Ta33Color.cream.ignoresSafeArea()
            ProgressView()
                .tint(Ta33Color.orange)
        }
    }
}

/// Stav „obsah není stažený": identity karta + empty-state karta + CTA.
struct DenikBeforeView: View {
    let onDownload: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: Ta33Spacing.x5) {
                IdentityCard(
                    date: "Sobota 19. 9. 2026",
                    place: "Teplice n. Metují",
                    sub: "Start 7:00–10:00 · prezentace u sokolovny"
                )
                PaperCard {
                    VStack(spacing: Ta33Spacing.x4) {
                        ZStack {
                            Circle()
                                .fill(Ta33Color.orange100)
                                .frame(width: Ta33Spacing.x9, height: Ta33Spacing.x9)
                            Image(systemName: "arrow.down.circle")
                                .font(.system(size: Ta33Spacing.x7))
                                .foregroundStyle(Ta33Color.orange)
                        }
                        Text("Akce ještě není stažená")
                            .font(Ta33Font.h1)
                            .foregroundStyle(Ta33Color.fgStrong)
                            .multilineTextAlignment(.center)
                        Text("Stáhni si trasy, kontroly a mapu, dokud máš signál. Na trase pak vše funguje offline.")
                            .font(Ta33Font.body)
                            .foregroundStyle(Ta33Color.fgMuted)
                            .multilineTextAlignment(.center)
                        PrimaryButton("Stáhnout data akce · 84 MB", action: onDownload)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .padding(Ta33Spacing.x5)
        }
        .background(Ta33Color.cream)
    }
}

/// Stav „na trase": offline banner + progress + skupiny KP řádků (Příští checkpoint / Hotovo).
struct DenikOnRouteView: View {
    let log: LogUiState
    let routeLabel: String
    let offline: Bool

    private var nextEntries: [RunLogEntry] {
        log.entries.filter { $0.state != .done && $0.state != .finish }
    }

    private var doneEntries: [RunLogEntry] {
        log.entries.filter { $0.state == .done || $0.state == .finish }
    }

    private var finishOrdinal: Int32? {
        log.entries.map { $0.control.ordinal }.max()
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Ta33Spacing.x5) {
                if offline {
                    OfflineBanner()
                }
                progressCard
                section("Příští checkpoint", entries: nextEntries)
                section("Hotovo", entries: doneEntries)
            }
            .padding(Ta33Spacing.x5)
        }
        .background(Ta33Color.cream)
    }

    @ViewBuilder private func section(_ title: String, entries: [RunLogEntry]) -> some View {
        if !entries.isEmpty {
            OverlineLabel(title)
            VStack(spacing: Ta33Spacing.x3) {
                ForEach(entries, id: \.control.id) { entry in
                    row(entry)
                }
            }
        }
    }

    private var progressCard: some View {
        PaperCard {
            VStack(alignment: .leading, spacing: Ta33Spacing.x3) {
                HStack(alignment: .firstTextBaseline) {
                    Text(routeLabel)
                        .font(Ta33Font.h2)
                        .foregroundStyle(Ta33Color.fgStrong)
                    Spacer()
                    Text("\(log.collectedCount) / \(log.totalCount)")
                        .font(Ta33Font.display2)
                        .foregroundStyle(Ta33Color.fgStrong)
                }
                progressBar
            }
        }
    }

    private var progressBar: some View {
        let fraction: Double = log.totalCount == 0
            ? 0
            : min(max(Double(log.collectedCount) / Double(log.totalCount), 0), 1)
        return GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Ta33Color.slate100)
                Capsule().fill(Ta33Color.orange)
                    .frame(width: geo.size.width * fraction)
            }
        }
        .frame(height: Ta33Spacing.x3)
    }

    private func row(_ entry: RunLogEntry) -> KPRow {
        let control = entry.control
        let isFinish = finishOrdinal != nil && control.ordinal == finishOrdinal
        let title: String
        if isFinish {
            title = "Cíl · \(control.name)"
        } else {
            let ord = control.ordinal < 10 ? "0\(control.ordinal)" : "\(control.ordinal)"
            title = "KP-\(ord) · \(control.name)"
        }
        let subtitle: String
        switch entry.state {
        case .done, .finish:
            if let millis = entry.collectedAtMillis {
                subtitle = "Splněno · \(Ta33Format.clock(millis.int64Value))"
            } else {
                subtitle = "Splněno"
            }
        case .active:
            subtitle = "Další"
        case .locked:
            subtitle = "Zamčeno"
        }
        return KPRow(ordinal: control.ordinal, title: title, subtitle: subtitle, state: entry.state, isFinish: isFinish)
    }
}

// MARK: - Previews

private func previewLog(collected: Int32) -> LogUiState {
    let names = ["Start", "Sloní pramen", "Vyhlídka", "Kamenné moře", "Adršpach"]
    let entries = names.enumerated().map { index, name -> RunLogEntry in
        let ordinal = Int32(index + 1)
        let state: ControlPointState
        if Int32(index) < collected {
            state = .done
        } else if Int32(index) == collected {
            state = .active
        } else {
            state = .locked
        }
        let control = ControlPoint_(
            id: "kp-\(ordinal)",
            routeId: "route-a",
            ordinal: ordinal,
            name: name,
            location: GeoPoint(latitude: 0, longitude: 0),
            radiusMeters: 50
        )
        let collectedAt: KotlinLong? = state == .done
            ? KotlinLong(longLong: 1_726_726_440_000 + Int64(index) * 600_000)
            : nil
        return RunLogEntry(control: control, state: state, collectedAtMillis: collectedAt, splitMillis: nil)
    }
    return LogUiState(
        entries: entries,
        collectedCount: collected,
        totalCount: Int32(names.count),
        nextControl: nil,
        finishState: .locked,
        isComplete: false,
        isRunFinished: false,
        loading: false
    )
}

#Preview("Loading") {
    DenikLoadingView()
}

#Preview("Before") {
    DenikBeforeView(onDownload: {})
}

#Preview("OnRoute") {
    DenikOnRouteView(log: previewLog(collected: 2), routeLabel: "Trasa A · 33 km", offline: true)
}

#Preview("OnRoute empty") {
    DenikOnRouteView(log: previewLog(collected: 0), routeLabel: "Trasa A · 33 km", offline: true)
}
