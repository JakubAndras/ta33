import SwiftUI

/// Mock „odbavovací QR" — deterministický 21×21 pseudo-vzor (jako design `QRGlyph`).
/// NENÍ skenovatelný; jen vizuální placeholder do doby, než přijde reálné generování QR
/// (Etapa 2, FR-13). Barvy z tokenů — [cellColor] tmavé buňky na [background].
struct QrGlyphView: View {
    var cellColor: Color = Ta33Color.slate900
    var background: Color = Ta33Color.paper

    private static let grid = 21

    var body: some View {
        Canvas { context, size in
            context.fill(
                Path(CGRect(origin: .zero, size: size)),
                with: .color(background)
            )
            let n = Self.grid
            let cell = min(size.width, size.height) / CGFloat(n)
            for y in 0..<n {
                for x in 0..<n where Self.isFilled(x: x, y: y) {
                    let rect = CGRect(
                        x: CGFloat(x) * cell,
                        y: CGFloat(y) * cell,
                        width: cell,
                        height: cell
                    )
                    context.fill(Path(rect), with: .color(cellColor))
                }
            }
        }
    }

    /// Reprodukce vzoru z design `QRGlyph`: tři „finder" rohy + jinak deterministický pseudo-šum.
    static func isFilled(x: Int, y: Int) -> Bool {
        let n = grid
        let corner = (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7)
        if corner {
            let cornerInner =
                ((1...5).contains(x) && (1...5).contains(y)) ||
                ((n - 6...n - 2).contains(x) && (1...5).contains(y)) ||
                ((1...5).contains(x) && (n - 6...n - 2).contains(y))
            let cornerCenter =
                ((2...4).contains(x) && (2...4).contains(y)) ||
                ((n - 5...n - 3).contains(x) && (2...4).contains(y)) ||
                ((2...4).contains(x) && (n - 5...n - 3).contains(y))
            return !cornerInner || cornerCenter
        } else {
            return (x * 7 + y * 13 + x * y * 3) % 3 < 1
        }
    }
}

#Preview("QrGlyphView") {
    QrGlyphView()
        .frame(width: 168, height: 168)
        .padding()
}
