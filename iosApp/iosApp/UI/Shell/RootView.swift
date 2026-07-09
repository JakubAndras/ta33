import SwiftUI
import Shared

/// Kořenová navigační skořápka iOS aplikace. Gate na `AppUiState.readiness`:
/// LOADING → splash, NOT_READY/PREPARING → příprava, READY → nativní `TabView`
/// (Deník / Mapa / Přehled). Tab bar je nativní iOS (liquid glass na iOS 26) — ne klon Android pillu.
/// Scan vstup (FR-09) je overlay zobrazený jen při aktivním běhu.
struct RootView: View {
    @StateObject private var model = RootModel()
    @State private var tab: TopLevelDestination = .denik

    var body: some View {
        ZStack {
            switch model.app.readiness {
            case .loading:
                SplashView()
            case .notReady, .preparing:
                PreparationPlaceholder()
            case .ready:
                TabView(selection: $tab) {
                    DenikView()
                        .tabItem { Label("Deník", systemImage: "book") }
                        .tag(TopLevelDestination.denik)
                    StubView(title: "Mapa")
                        .tabItem { Label("Mapa", systemImage: "map") }
                        .tag(TopLevelDestination.mapa)
                    StubView(title: "Přehled")
                        .tabItem { Label("Přehled", systemImage: "person") }
                        .tag(TopLevelDestination.prehled)
                }
                .tint(Ta33Color.orange)
                .overlay(alignment: .bottomTrailing) {
                    if model.app.activeRunId != nil {
                        ScanButton(action: model.onScan)
                            .padding(Ta33Spacing.x5)
                    }
                }
            default:
                SplashView()
            }
        }
        .task { await model.observe() }
    }
}

#Preview("RootView") {
    RootView()
}
