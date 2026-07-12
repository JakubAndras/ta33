import SwiftUI

/// Primární CTA - orange pill, full-width, UPPER text s teplým glow. Zrcadlí Android `PrimaryButton`.
struct PrimaryButton: View {
    let title: String
    let action: () -> Void

    init(_ title: String, action: @escaping () -> Void) {
        self.title = title
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Text(title.uppercased())
                .font(Ta33Font.button)
                .kerning(0.6)
                .foregroundStyle(Ta33Color.fgOnOrange)
                .frame(maxWidth: .infinity, minHeight: Ta33Spacing.x9)
        }
        .background(Ta33Color.orange)
        .clipShape(Capsule())
        .shadow(color: Ta33Color.orange.opacity(0.35), radius: Ta33Spacing.x4, x: 0, y: Ta33Spacing.x2)
    }
}

/// Sekundární akce - orange 2px obrys, transparentní výplň. Zrcadlí Android `OutlineButton`.
struct OutlineButton: View {
    let title: String
    let action: () -> Void

    init(_ title: String, action: @escaping () -> Void) {
        self.title = title
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Text(title.uppercased())
                .font(Ta33Font.button)
                .kerning(0.6)
                .foregroundStyle(Ta33Color.orange)
                .frame(maxWidth: .infinity, minHeight: Ta33Spacing.x9)
        }
        .overlay(Capsule().stroke(Ta33Color.orange, lineWidth: 2))
    }
}

#Preview("Buttons") {
    VStack(spacing: Ta33Spacing.x4) {
        PrimaryButton("Stáhnout data akce · 84 MB") {}
        OutlineButton("Stáhnout dlaždice") {}
    }
    .padding(Ta33Spacing.x5)
    .background(Ta33Color.cream)
}
