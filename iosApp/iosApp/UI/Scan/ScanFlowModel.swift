import Foundation
import Shared

/// Snapshot úspěšně sebrané kontroly (FR-08 `JustCollected`), zamražený v okamžiku sběru.
/// Drží label i čas, aby zelené Splnění nekmitalo s živým candidate/časem.
struct CollectedSnapshot: Equatable {
    let controlId: String
    let controlLabel: String
    let timeText: String
}

/// Drží scan flow stav (FR-09 časomíra + FR-08 sběr kontroly) pro `RootView`.
/// Pozoruje sdílené `TimingViewModel` a `ControlCollectionViewModel` (SKIE `AsyncSequence`).
/// Kamera je zatím simulovaná: `simulateStart/Finish` feednou payload do `onQrScanned(raw:)`.
@MainActor
final class ScanFlowModel: ObservableObject {
    private let timing = ViewModelProvider.shared.timingViewModel()
    private let collection = ViewModelProvider.shared.controlCollectionViewModel()

    // SKIE nevystavuje no-arg init pro Kotlin data class → počáteční stav sestav explicitně.
    @Published var timingState: TimingUiState = TimingUiState(
        isRunning: false,
        isFinished: false,
        elapsedMillis: 0,
        elapsedFormatted: "00:00",
        splits: [],
        lastScan: nil
    )
    @Published var collectionState: ControlCollectionUiState = ControlCollectionUiState(
        permissionStatus: .notDetermined,
        candidate: nil,
        isCollecting: false,
        lastResult: nil
    )
    /// Zamražený snímek posledního úspěšného sběru - Splnění se z něj vykresluje, takže je imunní
    /// vůči tomu, že VM po sběru překlopí `candidate` na nil / nabídne mezitím jinou kontrolu.
    @Published var lastCollected: CollectedSnapshot?

    /// Poslední nabízený candidate (držíme pro sestavení snímku v okamžiku sběru).
    private var lastCandidate: CollectionCandidate?
    private var currentRunId: String?

    /// Idempotentní na úrovni VM (jejich `bind` zruší předchozí job). Při změně běhu vyresetuje
    /// per-běh UI stav, ať se nezobrazí zastaralé Splnění z předchozího běhu.
    func bind(runId: String, routeId: String) {
        if currentRunId != runId {
            currentRunId = runId
            lastCandidate = nil
            lastCollected = nil
        }
        timing.bind(runId: runId, routeId: routeId)
        collection.bind(runId: runId, routeId: routeId)
    }

    /// Pozoruje oba stavy souběžně.
    func observe() async {
        async let t: () = observeTiming()
        async let c: () = observeCollection()
        _ = await (t, c)
    }

    private func observeTiming() async {
        for await s in timing.state {
            timingState = s
        }
    }

    private func observeCollection() async {
        for await s in collection.state {
            collectionState = s
            if let candidate = s.candidate {
                lastCandidate = candidate
            }
            // V okamžiku sběru zamrazíme snímek (label + čas). Jednou na controlId.
            if let result = s.lastResult,
               case .justCollected(let jc) = onEnum(of: result),
               lastCollected?.controlId != jc.controlId,
               let control = lastCandidate?.control {
                lastCollected = CollectedSnapshot(
                    controlId: jc.controlId,
                    controlLabel: "KP-\(control.ordinal) · \(control.name)",
                    timeText: timingState.elapsedFormatted
                )
            }
        }
    }

    func simulateStart(_ routeId: String) {
        timing.onQrScanned(raw: Self.startPayload(routeId: routeId))
    }

    func simulateFinish(_ routeId: String) {
        timing.onQrScanned(raw: Self.finishPayload(routeId: routeId))
    }

    func confirmCollect() {
        collection.confirm()
    }

    // MARK: - Simulační QR payloady
    // Zrcadlí `QrTimingConfig` výchozí formát (scheme:keyword[:routeId], routeScoped=false → 3. segment
    // parser ignoruje, přesto ho posíláme jako Android pro parádu). Field-tunable formát žije v Kotlinu.
    private static let scheme = "TA33"
    private static let separator = ":"
    private static let startKeyword = "START"
    private static let finishKeyword = "FINISH"

    private static func startPayload(routeId: String) -> String {
        [scheme, startKeyword, routeId].joined(separator: separator)
    }

    private static func finishPayload(routeId: String) -> String {
        [scheme, finishKeyword, routeId].joined(separator: separator)
    }
}
