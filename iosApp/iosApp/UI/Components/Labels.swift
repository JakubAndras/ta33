import SwiftUI

/// Overline label — UPPER, kerning, tlumená barva. Zrcadlí Android `Overline`.
struct OverlineLabel: View {
    let text: String
    var color: Color

    init(_ text: String, color: Color = Ta33Color.fgMuted) {
        self.text = text
        self.color = color
    }

    var body: some View {
        Text(text.uppercased())
            .font(Ta33Font.overline)
            .kerning(1.3)
            .foregroundStyle(color)
    }
}

#Preview("OverlineLabel") {
    VStack(alignment: .leading, spacing: Ta33Spacing.x3) {
        OverlineLabel("Startovní číslo")
        OverlineLabel("Hotovo")
    }
    .padding(Ta33Spacing.x5)
    .background(Ta33Color.cream)
}
