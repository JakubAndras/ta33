import SwiftUI

/// Statistický chip - slate-100 výplň, velké display číslo + malý overline label. Zrcadlí Android `StatChip`.
struct StatChip: View {
    let value: String
    let label: String

    var body: some View {
        VStack(alignment: .leading, spacing: Ta33Spacing.x1) {
            Text(value)
                .font(Ta33Font.display3)
                .foregroundStyle(Ta33Color.fgStrong)
            Text(label.uppercased())
                .font(Ta33Font.overline)
                .kerning(1.0)
                .foregroundStyle(Ta33Color.fgMuted)
        }
        .padding(Ta33Spacing.x3)
        .background(Ta33Color.slate100)
        .clipShape(RoundedRectangle(cornerRadius: Ta33Radius.md, style: .continuous))
    }
}

#Preview("StatChip") {
    HStack(spacing: Ta33Spacing.x3) {
        StatChip(value: "2/5", label: "Kontrol")
        StatChip(value: "14,1", label: "Km ujito")
        StatChip(value: "01:44", label: "Čas")
    }
    .padding(Ta33Spacing.x5)
    .background(Ta33Color.cream)
}
