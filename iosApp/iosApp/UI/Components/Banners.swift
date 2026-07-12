import SwiftUI

/// Offline banner - warning-tint pozadí, ⚡ (SF Symbol `bolt.fill`) + text. Zrcadlí Android `OfflineBanner`.
struct OfflineBanner: View {
    var body: some View {
        HStack(spacing: Ta33Spacing.x2) {
            Image(systemName: "bolt.fill")
                .font(.system(size: Ta33Spacing.x4))
                .foregroundStyle(Ta33Color.warning)
            Text("Offline režim — záznamy se uloží lokálně")
                .font(Ta33Font.bodyStrong)
                .foregroundStyle(Ta33Color.fgDefault)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Ta33Spacing.x3)
        .background(Ta33Color.warningTint)
        .clipShape(RoundedRectangle(cornerRadius: Ta33Radius.md, style: .continuous))
    }
}

#Preview("OfflineBanner") {
    OfflineBanner()
        .padding(Ta33Spacing.x5)
        .background(Ta33Color.cream)
}
