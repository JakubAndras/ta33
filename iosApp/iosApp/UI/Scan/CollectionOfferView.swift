import SwiftUI
import Shared

/// Spodní nabídka sběru kontroly v dosahu (FR-08). Ukáže se, když
/// `ControlCollectionViewModel` vystaví `candidate`. „Sebrat" volá `confirm()`;
/// během zápisu (`isCollecting`) je místo tlačítka spinner. `lastResult` slouží jen
/// pro nekritické hlášky pokusu (Už sebráno / Mimo dosah); úspěch (`JustCollected`)
/// řeší celoobrazovkové Splnění jinde.
struct CollectionOfferView: View {
    let candidate: CollectionCandidate
    let isCollecting: Bool
    var lastResult: CollectionOutcome?
    let onCollect: () -> Void

    var body: some View {
        PaperCard {
            VStack(alignment: .leading, spacing: Ta33Spacing.x2) {
                OverlineLabel("Kontrola v dosahu")
                Text("KP-\(candidate.control.ordinal) · \(candidate.control.name)")
                    .font(Ta33Font.h2)
                    .foregroundStyle(Ta33Color.fgStrong)
                Text("\(Int(candidate.distanceMeters.rounded())) m")
                    .font(Ta33Font.body)
                    .foregroundStyle(Ta33Color.fgMuted)

                if isCollecting {
                    HStack {
                        Spacer()
                        ProgressView().tint(Ta33Color.orange)
                        Spacer()
                    }
                    .frame(minHeight: Ta33Spacing.x9)
                    .padding(.top, Ta33Spacing.x2)
                } else {
                    PrimaryButton("Sebrat", action: onCollect)
                        .padding(.top, Ta33Spacing.x2)
                }

                if let message = Self.collectResultMessage(lastResult) {
                    Text(message)
                        .font(Ta33Font.body)
                        .foregroundStyle(Ta33Color.fgMuted)
                }
            }
        }
    }

    /// Nekritická hláška neúspěšného pokusu o sběr. Null = nic k zobrazení (nebo úspěch).
    static func collectResultMessage(_ result: CollectionOutcome?) -> String? {
        guard let result else { return nil }
        switch onEnum(of: result) {
        case .alreadyCollected: return "Už sebráno"
        case .outOfRange: return "Mimo dosah"
        case .justCollected, .unknownControl: return nil
        }
    }
}

private let previewCandidate = CollectionCandidate(
    control: ControlPoint_(
        id: "kp-02",
        routeId: "TA33",
        ordinal: 2,
        name: "Sloní pramen",
        location: GeoPoint(latitude: 50.6, longitude: 16.1),
        radiusMeters: 50.0
    ),
    distanceMeters: 34.0,
    accuracyMeters: 8.0,
    atLocation: GeoPoint(latitude: 50.6, longitude: 16.1)
)

#Preview("CollectionOfferView") {
    ZStack {
        Ta33Color.cream.ignoresSafeArea()
        CollectionOfferView(
            candidate: previewCandidate,
            isCollecting: false,
            onCollect: {}
        )
        .padding(Ta33Spacing.x5)
    }
}

#Preview("CollectionOfferView — sbírá se") {
    ZStack {
        Ta33Color.cream.ignoresSafeArea()
        CollectionOfferView(
            candidate: previewCandidate,
            isCollecting: true,
            onCollect: {}
        )
        .padding(Ta33Spacing.x5)
    }
}

#Preview("CollectionOfferView — mimo dosah") {
    ZStack {
        Ta33Color.cream.ignoresSafeArea()
        CollectionOfferView(
            candidate: previewCandidate,
            isCollecting: false,
            lastResult: CollectionOutcomeOutOfRange(),
            onCollect: {}
        )
        .padding(Ta33Spacing.x5)
    }
}
