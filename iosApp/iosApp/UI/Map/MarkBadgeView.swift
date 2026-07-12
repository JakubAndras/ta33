import SwiftUI
import Shared

/// KČT turistická značka (`MarkBadge`): čtyři KČT značky jsou bílý čtverec s barevným pruhem;
/// `vlastní` je oranžový kosočtverec; `cyklo` je žlutá plaketka s číslem trasy.
///
/// Barvy berou tokeny, kde existují (modrá → info, červená → error, vlastní → orange). KČT
/// zelená/žlutá a cyklo plaketka jsou oficiální barvy značení bez tokenu → strukturální literály
/// (jako mapová geometrie). Zrcadlí Android `MarkBadge`.
struct MarkBadgeView: View {
    let mark: TrailMark
    var markNumber: String?
    var size: CGFloat = 24

    var body: some View {
        switch mark {
        case .cyklo:
            cykloPlate
        case .vlastni:
            ownDiamond
        default:
            kctSquare
        }
    }

    private var kctSquare: some View {
        VStack(spacing: 0) {
            Color.clear.frame(maxHeight: .infinity)
            bandColor.frame(height: size * 0.38)
            Color.clear.frame(maxHeight: .infinity)
        }
        .frame(width: size, height: size)
        .background(Ta33Color.paper)
        .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 4, style: .continuous)
                .stroke(Ta33Color.slate300, lineWidth: 1.5)
        }
    }

    private var ownDiamond: some View {
        RoundedRectangle(cornerRadius: 3, style: .continuous)
            .fill(Ta33Color.orange)
            .frame(width: size * 0.62, height: size * 0.62)
            .rotationEffect(.degrees(45))
            .frame(width: size, height: size)
    }

    private var cykloPlate: some View {
        Text(markNumber ?? "")
            .font(.system(size: 11, weight: .heavy))
            .foregroundStyle(Self.cykloText)
            .padding(.horizontal, 5)
            .frame(minWidth: size, minHeight: size)
            .background(Self.cykloFill)
            .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .stroke(Self.cykloBorder, lineWidth: 1.5)
            }
    }

    /// modrá → info, červená → error (tokeny); zelená/žlutá jsou barvy značení (literály).
    private var bandColor: Color {
        switch mark {
        case .modra: return Ta33Color.info
        case .cervena: return Ta33Color.error
        case .zelena: return Self.kctGreen
        case .zluta: return Self.kctYellow
        default: return Ta33Color.info
        }
    }

    // KČT oficiální barvy značení (bez tokenu) + cyklo plaketka - strukturální literály.
    private static let kctGreen = Color(hex: 0x2E9E4F)
    private static let kctYellow = Color(hex: 0xF1C40F)
    private static let cykloFill = Color(hex: 0xF1C40F)
    private static let cykloBorder = Color(hex: 0xC99E06)
    private static let cykloText = Color(hex: 0x3A2E00)
}

#Preview("MarkBadge") {
    HStack(spacing: Ta33Spacing.x3) {
        MarkBadgeView(mark: .modra)
        MarkBadgeView(mark: .zelena)
        MarkBadgeView(mark: .zluta)
        MarkBadgeView(mark: .cervena)
        MarkBadgeView(mark: .vlastni)
        MarkBadgeView(mark: .cyklo, markNumber: "4036")
        MarkBadgeView(mark: .cyklo)
    }
    .padding(Ta33Spacing.x5)
    .background(Ta33Color.creamDeep)
}
