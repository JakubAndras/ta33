import SwiftUI

/// Scan vstupní bod (FR-09) — prominentní kruhové tlačítko zobrazené jen při aktivním běhu.
/// **Používá se jen jako iOS 18 fallback FAB** (`RootView.legacyScanFab`): solid oranžový kruh
/// vedle klasické lišty s teplým glow. Na iOS 26 scan renderuje systémová oddělená search-role
/// kapsle (viz `RootView.baseTabView`), ne tento view. Ikona `qrcode.viewfinder`.
struct ScanButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "qrcode.viewfinder")
                .font(.system(size: 20, weight: .medium)) // FabBar fabIconPointSize=20, medium
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
