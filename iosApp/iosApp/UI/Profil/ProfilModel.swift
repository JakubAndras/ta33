import Foundation
import Shared

/// Pozoruje sdílené `OverviewViewModel` + `SettingsViewModel` (FR-10) přes SKIE `AsyncSequence`
/// (`for await`) a vystavuje jejich stav SwiftUI. Zrcadlí vzor `RootModel`/`DenikModel` (ui-02/ui-04).
/// „Hlasové pokyny" nejsou v modelu - jsou lokální `@State` ve `ProfilView` (mock, Etapa 2).
@MainActor
final class ProfilModel: ObservableObject {
    private let overviewVM = ViewModelProvider.shared.overviewViewModel()
    private let settingsVM = ViewModelProvider.shared.settingsViewModel()

    // SKIE nevystavuje no-arg init pro Kotlin data class → sestav počáteční stav explicitně (loading).
    @Published var overview: OverviewUiState = OverviewUiState(
        readiness: .loading,
        contentAvailability: .unknown,
        activeRoute: nil,
        hasActiveRun: false,
        progress: nil,
        syncStatus: .notStarted,
        manifestVersion: nil,
        readyAtMillis: nil,
        loading: true
    )
    @Published var settings: SettingsUiState = SettingsUiState(
        notificationsEnabled: true,
        organizerContact: nil,
        faq: [],
        loading: true
    )

    func observeOverview() async {
        for await state in overviewVM.state {
            overview = state
        }
    }

    func observeSettings() async {
        for await state in settingsVM.state {
            settings = state
        }
    }

    func setNotifications(_ on: Bool) {
        settingsVM.setNotificationsEnabled(enabled: on)
    }
}
