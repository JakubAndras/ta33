import SwiftUI
import Shared

/// Mapa (RD-02) — kanonický `VariantHybrid`. Pozoruje sdílený `MapaViewModel` přes `MapaModel`.
struct MapaView: View {
    @StateObject private var model = MapaModel()

    var body: some View {
        ZStack {
            Ta33Color.cream.ignoresSafeArea()
            if let state = model.state, !state.loading {
                MapaHybridView(state: state, onSwitch: model.toggle, onHighlight: model.highlight)
            } else {
                ProgressView().tint(Ta33Color.orange)
            }
        }
        .task { await model.observe() }
    }
}

/// Mapa dle kanonického `VariantHybrid`: nahoře schematická mapa (fixní výška) + route chip s
/// přepínačem, dole srolovatelný kompaktní itinerář (km + KP badge + název + směr/značka).
struct MapaHybridView: View {
    let state: MapaUiState
    let onSwitch: () -> Void
    let onHighlight: (Int32?) -> Void

    private let mapHeight: CGFloat = 452
    private var routeColor: Color { state.shortId == "TA50" ? Ta33Color.error : Ta33Color.orange }
    private var highlighted: Int32? { state.highlightedControl?.int32Value }

    var body: some View {
        VStack(spacing: 0) {
            ZStack(alignment: .top) {
                SchematicMapView(
                    controlsCount: Int(state.controlsCount),
                    routeColor: routeColor,
                    dashed: state.shortId == "TA50",
                    highlightedControl: highlighted,
                    onPin: { ordinal in onHighlight(highlighted == ordinal ? nil : ordinal) }
                )
                routeChip
                    .padding(.horizontal, Ta33Spacing.x4)
                    .padding(.top, Ta33Spacing.x3)
            }
            .frame(height: mapHeight)
            .clipped()

            itinerary
        }
    }

    // MARK: Route chip

    private var routeChip: some View {
        HStack(spacing: Ta33Spacing.x2) {
            HStack(spacing: Ta33Spacing.x2) {
                Text(state.letter)
                    .font(.system(size: 13, weight: .black))
                    .foregroundStyle(Ta33Color.fgOnOrange)
                    .frame(width: 24, height: 24)
                    .background(Ta33Color.slate800)
                    .clipShape(RoundedRectangle(cornerRadius: 7, style: .continuous))
                Text("Trasa \(state.shortId) · \(fmtKm(state.distanceKm)) km · ↑\(state.ascentMeters) m")
                    .font(.system(size: 13.5, weight: .semibold))
                    .foregroundStyle(Ta33Color.fgStrong)
                    .lineLimit(1)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, Ta33Spacing.x3)
            .padding(.vertical, Ta33Spacing.x2)
            .frame(maxWidth: .infinity)
            .background(Ta33Color.paper)
            .clipShape(Capsule())
            .shadow(color: .black.opacity(0.12), radius: 8, x: 0, y: 2)

            Button(action: onSwitch) {
                Image(systemName: "arrow.left.arrow.right")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(state.canSwitch ? Ta33Color.fgStrong : Ta33Color.fgFaint)
                    .frame(width: 40, height: 40)
                    .background(Ta33Color.paper)
                    .clipShape(Circle())
                    .shadow(color: .black.opacity(0.12), radius: 8, x: 0, y: 2)
            }
            .disabled(!state.canSwitch)
        }
    }

    // MARK: Itinerary

    private var itinerary: some View {
        ScrollView {
            LazyVStack(spacing: Ta33Spacing.x2) {
                HStack {
                    OverlineLabel("Itinerář")
                    Spacer()
                    Text("Kontroly zvýrazněné")
                        .font(Ta33Font.caption)
                        .foregroundStyle(Ta33Color.fgMuted)
                }
                .padding(.bottom, Ta33Spacing.x1)

                ForEach(state.waypoints, id: \.index) { wp in
                    itineraryRow(wp)
                }
            }
            .padding(.horizontal, Ta33Spacing.x4)
            .padding(.top, Ta33Spacing.x3)
            .padding(.bottom, Ta33Spacing.x4)
        }
        .background(Ta33Color.cream)
    }

    private func itineraryRow(_ wp: RouteWaypoint) -> some View {
        let isKp = wp.kind == .control
        let isEnd = wp.kind == .start || wp.kind == .finish
        let isFinish = wp.kind == .finish
        let active = isKp && highlighted == wp.controlOrdinal?.int32Value

        let kmColor: Color = active ? Ta33Color.fgOnOrange : (isEnd || isKp ? Ta33Color.orange : Ta33Color.fgStrong)
        let nameColor: Color = active ? Ta33Color.fgOnOrange : Ta33Color.fgStrong
        let glyphColor: Color = active ? Ta33Color.fgOnOrange : Ta33Color.fgMuted

        return HStack(spacing: Ta33Spacing.x3) {
            Text(fmtKm(wp.km))
                .font(.system(size: 17, weight: .black))
                .foregroundStyle(kmColor)
                .frame(width: 40, alignment: .trailing)
            if isKp {
                Text(wp.controlOrdinal.map { "\($0.int32Value)" } ?? "")
                    .font(.system(size: 13, weight: .black))
                    .foregroundStyle(Ta33Color.fgOnOrange)
                    .frame(width: 24, height: 24)
                    .background(Ta33Color.kpActiveBg)
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            }
            Text(wp.name)
                .font(.system(size: 14, weight: isKp || isEnd ? .bold : .semibold))
                .foregroundStyle(nameColor)
                .lineLimit(1)
                .truncationMode(.tail)
                .frame(maxWidth: .infinity, alignment: .leading)
            if isFinish {
                Image(systemName: "star.fill")
                    .font(.system(size: 18))
                    .foregroundStyle(active ? Ta33Color.fgOnOrange : Ta33Color.orange)
            } else {
                DirArrowView(dir: wp.direction, size: 20, color: glyphColor)
            }
            MarkBadgeView(mark: wp.mark, markNumber: wp.markNumber, size: 22)
        }
        .padding(.horizontal, Ta33Spacing.x3)
        .padding(.vertical, Ta33Spacing.x2)
        .background(active ? Ta33Color.slate800 : Ta33Color.paper)
        .clipShape(RoundedRectangle(cornerRadius: Ta33Radius.md, style: .continuous))
        .overlay {
            if isKp && !active {
                RoundedRectangle(cornerRadius: Ta33Radius.md, style: .continuous)
                    .stroke(Ta33Color.kpActiveBg.opacity(0.35), lineWidth: 1.5)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture {
            if isKp { onHighlight(active ? nil : wp.controlOrdinal?.int32Value) }
        }
    }

    // MARK: Helpers

    /// Km s českou desetinnou čárkou; celé číslo bez desetin (zrcadlí Android `formatKm`).
    private func fmtKm(_ km: Double) -> String {
        if km.truncatingRemainder(dividingBy: 1) == 0 { return String(Int(km)) }
        return String(format: "%.1f", km).replacingOccurrences(of: ".", with: ",")
    }
}

// MARK: - Previews

private func previewState() -> MapaUiState {
    let itin = RouteCatalog.shared.itineraries.first!
    return MapaUiState(
        routeId: itin.routeId,
        shortId: itin.shortId,
        letter: itin.letter,
        distanceKm: itin.distanceKm,
        ascentMeters: itin.ascentMeters,
        controlsCount: itin.controlsCount,
        waypoints: itin.waypoints,
        highlightedControl: KotlinInt(int: 2),
        canSwitch: true,
        loading: false
    )
}

#Preview("VariantHybrid") {
    MapaHybridView(state: previewState(), onSwitch: {}, onHighlight: { _ in })
}
