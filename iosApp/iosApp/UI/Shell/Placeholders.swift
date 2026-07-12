import SwiftUI

/// Splash - zobrazí se, dokud `AppUiState.readiness == .loading`.
/// Nativní iOS splash: značka „TA33" + systémový `ProgressView` na cream pozadí.
struct SplashView: View {
    var body: some View {
        ZStack {
            Ta33Color.cream.ignoresSafeArea()
            VStack(spacing: Ta33Spacing.x6) {
                Text("TA33")
                    .font(Ta33Font.display1)
                    .kerning(0.4)
                    .foregroundStyle(Ta33Color.fgStrong)
                ProgressView()
                    .tint(Ta33Color.orange)
            }
        }
    }
}

/// Stub obrazovky pro taby, které ještě nemají implementaci (Mapa/Přehled).
/// Cream pozadí, centrovaný název tabu + podtitul (výchozí „Připravujeme").
struct StubView: View {
    let title: String
    var subtitle: String = "Připravujeme"

    var body: some View {
        ZStack {
            Ta33Color.cream.ignoresSafeArea()
            VStack(spacing: Ta33Spacing.x3) {
                OverlineLabel(title)
                Text(subtitle)
                    .font(Ta33Font.body)
                    .foregroundStyle(Ta33Color.fgMuted)
            }
        }
    }
}

/// Placeholder pro přípravu dat akce (FR-11 gate - skutečná obrazovka jindy).
/// Zobrazí se pro `readiness == .notReady / .preparing`.
struct PreparationPlaceholder: View {
    var body: some View {
        StubView(title: "Příprava", subtitle: "Příprava dat akce")
    }
}

#Preview("Splash") {
    SplashView()
}

#Preview("Stub") {
    StubView(title: "Mapa")
}

#Preview("Preparation") {
    PreparationPlaceholder()
}
