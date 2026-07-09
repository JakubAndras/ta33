import SwiftUI
import Shared

/// Kořenová navigační skořápka iOS aplikace. Gate na `AppUiState.readiness`:
/// LOADING → splash, NOT_READY/PREPARING → příprava, READY → nativní `TabView`
/// (Deník / Mapa / Přehled). Tab bar je nativní iOS (liquid glass na iOS 26) — ne klon Android pillu.
/// Scan vstup (FR-09) je overlay zobrazený jen při aktivním běhu; otevírá `ScanView` (`.fullScreenCover`).
/// Nad taby žijí i nabídka sběru kontroly (FR-08) a zelená obrazovka Splnění.
struct RootView: View {
    @StateObject private var model = RootModel()
    @StateObject private var scanFlow = ScanFlowModel()
    @State private var tab: TopLevelDestination = .denik
    @State private var showScan = false
    @State private var dismissedControlId: String?

    var body: some View {
        ZStack {
            switch model.app.readiness {
            case .loading:
                SplashView()
            case .notReady, .preparing:
                PreparationView()
            case .ready:
                readyContent
            default:
                SplashView()
            }
        }
        .task { await model.observe() }
    }

    private var readyContent: some View {
        TabView(selection: $tab) {
            DenikView()
                .tabItem { Label("Deník", systemImage: "book") }
                .tag(TopLevelDestination.denik)
            StubView(title: "Mapa")
                .tabItem { Label("Mapa", systemImage: "map") }
                .tag(TopLevelDestination.mapa)
            PrehledView()
                .tabItem { Label("Přehled", systemImage: "person") }
                .tag(TopLevelDestination.prehled)
        }
        .tint(Ta33Color.orange)
        .overlay(alignment: .bottomTrailing) {
            if model.app.activeRunId != nil {
                ScanButton(action: { showScan = true })
                    .padding(Ta33Spacing.x5)
            }
        }
        .safeAreaInset(edge: .bottom) {
            if let candidate = scanFlow.collectionState.candidate {
                CollectionOfferView(
                    candidate: candidate,
                    isCollecting: scanFlow.collectionState.isCollecting,
                    lastResult: scanFlow.collectionState.lastResult,
                    onCollect: scanFlow.confirmCollect
                )
                .padding(.horizontal, Ta33Spacing.x5)
                .padding(.bottom, Ta33Spacing.x4)
            }
        }
        .task(id: model.app.activeRunId) {
            guard let runId = model.app.activeRunId,
                  let routeId = model.app.activeRouteId else { return }
            scanFlow.bind(runId: runId, routeId: routeId)
            await scanFlow.observe()
        }
        .onChange(of: model.app.activeRunId) { _, _ in
            showScan = false
            dismissedControlId = nil
        }
        .fullScreenCover(isPresented: $showScan) {
            if let routeId = model.app.activeRouteId {
                ScanView(
                    state: scanFlow.timingState,
                    onSimulateStart: { scanFlow.simulateStart(routeId) },
                    onSimulateFinish: { scanFlow.simulateFinish(routeId) },
                    onClose: { showScan = false }
                )
            }
        }
        .fullScreenCover(isPresented: splneniBinding) {
            if let collected = scanFlow.lastCollected {
                SplneniView(
                    controlName: collected.controlLabel,
                    timeText: collected.timeText,
                    subtitle: "Kontrola uložena do deníku",
                    onClose: { dismissedControlId = collected.controlId }
                )
            }
        }
    }

    /// Splnění ukážeme jen pro zamražený sběr, který uživatel ještě nezavřel.
    private var splneniBinding: Binding<Bool> {
        Binding(
            get: {
                guard let collected = scanFlow.lastCollected else { return false }
                return collected.controlId != dismissedControlId
            },
            set: { presented in
                if !presented { dismissedControlId = scanFlow.lastCollected?.controlId }
            }
        )
    }
}

#Preview("RootView") {
    RootView()
}
