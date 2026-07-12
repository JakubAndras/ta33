import SwiftUI
import Shared

/// Uzel timeline „Kontroly na trase" (38×38, radius md):
/// START/FINISH → slate-800 se SF Symbolem map/star; CONTROL done → success, next → orange (+ glow),
/// upcoming → paper se slate-200 obrysem. Číslo kontroly barvené dle stavu. Zrcadlí Android `TimelineNode`.
struct TimelineNodeView: View {
    let status: StopStatus
    let kind: WaypointKind
    let ordinal: Int32?

    private var isEnd: Bool { kind == .start || kind == .finish }
    private var isNext: Bool { status == .next && !isEnd }
    private var isUpcoming: Bool { status == .upcoming && !isEnd }

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: Ta33Radius.md, style: .continuous)
                .fill(background)
            content
        }
        .frame(width: 38, height: 38)
        .overlay {
            if isUpcoming {
                RoundedRectangle(cornerRadius: Ta33Radius.md, style: .continuous)
                    .stroke(Ta33Color.kpLockedBg, lineWidth: 2)
            }
        }
        .shadow(color: isNext ? Ta33Color.orange.opacity(0.35) : .clear, radius: isNext ? 7 : 0)
    }

    @ViewBuilder private var content: some View {
        if isEnd {
            Image(systemName: kind == .finish ? "star.fill" : "map")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(Ta33Color.fgOnOrange)
        } else if let ordinal {
            Text("\(ordinal)")
                .font(.system(size: 18, weight: .black))
                .foregroundStyle(isUpcoming ? Ta33Color.fgFaint : Ta33Color.fgOnOrange)
        }
    }

    private var background: Color {
        if isEnd { return Ta33Color.slate800 }
        switch status {
        case .done: return Ta33Color.success
        case .next: return Ta33Color.orange
        default: return Ta33Color.paper
        }
    }
}

#Preview("TimelineNode") {
    HStack(spacing: Ta33Spacing.x3) {
        TimelineNodeView(status: .done, kind: .start, ordinal: nil)
        TimelineNodeView(status: .done, kind: .control, ordinal: 1)
        TimelineNodeView(status: .next, kind: .control, ordinal: 2)
        TimelineNodeView(status: .upcoming, kind: .control, ordinal: 3)
        TimelineNodeView(status: .upcoming, kind: .finish, ordinal: nil)
    }
    .padding(Ta33Spacing.x5)
    .background(Ta33Color.cream)
}
