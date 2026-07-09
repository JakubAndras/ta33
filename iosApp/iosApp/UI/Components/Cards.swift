import SwiftUI

/// Slate identity karta („kdo a kdy") — datum + místo + podtitul. Zrcadlí Android `IdentityCard`.
struct IdentityCard: View {
    let date: String
    let place: String
    let sub: String

    var body: some View {
        VStack(alignment: .leading, spacing: Ta33Spacing.x2) {
            Text(date.uppercased())
                .font(Ta33Font.overline)
                .kerning(1.3)
                .foregroundStyle(Ta33Color.fgOnDarkMuted)
            Text(place)
                .font(Ta33Font.display3)
                .foregroundStyle(Ta33Color.fgOnDark)
            Text(sub)
                .font(Ta33Font.small)
                .foregroundStyle(Ta33Color.fgOnDarkMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Ta33Spacing.x6)
        .background(Ta33Color.slate800)
        .clipShape(RoundedRectangle(cornerRadius: Ta33Radius.lg, style: .continuous))
        .shadow(color: .black.opacity(0.12), radius: Ta33Spacing.x2, x: 0, y: Ta33Spacing.x1)
    }
}

/// Bílá „paper" karta — univerzální povrch pro operační obsah. Zrcadlí Android `PaperCard`.
struct PaperCard<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        content
            .padding(Ta33Spacing.x4)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Ta33Color.paper)
            .clipShape(RoundedRectangle(cornerRadius: Ta33Radius.lg, style: .continuous))
            .shadow(color: .black.opacity(0.08), radius: Ta33Spacing.x2, x: 0, y: Ta33Spacing.x1)
    }
}

#Preview("IdentityCard") {
    IdentityCard(
        date: "Sobota 19. 9. 2026",
        place: "Teplice n. Metují",
        sub: "Start 7:00–10:00 · prezentace u sokolovny"
    )
    .padding(Ta33Spacing.x5)
    .background(Ta33Color.cream)
}

#Preview("PaperCard") {
    PaperCard {
        VStack(alignment: .leading, spacing: Ta33Spacing.x2) {
            Text("Obsah karty").font(Ta33Font.h2).foregroundStyle(Ta33Color.fgStrong)
            Text("Vše operační jde sem.").font(Ta33Font.body).foregroundStyle(Ta33Color.fgMuted)
        }
    }
    .padding(Ta33Spacing.x5)
    .background(Ta33Color.cream)
}
