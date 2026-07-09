import SwiftUI
import Shared

/// Jeden řádek deníku: 56pt stavový swatch + titul (bodyStrong) + podtitul (small).
/// Barvy swatche a chování řádku odvozeny z [state] (a [isFinish]) přes `Ta33Color`.
/// Zrcadlí Android `KPRow`; ikony jsou SF Symbols (`checkmark`, `star.fill`).
struct KPRow: View {
    let ordinal: Int32
    let title: String
    let subtitle: String
    let state: ControlPointState
    var isFinish: Bool = false

    var body: some View {
        HStack(spacing: Ta33Spacing.x3) {
            swatch
            VStack(alignment: .leading, spacing: Ta33Spacing.x1) {
                Text(title)
                    .font(Ta33Font.bodyStrong)
                    .foregroundStyle(Ta33Color.fgStrong)
                    .lineLimit(1)
                Text(subtitle)
                    .font(Ta33Font.small)
                    .foregroundStyle(Ta33Color.fgMuted)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .padding(Ta33Spacing.x3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Ta33Color.paper)
        .clipShape(RoundedRectangle(cornerRadius: Ta33Radius.lg, style: .continuous))
        .shadow(color: .black.opacity(0.06), radius: Ta33Spacing.x1, x: 0, y: Ta33Spacing.x1)
        .opacity(state == .locked ? 0.55 : 1.0)
    }

    @ViewBuilder private var swatch: some View {
        let style = swatchStyle
        ZStack {
            RoundedRectangle(cornerRadius: Ta33Radius.md, style: .continuous)
                .fill(style.bg)
            swatchContent(fg: style.fg)
        }
        .frame(width: Ta33Spacing.x9, height: Ta33Spacing.x9)
        .shadow(
            color: style.glow ? Ta33Color.orange.opacity(0.45) : .clear,
            radius: style.glow ? Ta33Spacing.x2 : 0,
            x: 0,
            y: style.glow ? Ta33Spacing.x1 : 0
        )
    }

    private var swatchStyle: (bg: Color, fg: Color, glow: Bool) {
        switch state {
        case .done: return (Ta33Color.kpDoneBg, Ta33Color.kpDoneFg, false)
        case .active: return (Ta33Color.kpActiveBg, Ta33Color.kpActiveFg, true)
        case .finish: return (Ta33Color.kpFinishBg, Ta33Color.kpFinishFg, false)
        case .locked: return (Ta33Color.kpLockedBg, Ta33Color.kpLockedFg, false)
        }
    }

    @ViewBuilder private func swatchContent(fg: Color) -> some View {
        if state == .done {
            Image(systemName: "checkmark")
                .font(.system(size: Ta33Spacing.x6, weight: .bold))
                .foregroundStyle(fg)
        } else if isFinish || state == .finish {
            Image(systemName: "star.fill")
                .font(.system(size: Ta33Spacing.x5))
                .foregroundStyle(fg)
        } else {
            Text("\(ordinal)")
                .font(Ta33Font.display3)
                .foregroundStyle(fg)
        }
    }
}

#Preview("KPRow states") {
    VStack(spacing: Ta33Spacing.x3) {
        KPRow(ordinal: 1, title: "KP-01 · Start", subtitle: "Splněno · 08:14", state: .done)
        KPRow(ordinal: 2, title: "KP-02 · Sloní pramen", subtitle: "Další", state: .active)
        KPRow(ordinal: 3, title: "KP-03 · Vyhlídka", subtitle: "Zamčeno", state: .locked)
        KPRow(ordinal: 5, title: "Cíl · Adršpach", subtitle: "Zamčeno", state: .locked, isFinish: true)
        KPRow(ordinal: 5, title: "Cíl · Adršpach", subtitle: "Splněno · 11:02", state: .finish, isFinish: true)
    }
    .padding(Ta33Spacing.x5)
    .background(Ta33Color.cream)
}
