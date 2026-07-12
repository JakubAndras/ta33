import Foundation
import Shared

/// Pozoruje sdílený `DenikViewModel` (přes SKIE `AsyncSequence`) a vystavuje `DenikUiState` SwiftUI.
/// Kombinuje route katalog (RD-00) se stavem běhu; přepínač tras deleguje na `toggle()`.
@MainActor
final class DenikModel: ObservableObject {
    private let vm = ViewModelProvider.shared.denikViewModel()

    /// `nil` dokud nedorazí první stav → obrazovka ukazuje loading.
    @Published var state: DenikUiState?

    func observe() async {
        for await state in vm.state {
            self.state = state
        }
    }

    func toggle() {
        vm.toggle()
    }
}
