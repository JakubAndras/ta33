import SwiftUI
import Shared

/// Na iOS 26 plovoucí Liquid Glass lišta nezmenšuje safe area (obsah jde edge-to-edge pod ni).
/// `contentMargins` přidá scroll obsahu spodní clearance, aby šla **poslední kontrola (cíl)
/// doscrollovat nad lištu** a nezůstala schovaná za sklem - všechny kontroly na trase zůstávají
/// viditelné a scrollovatelné. iOS 18 lišta je neprůhledná a ukotvená, safe area řeší systém.
private struct GlassTabBarClearance: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content.contentMargins(.bottom, Ta33Spacing.x10, for: .scrollContent)
        } else {
            content
        }
    }
}

/// Vycentrovaný nativní spinner na cream pozadí (splash řeší app shell).
struct DenikLoadingView: View {
    var body: some View {
        ZStack {
            Ta33Color.cream.ignoresSafeArea()
            ProgressView().tint(Ta33Color.orange)
        }
    }
}

/// Deník dle kanonického `VariantPrehled`: tmavý header (TRASA/shortId/Přepnout + 3 staty),
/// timeline „Kontroly na trase" (start/kontroly/cíl s km, úsekem, mezičasem, stavem) a výškový profil.
struct DenikVariantPrehledView: View {
    let state: DenikUiState
    let onSwitch: () -> Void

    private var routeColor: Color { state.shortId == "TA50" ? Ta33Color.error : Ta33Color.orange }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Ta33Spacing.x4) {
                header
                sectionHeader("Kontroly na trase")
                timeline
                if let elevation = state.elevation {
                    elevationCard(elevation)
                }
            }
            .padding(Ta33Spacing.x4)
        }
        .modifier(GlassTabBarClearance())
    }

    // MARK: Header

    private var header: some View {
        VStack(alignment: .leading, spacing: Ta33Spacing.x3) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: Ta33Spacing.x1) {
                    OverlineLabel("Trasa", color: Ta33Color.fgOnDarkMuted)
                    Text(state.shortId)
                        .font(Ta33Font.display2)
                        .foregroundStyle(Ta33Color.fgOnDark)
                }
                Spacer()
                switchPill
            }
            HStack(spacing: Ta33Spacing.x6) {
                headerStat(fmtKm(state.distanceKm) + " km", "Délka")
                headerStat(clockText(state.startTimeMillis), "Čas startu")
                headerStat(clockText(state.finishTimeMillis), "Finální čas")
            }
        }
        .padding(.horizontal, Ta33Spacing.x5)
        .padding(.vertical, Ta33Spacing.x4)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Ta33Color.slate800)
        .clipShape(RoundedRectangle(cornerRadius: Ta33Radius.lg, style: .continuous))
    }

    private var switchPill: some View {
        Button(action: onSwitch) {
            HStack(spacing: Ta33Spacing.x2) {
                Text("Přepnout").font(Ta33Font.caption)
                Image(systemName: "arrow.left.arrow.right").font(.system(size: 12, weight: .bold))
            }
            .foregroundStyle(state.canSwitch ? Ta33Color.fgOnDark : Ta33Color.fgOnDarkMuted)
            .padding(.horizontal, Ta33Spacing.x3)
            .padding(.vertical, Ta33Spacing.x2)
            .background(Ta33Color.fgOnOrange.opacity(0.12))
            .clipShape(Capsule())
        }
        .disabled(!state.canSwitch)
    }

    private func headerStat(_ value: String, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: Ta33Spacing.x1) {
            Text(value).font(Ta33Font.display3).foregroundStyle(Ta33Color.fgOnDark)
            OverlineLabel(label, color: Ta33Color.fgOnDarkMuted)
        }
    }

    // MARK: Timeline

    private func sectionHeader(_ text: String) -> some View {
        HStack(spacing: Ta33Spacing.x2) {
            OverlineLabel(text)
            Rectangle().fill(Ta33Color.slate100).frame(height: 1)
        }
    }

    private var timeline: some View {
        VStack(spacing: 0) {
            ForEach(Array(state.stops.enumerated()), id: \.element.km) { index, stop in
                timelineRow(stop, hasNext: index < state.stops.count - 1)
            }
        }
    }

    private func timelineRow(_ stop: DenikStop, hasNext: Bool) -> some View {
        HStack(alignment: .top, spacing: Ta33Spacing.x4) {
            VStack(spacing: 0) {
                TimelineNodeView(
                    status: stop.status,
                    kind: stop.kind,
                    ordinal: stop.controlOrdinal.map { $0.int32Value }
                )
                if hasNext {
                    Rectangle()
                        .fill(stop.status == .done ? Ta33Color.success : Ta33Color.kpLockedBg)
                        .frame(width: 3)
                        .frame(maxHeight: .infinity)
                        .padding(.vertical, Ta33Spacing.x1)
                }
            }
            .frame(width: 38)

            timelineContent(stop, hasNext: hasNext)
        }
    }

    private func timelineContent(_ stop: DenikStop, hasNext: Bool) -> some View {
        VStack(alignment: .leading, spacing: Ta33Spacing.x1) {
            HStack(alignment: .top) {
                Text(stop.label.uppercased() + statusText(stop))
                    .font(Ta33Font.overline)
                    .kerning(1.0)
                    .foregroundStyle(labelColor(stop.status))
                Spacer()
                Text(fmtKm(stop.km) + " km")
                    .font(.system(size: 16, weight: .black))
                    .foregroundStyle(Ta33Color.fgStrong)
            }
            Text(stop.name)
                .font(Ta33Font.h3)
                .foregroundStyle(Ta33Color.fgStrong)
            if let segment = stop.segmentKm?.doubleValue {
                segmentChip(segment)
            }
        }
        .padding(.bottom, hasNext ? Ta33Spacing.x5 : 0)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func segmentChip(_ km: Double) -> some View {
        HStack(spacing: Ta33Spacing.x2) {
            Text("úsek \(fmtKm(km)) km").font(Ta33Font.caption).foregroundStyle(Ta33Color.fgMuted)
            Rectangle().fill(Ta33Color.kpLockedBg).frame(width: 1, height: 12)
            Text("mezičas —:—").font(Ta33Font.caption).foregroundStyle(Ta33Color.fgFaint)
        }
        .padding(.horizontal, Ta33Spacing.x3)
        .padding(.vertical, Ta33Spacing.x1)
        .background(Ta33Color.creamDeep)
        .clipShape(RoundedRectangle(cornerRadius: Ta33Radius.sm, style: .continuous))
    }

    // MARK: Elevation

    private func elevationCard(_ profile: ElevationProfile) -> some View {
        PaperCard {
            VStack(alignment: .leading, spacing: Ta33Spacing.x3) {
                HStack {
                    OverlineLabel("Výškový profil")
                    Spacer()
                    HStack(spacing: Ta33Spacing.x4) {
                        climbStat("arrow.up.right", Int(profile.ascentMeters))
                        climbStat("arrow.down.right", Int(profile.descentMeters))
                    }
                }
                ElevationChartView(profile: profile, routeColor: routeColor)
            }
        }
    }

    private func climbStat(_ symbol: String, _ meters: Int) -> some View {
        HStack(spacing: Ta33Spacing.x1) {
            Image(systemName: symbol).font(.system(size: 12, weight: .bold)).foregroundStyle(Ta33Color.fgMuted)
            Text("\(meters) m").font(.system(size: 15, weight: .black)).foregroundStyle(Ta33Color.fgStrong)
        }
    }

    // MARK: Helpers

    private func labelColor(_ status: StopStatus) -> Color {
        switch status {
        case .done: return Ta33Color.success
        case .next: return Ta33Color.orange
        default: return Ta33Color.fgMuted
        }
    }

    private func statusText(_ stop: DenikStop) -> String {
        if stop.kind == .start, let ms = stop.startTimeMillis {
            return " · " + Ta33Format.clock(ms.int64Value)
        }
        switch stop.status {
        case .done: return " · splněno"
        case .next: return " · následující"
        default: return ""
        }
    }

    private func clockText(_ millis: KotlinLong?) -> String {
        guard let millis else { return "—" }
        return Ta33Format.clock(millis.int64Value)
    }

    /// Km s českou desetinnou čárkou; celé číslo bez desetin (zrcadlí Android `formatKm`).
    private func fmtKm(_ km: Double) -> String {
        if km.truncatingRemainder(dividingBy: 1) == 0 { return String(Int(km)) }
        return String(format: "%.1f", km).replacingOccurrences(of: ".", with: ",")
    }
}

// MARK: - Previews

private func previewState() -> DenikUiState {
    let elevation = RouteCatalog.shared.itineraries.first!.elevation
    let stops: [DenikStop] = [
        DenikStop(kind: .start, label: "Start", name: "Koupaliště", km: 0.0, controlOrdinal: nil,
                  status: .done, segmentKm: KotlinDouble(double: 5.9),
                  startTimeMillis: KotlinLong(longLong: 1_726_726_320_000), isFinish: false),
        DenikStop(kind: .control, label: "Kontrola 1", name: "Zámek Bischofstein", km: 5.9,
                  controlOrdinal: KotlinInt(int: 1), status: .done, segmentKm: KotlinDouble(double: 6.5),
                  startTimeMillis: nil, isFinish: false),
        DenikStop(kind: .control, label: "Kontrola 2", name: "Vlčí rokle – Pod 7 sch.", km: 12.4,
                  controlOrdinal: KotlinInt(int: 2), status: .next, segmentKm: KotlinDouble(double: 4.5),
                  startTimeMillis: nil, isFinish: false),
        DenikStop(kind: .finish, label: "Cíl", name: "Koupaliště", km: 33.2, controlOrdinal: nil,
                  status: .upcoming, segmentKm: nil, startTimeMillis: nil, isFinish: true),
    ]
    return DenikUiState(
        routeId: "dev-ta33", shortId: "TA33", distanceKm: 33.2,
        startTimeMillis: KotlinLong(longLong: 1_726_726_320_000), finishTimeMillis: nil,
        ascentMeters: 740, descentMeters: 740, elevation: elevation, stops: stops,
        canSwitch: true, loading: false
    )
}

#Preview("Loading") {
    DenikLoadingView()
}

#Preview("VariantPrehled") {
    DenikVariantPrehledView(state: previewState(), onSwitch: {})
}
