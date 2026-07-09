import Foundation
import Shared

/// Pozoruje sdílené ViewModely (přes SKIE `AsyncSequence`) a vystavuje jejich stav SwiftUI.
/// `AppViewModel` je gate (FR-01), `RunLogViewModel` obsah (FR-04), `RouteDetailViewModel` label (FR-03).
@MainActor
final class DenikModel: ObservableObject {
    private let appVM = ViewModelProvider.shared.appViewModel()
    private let logVM = ViewModelProvider.shared.runLogViewModel()
    private let routeVM = ViewModelProvider.shared.routeDetailViewModel()

    @Published var app: AppUiState = AppUiState(
        readiness: .loading,
        contentAvailability: .unknown,
        activeRouteId: nil,
        activeRunId: nil,
        startDestination: nil
    )
    @Published var log: LogUiState = LogUiState(
        entries: [],
        collectedCount: 0,
        totalCount: 0,
        nextControl: nil,
        finishState: .locked,
        isComplete: false,
        isRunFinished: false,
        loading: true
    )
    @Published var routeLabel: String = "Trasa"

    private var boundRun: String?
    private var boundRoute: String?

    func observe() async {
        for await state in appVM.state {
            app = state
            bindIfNeeded(state)
        }
    }

    func observeLog() async {
        for await state in logVM.state {
            log = state
        }
    }

    func observeRoute() async {
        for await state in routeVM.state {
            routeLabel = label(state)
        }
    }

    private func bindIfNeeded(_ state: AppUiState) {
        guard let run = state.activeRunId, let route = state.activeRouteId else {
            boundRun = nil
            boundRoute = nil
            return
        }
        guard run != boundRun || route != boundRoute else { return }
        boundRun = run
        boundRoute = route
        logVM.bind(runId: run, routeId: route)
        routeVM.bind(routeId: route)
    }

    /// Složí label „<jméno> · <km> km" z RouteDetail; dokud detail nedorazí, vrátí placeholder.
    private func label(_ state: RouteDetailUiState) -> String {
        guard let detail = state.detail else { return "Trasa" }
        let km = detail.distanceKm
        let kmStr: String
        if km.truncatingRemainder(dividingBy: 1) == 0 {
            kmStr = String(Int(km))
        } else {
            kmStr = String(km).replacingOccurrences(of: ".", with: ",")
        }
        return "\(detail.name) · \(kmStr) km"
    }

    func onDownload() {
        // TODO: navigace na Preparation (app-shell) / DownloadViewModel (FR-11).
    }
}
