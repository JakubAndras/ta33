import Foundation
import Shared

/// Pozoruje sdílený `MapaViewModel` (přes SKIE `AsyncSequence`) a vystavuje `MapaUiState` SwiftUI.
/// Kombinuje route katalog (RD-00) s aktivní trasou; přepínač deleguje na `toggle()`, křížové
/// zvýraznění pin↔řádek na `highlight(controlOrdinal:)`.
///
/// Oddělené od FR-06 `MapModel`/`MapViewModel` (ten je pro budoucí reálnou MapLibre mapu).
@MainActor
final class MapaModel: ObservableObject {
    private let vm = ViewModelProvider.shared.mapaViewModel()

    /// `nil` dokud nedorazí první stav → obrazovka ukazuje loading.
    @Published var state: MapaUiState?

    func observe() async {
        for await state in vm.state {
            self.state = state
        }
    }

    func toggle() {
        vm.toggle()
    }

    /// Zvýrazní/odznačí kontrolu (dle ordinálu). `nil` zvýraznění zruší.
    func highlight(_ ordinal: Int32?) {
        vm.highlight(controlOrdinal: ordinal.map { KotlinInt(int: $0) })
    }
}
