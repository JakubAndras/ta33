import SwiftUI

/// Scan vstupní bod (FR-09) — prominentní kruhové tlačítko zobrazené jen při aktivním běhu.
/// iOS-idiomatické (ne Material FAB): kruh v `Ta33Color.orange` s teplým glow (`shadow-scan-glow`),
/// SF Symbol `qrcode.viewfinder`, ikona v `fgOnOrange`.
struct ScanButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "qrcode.viewfinder")
                .font(.system(size: 28, weight: .semibold))
                .foregroundStyle(Ta33Color.fgOnOrange)
                .frame(width: Ta33Spacing.x9, height: Ta33Spacing.x9)
                .background(Ta33Color.orange, in: Circle())
                .shadow(color: Ta33Color.orange.opacity(0.35), radius: Ta33Spacing.x4, x: 0, y: Ta33Spacing.x2)
        }
        .accessibilityLabel("Skenovat QR kontrolu")
    }
}

#Preview("ScanButton") {
    ZStack {
        Ta33Color.cream.ignoresSafeArea()
        ScanButton(action: {})
    }
}
