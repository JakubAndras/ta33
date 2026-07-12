import SwiftUI

/// TA33 event hero karta - brandová hlavička Teplicko-adršpašského pochodu: datum + odznak „33 KM",
/// wordmark „TA33" (33 oranžově), název akce, místo + start, a silueta adršpašských skalních věží dole.
/// Slate identita + oranžový akcent. Zrcadlí Android `IdentityCard`.
struct IdentityCard: View {
    let date: String
    let place: String
    let sub: String

    var body: some View {
        ZStack(alignment: .bottom) {
            sunriseGlow
            rockRidge

            VStack(alignment: .leading, spacing: Ta33Spacing.x2) {
                HStack(alignment: .center) {
                    Text(date.uppercased())
                        .font(Ta33Font.overline)
                        .kerning(1.3)
                        .foregroundStyle(Ta33Color.fgOnDarkMuted)
                    Spacer(minLength: Ta33Spacing.x3)
                    distanceBadge
                }

                (Text("TA").foregroundStyle(Ta33Color.fgOnDark)
                    + Text("33").foregroundStyle(Ta33Color.orange))
                    .font(Ta33Font.display1)
                    .kerning(0.5)

                Text("Teplicko-adršpašský pochod".uppercased())
                    .font(Ta33Font.caption)
                    .kerning(1.6)
                    .foregroundStyle(Ta33Color.fgOnDarkMuted)

                VStack(alignment: .leading, spacing: Ta33Spacing.x1) {
                    Text(place)
                        .font(Ta33Font.h3)
                        .foregroundStyle(Ta33Color.fgOnDark)
                    Text(sub)
                        .font(Ta33Font.small)
                        .foregroundStyle(Ta33Color.fgOnDarkMuted)
                }
                .padding(.top, Ta33Spacing.x2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(Ta33Spacing.x6)
            .padding(.bottom, Ta33Spacing.x9) // místo pro horizont skalních věží
        }
        .background(
            LinearGradient(
                colors: [Ta33Color.slate900, Ta33Color.slate800],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .clipShape(RoundedRectangle(cornerRadius: Ta33Radius.xl, style: .continuous))
        .shadow(color: .black.opacity(0.12), radius: Ta33Spacing.x2, x: 0, y: Ta33Spacing.x1)
    }

    private var distanceBadge: some View {
        Text("33 KM")
            .font(Ta33Font.caption)
            .kerning(0.8)
            .foregroundStyle(Ta33Color.fgOnOrange)
            .padding(.horizontal, Ta33Spacing.x3)
            .padding(.vertical, Ta33Spacing.x1)
            .background(Ta33Color.orange, in: Capsule())
    }

    /// Jemný oranžový „východ slunce" za skalními věžemi (dawn glow u horizontu).
    private var sunriseGlow: some View {
        RadialGradient(
            colors: [Ta33Color.orange.opacity(0.28), Ta33Color.orange.opacity(0.0)],
            center: .bottom,
            startRadius: 0,
            endRadius: 190
        )
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        .blendMode(.plusLighter)
        .allowsHitTesting(false)
    }

    /// Nepravidelná silueta skalních věží (Adršpašsko-teplické skály) podél spodní hrany karty.
    private var rockRidge: some View {
        RockRidge()
            .fill(Ta33Color.slate700)
            .frame(maxWidth: .infinity)
            .frame(height: Ta33Spacing.x10) // ~72pt vysoký horizont
    }
}

/// Silueta shluku pískovcových věží: úzké nepravidelné pilíře různých výšek a šířek sdílející základnu.
private struct RockRidge: Shape {
    /// (šířkový poměr, výškový poměr 0..1) pro každou věž zleva doprava.
    private let towers: [(w: CGFloat, h: CGFloat)] = [
        (1.0, 0.42), (0.65, 0.68), (1.35, 0.52), (0.55, 0.9), (0.9, 0.64),
        (1.15, 0.46), (0.5, 0.82), (1.45, 0.6), (0.8, 1.0), (0.6, 0.55),
        (1.2, 0.74), (0.7, 0.48), (1.0, 0.86), (0.85, 0.58), (1.3, 0.7),
    ]

    func path(in rect: CGRect) -> Path {
        var p = Path()
        let gap: CGFloat = 2.5
        let totalWeight = towers.reduce(0) { $0 + $1.w }
        let usableW = rect.width - gap * CGFloat(towers.count - 1)
        let scale = usableW / totalWeight

        p.move(to: CGPoint(x: rect.minX, y: rect.maxY))
        var x = rect.minX
        for (i, t) in towers.enumerated() {
            let w = t.w * scale
            let topY = rect.maxY - rect.height * t.h
            let cap = min(w * 0.5, 5)
            p.addLine(to: CGPoint(x: x, y: topY + cap))
            p.addQuadCurve(to: CGPoint(x: x + cap, y: topY), control: CGPoint(x: x, y: topY))
            p.addLine(to: CGPoint(x: x + w - cap, y: topY))
            p.addQuadCurve(to: CGPoint(x: x + w, y: topY + cap), control: CGPoint(x: x + w, y: topY))
            p.addLine(to: CGPoint(x: x + w, y: rect.maxY))
            x += w
            if i < towers.count - 1 {
                p.addLine(to: CGPoint(x: x + gap, y: rect.maxY)) // úzká štěrbina k základně
                x += gap
            }
        }
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        p.closeSubpath()
        return p
    }
}

/// Bílá „paper" karta - univerzální povrch pro operační obsah. Zrcadlí Android `PaperCard`.
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
