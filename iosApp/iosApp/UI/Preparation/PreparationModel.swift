import Foundation
import Shared

/// Pozoruje sdílený `DownloadViewModel` (FR-11) přes SKIE `AsyncSequence` (`for await`)
/// a vystavuje jeho `DownloadUiState` SwiftUI. Zrcadlí vzor `DenikModel`/`PrehledModel` (ui-02/ui-06).
@MainActor
final class PreparationModel: ObservableObject {
    private let vm = ViewModelProvider.shared.downloadViewModel()

    // SKIE nevystavuje no-arg init pro Kotlin data class → sestav počáteční stav explicitně.
    @Published var state: DownloadUiState = DownloadUiState(
        progress: OfflinePackageProgress(items: [], overallStatus: .idle, overallFraction: 0),
        networkPreference: .wifiOnly,
        currentNetworkType: .none,
        blockedByNetwork: false,
        preparation: PreparationState(status: .notStarted, manifestVersion: nil, readyAtMillis: nil)
    )

    func observe() async {
        for await state in vm.state {
            self.state = state
        }
    }

    func start() { vm.start() }
    func pause() { vm.pause() }
    func resume() { vm.resume() }
    func retry() { vm.retry() }

    func setWifiOnly(_ on: Bool) {
        vm.setNetworkPreference(pref: on ? .wifiOnly : .wifiAndCellular)
    }
}
