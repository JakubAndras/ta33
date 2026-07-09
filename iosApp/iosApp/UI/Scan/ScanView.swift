import SwiftUI
import Shared

/// Celoobrazovkový QR sken (FR-09) — slate-900 pozadí, oranžový scan rámeček s pulzující linkou.
/// Kamera je zatím zástupná plocha; start/cíl se simuluje tlačítky, která feednou payload do
/// `TimingViewModel.onQrScanned(raw:)`. Poslední výsledek (`TimingUiState.lastScan`) se zobrazí
/// jako hláška pod textem.
///
/// TODO(device): AVCaptureSession + VNBarcodeObservation → onQrScanned(raw)
struct ScanView: View {
    let state: TimingUiState
    let onSimulateStart: () -> Void
    let onSimulateFinish: () -> Void
    let onClose: () -> Void

    var body: some View {
        ZStack {
            Ta33Color.slate900.ignoresSafeArea()

            VStack(spacing: Ta33Spacing.x5) {
                HStack {
                    Spacer()
                    Button(action: onClose) {
                        Image(systemName: "xmark")
                            .font(.system(size: Ta33Spacing.x5, weight: .semibold))
                            .foregroundStyle(Ta33Color.fgOnDark)
                    }
                    .accessibilityLabel("Zavřít")
                }

                Spacer()

                ScanFrame()

                Text("Namiř na QR kontroly")
                    .font(Ta33Font.display3)
                    .foregroundStyle(Ta33Color.fgOnDark)
                    .multilineTextAlignment(.center)

                Text("Funguje offline · čas se uloží z telefonu")
                    .font(Ta33Font.body)
                    .foregroundStyle(Ta33Color.fgOnDarkMuted)
                    .multilineTextAlignment(.center)

                if let message = Self.scanResultMessage(state.lastScan) {
                    Text(message)
                        .font(Ta33Font.h3)
                        .foregroundStyle(Ta33Color.orange)
                        .multilineTextAlignment(.center)
                }

                Spacer()

                VStack(spacing: Ta33Spacing.x3) {
                    PrimaryButton("Simulovat start QR", action: onSimulateStart)
                    OutlineButton("Simulovat cíl QR", action: onSimulateFinish)
                }
            }
            .padding(Ta33Spacing.x5)
        }
    }

    /// Mapuje výsledek skenu (FR-09) na lokalizovanou hlášku. Null = zatím nic naskenováno.
    static func scanResultMessage(_ result: ScanTimingResult?) -> String? {
        guard let result else { return nil }
        switch onEnum(of: result) {
        case .started: return "Start zaznamenán"
        case .finished: return "Cíl zaznamenán"
        case .alreadyStarted: return "Start už byl zaznamenán"
        case .alreadyFinished: return "Cíl už byl zaznamenán"
        case .finishBeforeStart: return "Nejdřív naskenuj start"
        case .wrongRoute: return "QR je z jiné trasy"
        case .notATimingQr: return "Tohle není QR kontroly"
        case .runNotFound: return "Běh nenalezen"
        }
    }
}

/// Oranžové L-rohy (SF `viewfinder`) + vodorovná pulzující linka (1.6s loop).
private struct ScanFrame: View {
    @State private var pulsing = false

    var body: some View {
        ZStack {
            Image(systemName: "viewfinder")
                .font(.system(size: Ta33Spacing.x10 * 2, weight: .thin))
                .foregroundStyle(Ta33Color.orange)

            Rectangle()
                .fill(Ta33Color.orange)
                .frame(width: Ta33Spacing.x10 + Ta33Spacing.x8, height: Ta33Spacing.x1 / 2)
                .opacity(pulsing ? 1.0 : 0.5)
                .shadow(color: Ta33Color.orange.opacity(0.7), radius: Ta33Spacing.x2)
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 1.6).repeatForever(autoreverses: true)) {
                pulsing = true
            }
        }
    }
}

#Preview("ScanView") {
    ScanView(
        state: TimingUiState(
            isRunning: false,
            isFinished: false,
            elapsedMillis: 0,
            elapsedFormatted: "00:00",
            splits: [],
            lastScan: nil
        ),
        onSimulateStart: {},
        onSimulateFinish: {},
        onClose: {}
    )
}

#Preview("ScanView — start zaznamenán") {
    ScanView(
        state: TimingUiState(
            isRunning: true,
            isFinished: false,
            elapsedMillis: 0,
            elapsedFormatted: "00:00",
            splits: [],
            lastScan: ScanTimingResultStarted(startedAtMillis: 0)
        ),
        onSimulateStart: {},
        onSimulateFinish: {},
        onClose: {}
    )
}
