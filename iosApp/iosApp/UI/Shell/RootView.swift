import SwiftUI
import Shared

/// Selection model tab baru. Destinace zrcadlí sdílený `TopLevelDestination`;
/// `.scan` je iOS-only akční sentinel pro oddělený search-role tab (otevírá cover,
/// není to samostatná obrazovka). Držíme ho ve Swiftu, sdílený enum zůstává čistý.
private enum TabSelection: Hashable {
    case destination(TopLevelDestination)
    case scan
}

/// Kořenová navigační skořápka iOS aplikace. LOADING → splash; jinak vždy shell s tab barem.
/// Dokud data akce/mapa nejsou stažená (readiness != READY), tab Deník ukazuje `DownloadCardView`
/// a tab Mapa je skrytý; po stažení (READY) se Mapa objeví a Deník/Mapa ukazují normální obsah.
/// Tab bar je **nativní** `TabView` - na iOS 26 ho systém vykreslí jako Liquid Glass
/// (morphing, interaktivní sklo, scroll-edge efekt zdarma, `.tabBarMinimizeBehavior` při scrollu),
/// na iOS 18 jako klasickou lištu. Sdílíme jen navigační model (`TopLevelDestination`), vzhled
/// patří platformě. Scan vstup (FR-09) je při aktivním běhu na iOS 26 oddělená Liquid Glass kapsle
/// přes `Tab(role: .search)` (systém ji vykreslí vpravo, morphuje a minimalizuje s lištou), na
/// iOS 18 overlay FAB. Nad taby žijí i nabídka sběru kontroly (FR-08) a zelená obrazovka Splnění.
struct RootView: View {
    @StateObject private var model = RootModel()
    @StateObject private var scanFlow = ScanFlowModel()
    @StateObject private var prep = PreparationModel() // sdílená download VM (vlastní shell)
    @State private var selection: TabSelection = .destination(RootView.initialTab())
    @State private var showScan = false
    @State private var dismissedControlId: String?

    /// Data akce/mapa stažená → plný obsah + tab Mapa; jinak Deník ukazuje download kartu, Mapa je skrytá.
    private var isReady: Bool { model.app.readiness == .ready }

    var body: some View {
        ZStack {
            switch model.app.readiness {
            case .loading:
                SplashView()
            default:
                // Po LOADING je shell vždy; not-ready řešíme uvnitř (Deník karta + skrytá Mapa).
                readyContent
            }
        }
        .task { await model.observe() }
    }

    // MARK: - Ready content (společné pro obě verze)

    private var readyContent: some View {
        tabShell
            .task { await prep.observe() }
            .onChange(of: model.app.readiness, initial: true) { _, _ in
                // Když se Mapa schová (not-ready) a byla aktivní, přepni zpět na Deník (jinak prázdný výběr).
                if !isReady, selection == .destination(.mapa) {
                    selection = .destination(.denik)
                }
            }
            .task(id: model.app.activeRunId) {
                guard let runId = model.app.activeRunId,
                      let routeId = model.app.activeRouteId else { return }
                scanFlow.bind(runId: runId, routeId: routeId)
                // Reset až po `bind` (které vyčistí `lastCollected`), jinak by mezi-render viděl
                // starý snímek s už vynulovaným `dismissedControlId` a znovu ukázal staré Splnění.
                dismissedControlId = nil
                await scanFlow.observe()
            }
            .onChange(of: model.app.activeRunId) { _, _ in
                showScan = false
            }
            .onChange(of: selection) { oldValue, newValue in
                guard newValue == .scan else { return }
                showScan = true
                selection = oldValue // oldValue je vždy .destination(...) - na .scan nikdy nezůstaneme
            }
            .fullScreenCover(isPresented: $showScan) { scanCover }
            .fullScreenCover(isPresented: splneniBinding) { splneniCover }
    }

    /// Skořápka tab baru. iOS 26: scan je **oddělená search-role kapsle** přímo v `baseTabView`
    /// (systémový Liquid Glass, morph i minimalizace zdarma). iOS 18: scan je overlay FAB
    /// (`legacyScanFab`) vedle klasické lišty. Obě řešení se nikdy nezobrazí současně.
    /// Nepoužíváme `.tabViewBottomAccessory` (dával by pill *nad* lištou, ne vedle ní).
    private var tabShell: some View {
        baseTabView
            .ta33TabBarMinimizeOnScroll()
            .overlay(alignment: .bottomTrailing) { legacyScanFab }
            .safeAreaInset(edge: .bottom) { collectionOffer }
    }

    /// iOS 18 fallback: oranžový scan FAB plovoucí **nad** celošířkovou lištou (jako Android
    /// Scaffold docked FAB) - jen při aktivním běhu. Na iOS 26 řeší scan oddělená search-role
    /// kapsle v `baseTabView`, tady se nevykreslí. Zvednutý nad lištu, ať nepřekrývá tab „Profil".
    @ViewBuilder
    private var legacyScanFab: some View {
        if #unavailable(iOS 26.0), model.app.activeRunId != nil {
            ScanButton(action: { showScan = true })
                .padding(.trailing, Ta33Spacing.x4)
                .padding(.bottom, Self.legacyTabBarHeight + Ta33Spacing.x3)
        }
    }

    /// Standardní výška obsahu iOS tab baru (pt) - o tolik zvedáme iOS 18 scan FAB nad lištu.
    private static let legacyTabBarHeight: CGFloat = 49

    /// Vlastní obsah tabů. `TabView` si tři views drží naživu sám (scroll/stav se zachová
    /// mezi taby) - žádný ruční `ZStack`/opacity juggling. Na iOS 26 přibývá při aktivním běhu
    /// scan jako `Tab(role: .search)` - systém ho vykreslí jako oddělenou Liquid Glass kapsli
    /// vpravo. Scan je akce (ne obrazovka): bez `.searchable` neaktivuje search field a jeho
    /// selection se hned vrací zpět (viz `.onChange(of: selection)` v `readyContent`).
    private var baseTabView: some View {
        TabView(selection: $selection) {
            Tab("Deník", systemImage: "book", value: TabSelection.destination(.denik)) {
                if isReady { DenikView() } else { DownloadCardView(model: prep) }
            }
            if isReady {
                Tab("Mapa", systemImage: "map", value: TabSelection.destination(.mapa)) {
                    MapaView()
                }
            }
            Tab("Profil", systemImage: "person", value: TabSelection.destination(.prehled)) {
                ProfilView()
            }
            if #available(iOS 26.0, *), model.app.activeRunId != nil {
                Tab("Skenovat", systemImage: "qrcode.viewfinder",
                    value: TabSelection.scan, role: .search) {
                    Color.clear // scan nemá vlastní obrazovku; selection se hned vrací
                }
            }
        }
        .tint(Ta33Color.orange)
    }

    // MARK: - Sdílené overlaye

    @ViewBuilder
    private var collectionOffer: some View {
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

    @ViewBuilder
    private var scanCover: some View {
        if let routeId = model.app.activeRouteId {
            ScanView(
                state: scanFlow.timingState,
                onSimulateStart: { scanFlow.simulateStart(routeId) },
                onSimulateFinish: { scanFlow.simulateFinish(routeId) },
                onClose: { showScan = false }
            )
        }
    }

    @ViewBuilder
    private var splneniCover: some View {
        if let collected = scanFlow.lastCollected {
            SplneniView(
                controlName: collected.controlLabel,
                timeText: collected.timeText,
                subtitle: "Kontrola uložena do deníku",
                onClose: { dismissedControlId = collected.controlId }
            )
        }
    }

    /// DEV/TESTING: výchozí tab lze zvolit env proměnnou `TA33_TAB` (denik/mapa/profil) pro
    /// deterministické snímání obrazovek na simulátoru. V produkci se env nenastaví → `.denik`.
    private static func initialTab() -> TopLevelDestination {
        switch ProcessInfo.processInfo.environment["TA33_TAB"] {
        case "mapa": return .mapa
        case "profil": return .prehled
        default: return .denik
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

private extension View {
    /// iOS 26: lišta se minimalizuje při scrollu dolů (a vrátí při scrollu nahoru). Na iOS 18 no-op.
    @ViewBuilder func ta33TabBarMinimizeOnScroll() -> some View {
        if #available(iOS 26.0, *) {
            self.tabBarMinimizeBehavior(.onScrollDown)
        } else {
            self
        }
    }
}

#Preview("RootView") {
    RootView()
}
