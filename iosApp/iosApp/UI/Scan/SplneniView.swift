import SwiftUI

/// Celoobrazovková zelená obrazovka úspěchu (FR-08 `JustCollected`): fajfka v kruhu,
/// název kontroly a čas. „Pokračovat na trase" zavře cover (`onClose`).
struct SplneniView: View {
    let controlName: String
    let timeText: String
    let subtitle: String
    let onClose: () -> Void

    @State private var checkShown = false

    var body: some View {
        ZStack {
            Ta33Color.success.ignoresSafeArea()

            VStack(spacing: Ta33Spacing.x5) {
                Spacer()

                CheckBadge(shown: checkShown)

                OverlineLabel("KP · splněno", color: Ta33Color.fgOnDark)

                Text(controlName)
                    .font(Ta33Font.display3)
                    .foregroundStyle(Ta33Color.fgOnDark)
                    .multilineTextAlignment(.center)

                Text(timeText)
                    .font(Ta33Font.display1)
                    .foregroundStyle(Ta33Color.fgOnDark)
                    .multilineTextAlignment(.center)

                Text(subtitle)
                    .font(Ta33Font.body)
                    .foregroundStyle(Ta33Color.fgOnDarkMuted)
                    .multilineTextAlignment(.center)

                Spacer()

                PrimaryButton("Pokračovat na trase", action: onClose)
            }
            .padding(Ta33Spacing.x5)
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.32)) { checkShown = true }
        }
    }
}

/// Bílý kruh s fajfkou; jemné scale-in.
private struct CheckBadge: View {
    let shown: Bool

    var body: some View {
        Image(systemName: "checkmark")
            .font(.system(size: Ta33Spacing.x8, weight: .bold))
            .foregroundStyle(Ta33Color.success)
            .frame(width: Ta33Spacing.x10 + Ta33Spacing.x8, height: Ta33Spacing.x10 + Ta33Spacing.x8)
            .background(Ta33Color.fgOnDark, in: Circle())
            .scaleEffect(shown ? 1.0 : 0.6)
    }
}

#Preview("SplneniView") {
    SplneniView(
        controlName: "KP-2 · Sloní pramen",
        timeText: "01:44",
        subtitle: "Kontrola uložena do deníku",
        onClose: {}
    )
}
