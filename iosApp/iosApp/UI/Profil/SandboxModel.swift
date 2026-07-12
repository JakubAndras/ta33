import Foundation
import Shared

/// DEV / TESTING ONLY (UI-12) — pozoruje sdílený `SandboxViewModel` (SKIE `AsyncSequence`) a vystavuje
/// stav 4 přepínačů Sandbox panelu SwiftUI. Akce delegují na VM, který mění REÁLNÝ stav appky
/// (readiness gate, scan kapsle, Deník, Profil). Používá se jen v DEBUG (viz `#if DEBUG` v `ProfilView`).
@MainActor
final class SandboxModel: ObservableObject {
    private let vm = ViewModelProvider.shared.sandboxViewModel()

    // SKIE nevystavuje no-arg init pro Kotlin data class → sestav počáteční stav explicitně.
    @Published var state: SandboxUiState = SandboxUiState(
        paid: true,
        naTrase: false,
        downloaded: true,
        finished: false,
        runExists: false
    )

    func observe() async {
        for await s in vm.state {
            state = s
        }
    }

    func setPaid(_ on: Bool) { vm.setPaid(on: on) }
    func setNaTrase(_ on: Bool) { vm.setNaTrase(on: on) }
    func setDownloaded(_ on: Bool) { vm.setDownloaded(on: on) }
    func setFinished(_ on: Bool) { vm.setFinished(on: on) }
}
