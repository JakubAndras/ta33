import SwiftUI
import Shared

/// Deník — gate stavů řízený `AppUiState.readiness`. Pozoruje sdílené ViewModely přes `DenikModel`.
struct DenikView: View {
    @StateObject private var model = DenikModel()

    var body: some View {
        ZStack {
            Ta33Color.cream.ignoresSafeArea()
            switch model.app.readiness {
            case .loading:
                DenikLoadingView()
            case .ready where model.app.activeRunId != nil && model.app.activeRouteId != nil:
                DenikOnRouteView(log: model.log, routeLabel: model.routeLabel, offline: true)
            default:
                DenikBeforeView(onDownload: model.onDownload)
            }
        }
        .task { await model.observe() }
        .task { await model.observeLog() }
        .task { await model.observeRoute() }
    }
}
