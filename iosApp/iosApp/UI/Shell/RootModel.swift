import Foundation
import Shared

/// Drží gate stav aplikace pro `RootView`. Pozoruje sdílený `AppViewModel` (FR-01)
/// přes SKIE `AsyncSequence` (`for await`). Zrcadlí vzor `DenikModel` (ui-02).
@MainActor
final class RootModel: ObservableObject {
    private let appVM = ViewModelProvider.shared.appViewModel()

    // SKIE nevystavuje no-arg init pro Kotlin data class → sestav počáteční stav explicitně (LOADING).
    @Published var app: AppUiState = AppUiState(
        readiness: .loading,
        contentAvailability: .unknown,
        activeRouteId: nil,
        activeRunId: nil,
        startDestination: nil
    )

    func observe() async {
        for await state in appVM.state {
            app = state
        }
    }

    func onScan() {
        // TODO: FR-09 scan flow (start/cíl QR) — vstupní bod hoisted z shellu.
    }
}
