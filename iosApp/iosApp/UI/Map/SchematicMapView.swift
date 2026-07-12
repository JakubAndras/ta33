import SwiftUI
import Shared

/// Schematická mapa (`SchematicMap`): stylizovaná náhrada reálné mapy — gradient parchment→sage,
/// jemné vrstevnice + les/voda blobs, výrazná smyčka trasy (bílý podklad + barva trasy, TA50 dashed),
/// start/cíl marker a klikací piny kontrol (orotovaný kosočtverec s číslem). Piny se křížově
/// zvýrazňují s itinerářem přes `highlightedControl`. Zrcadlí Android `SchematicMap`.
///
/// Geometrie (smyčka, vrstevnice, souřadnice pinů) je 1:1 z návrhového viewportu 360×460 (literály).
/// POZN.: Toto je schematická mapa; reálná MapLibre mapa + živá GPS (FR-06) je follow-up.
struct SchematicMapView: View {
    let controlsCount: Int
    let routeColor: Color
    let dashed: Bool
    let highlightedControl: Int32?
    let onPin: (Int32) -> Void

    private var pins: [MapPin] { controlsCount >= 6 ? SchematicGeometry.pins6 : SchematicGeometry.pins5 }

    var body: some View {
        GeometryReader { geo in
            ZStack {
                LinearGradient(
                    colors: [Ta33Color.creamDeep, Ta33Color.mapTile],
                    startPoint: .topLeading, endPoint: .bottomTrailing
                )
                Canvas { ctx, size in draw(ctx, size) }
                ForEach(pins, id: \.n) { pin in
                    let cx = geo.size.width * (pin.x / 360.0)
                    let cy = geo.size.height * (pin.y / 460.0)
                    Button { onPin(pin.n) } label: {
                        ControlPinView(number: pin.n, active: highlightedControl == pin.n)
                    }
                    .buttonStyle(.plain)
                    .position(x: cx, y: cy - ControlPinView.pinSize / 2)
                }
            }
        }
    }

    private func draw(_ ctx: GraphicsContext, _ size: CGSize) {
        let sx = size.width / 360.0
        let sy = size.height / 460.0
        let s = min(sx, sy)

        // faint contour lines
        for spec in SchematicGeometry.contours {
            ctx.stroke(SchematicGeometry.path(spec, sx, sy), with: .color(Ta33Color.mapGrid.opacity(0.7)),
                       style: StrokeStyle(lineWidth: 1.2 * s, lineCap: .round, lineJoin: .round))
        }
        // forest + water blobs
        for spec in SchematicGeometry.forestBlobs {
            ctx.fill(SchematicGeometry.path(spec, sx, sy), with: .color(Ta33Color.mapGrid.opacity(0.5)))
        }
        ctx.fill(SchematicGeometry.path(SchematicGeometry.waterBlob, sx, sy), with: .color(Ta33Color.infoTint.opacity(0.5)))

        // route loop: white underlay + route colour (dashed for TA50)
        let loop = SchematicGeometry.path(SchematicGeometry.loop, sx, sy)
        ctx.stroke(loop, with: .color(Ta33Color.paper.opacity(0.85)),
                   style: StrokeStyle(lineWidth: 9 * s, lineCap: .round, lineJoin: .round))
        ctx.stroke(loop, with: .color(routeColor),
                   style: StrokeStyle(lineWidth: 5 * s, lineCap: .round, lineJoin: .round,
                                      dash: dashed ? [14 * s, 8 * s] : []))

        // start / cíl marker (bottom-centre of the loop)
        let mc = CGPoint(x: 196 * sx, y: 300 * sy)
        ctx.fill(Path(ellipseIn: CGRect(x: mc.x - 9 * s, y: mc.y - 9 * s, width: 18 * s, height: 18 * s)),
                 with: .color(Ta33Color.paper))
        ctx.stroke(Path(ellipseIn: CGRect(x: mc.x - 9 * s, y: mc.y - 9 * s, width: 18 * s, height: 18 * s)),
                   with: .color(Ta33Color.slate800), lineWidth: 3 * s)
        ctx.fill(Path(ellipseIn: CGRect(x: mc.x - 3.4 * s, y: mc.y - 3.4 * s, width: 6.8 * s, height: 6.8 * s)),
                 with: .color(Ta33Color.slate800))
    }
}

/// Pin kontroly — orotovaný kosočtverec (tři zaoblené rohy + ostrý spodní) s číslem.
private struct ControlPinView: View {
    let number: Int32
    let active: Bool
    static let pinSize: CGFloat = 30

    var body: some View {
        let shape = UnevenRoundedRectangle(
            topLeadingRadius: Self.pinSize / 2,
            bottomLeadingRadius: 2,
            bottomTrailingRadius: Self.pinSize / 2,
            topTrailingRadius: Self.pinSize / 2,
            style: .continuous
        )
        return ZStack {
            shape.fill(active ? Ta33Color.slate800 : Ta33Color.kpActiveBg)
            shape.stroke(Ta33Color.fgOnOrange, lineWidth: 2.5)
        }
        .frame(width: Self.pinSize, height: Self.pinSize)
        .rotationEffect(.degrees(45))
        .overlay {
            Text("\(number)")
                .font(.system(size: 15, weight: .black))
                .foregroundStyle(Ta33Color.fgOnOrange)
        }
        .scaleEffect(active ? 1.18 : 1)
        .shadow(color: Ta33Color.slate900.opacity(0.28), radius: 3, x: 0, y: 3)
    }
}

// MARK: - Geometry (1:1 z návrhového viewportu 360×460)

private struct MapPin { let x: CGFloat; let y: CGFloat; let n: Int32 }

private enum MapCmd {
    case m(CGFloat, CGFloat)
    case c(CGFloat, CGFloat, CGFloat, CGFloat, CGFloat, CGFloat)
    case q(CGFloat, CGFloat, CGFloat, CGFloat)
    case z
}

private enum SchematicGeometry {
    static func path(_ spec: [MapCmd], _ sx: CGFloat, _ sy: CGFloat) -> Path {
        var p = Path()
        for cmd in spec {
            switch cmd {
            case let .m(x, y): p.move(to: CGPoint(x: x * sx, y: y * sy))
            case let .c(x1, y1, x2, y2, x, y):
                p.addCurve(to: CGPoint(x: x * sx, y: y * sy),
                           control1: CGPoint(x: x1 * sx, y: y1 * sy),
                           control2: CGPoint(x: x2 * sx, y: y2 * sy))
            case let .q(x1, y1, x, y):
                p.addQuadCurve(to: CGPoint(x: x * sx, y: y * sy), control: CGPoint(x: x1 * sx, y: y1 * sy))
            case .z: p.closeSubpath()
            }
        }
        return p
    }

    static let pins5: [MapPin] = [
        MapPin(x: 150, y: 150, n: 1), MapPin(x: 120, y: 250, n: 2), MapPin(x: 96, y: 118, n: 3),
        MapPin(x: 150, y: 310, n: 4), MapPin(x: 244, y: 312, n: 5),
    ]
    static let pins6: [MapPin] = [
        MapPin(x: 150, y: 150, n: 1), MapPin(x: 120, y: 250, n: 2), MapPin(x: 96, y: 118, n: 3),
        MapPin(x: 262, y: 150, n: 4), MapPin(x: 236, y: 118, n: 5), MapPin(x: 244, y: 312, n: 6),
    ]

    static let loop: [MapCmd] = [
        .m(196, 300),
        .c(150, 250, 120, 200, 150, 150),
        .c(175, 110, 120, 96, 96, 118),
        .c(70, 142, 96, 176, 132, 176),
        .c(176, 176, 150, 230, 120, 250),
        .c(96, 268, 120, 300, 150, 310),
        .c(130, 340, 160, 372, 196, 356),
        .c(232, 372, 262, 340, 244, 312),
        .c(288, 300, 300, 250, 270, 224),
        .c(306, 196, 300, 150, 262, 150),
        .c(300, 120, 262, 96, 236, 118),
        .c(262, 156, 226, 176, 210, 210),
        .c(244, 240, 232, 280, 196, 300),
        .z,
    ]

    static let contours: [[MapCmd]] = [
        [.m(-20, 90), .c(60, 60, 120, 120, 200, 96), .c(280, 72, 340, 120, 400, 96)],
        [.m(-20, 150), .c(70, 120, 140, 180, 210, 150), .c(290, 120, 340, 170, 400, 150)],
        [.m(-20, 220), .c(60, 190, 130, 250, 220, 220), .c(300, 195, 350, 240, 400, 220)],
        [.m(-20, 300), .c(80, 275, 150, 330, 230, 300), .c(310, 275, 360, 320, 400, 300)],
        [.m(-20, 380), .c(70, 355, 140, 410, 220, 380), .c(300, 355, 360, 400, 400, 380)],
    ]

    static let forestBlobs: [[MapCmd]] = [
        [.m(40, 60), .q(70, 40, 100, 62), .q(120, 90, 90, 108), .q(55, 112, 40, 90), .z],
        [.m(285, 340), .q(320, 330, 330, 360), .q(322, 392, 292, 388), .q(272, 366, 285, 340), .z],
    ]
    static let waterBlob: [MapCmd] = [
        .m(300, 70), .q(330, 74, 328, 100), .q(312, 118, 292, 106), .q(286, 82, 300, 70), .z,
    ]
}

#Preview("SchematicMap") {
    SchematicMapView(controlsCount: 5, routeColor: Ta33Color.orange, dashed: false,
                     highlightedControl: 2, onPin: { _ in })
        .frame(height: 452)
        .background(Ta33Color.cream)
}
