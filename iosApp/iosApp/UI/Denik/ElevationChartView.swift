import SwiftUI
import Shared

/// Výškový profil (`ElevProfile`): area + line v barvě trasy, min/peak callouty (vodítko + kroužek +
/// „462 / 727" + „m n. m.") a km osa s gridlines. Kreslené nativním SwiftUI `Canvas`, škálované
/// z návrhového viewportu 324×172. Zrcadlí Android `ElevationChart`.
struct ElevationChartView: View {
    let profile: ElevationProfile
    let routeColor: Color

    var body: some View {
        Canvas { context, size in
            let pts = profile.pointsNormalized.map { $0.doubleValue }
            guard pts.count >= 2 else { return }

            let sx = size.width / 324.0
            let sy = size.height / 172.0
            func px(_ x: Double) -> Double { x * sx }
            func py(_ y: Double) -> Double { y * sy }

            let x0 = 12.0, x1 = 312.0, top = 66.0, bot = 130.0, base = 142.0
            let n = pts.count
            func vx(_ i: Int) -> Double { x0 + Double(i) * ((x1 - x0) / Double(n - 1)) }
            func vy(_ v: Double) -> Double { bot - v * (bot - top) }

            let kmTotal = max(profile.kmTotal, 0.0001)
            let ticks = profile.tickKm.map { Int($0.int32Value) }
            func tx(_ km: Int) -> Double { x0 + (Double(km) / kmTotal) * (x1 - x0) }
            let unit = sx

            // gridlines
            for km in ticks {
                var p = Path()
                p.move(to: CGPoint(x: px(tx(km)), y: py(54)))
                p.addLine(to: CGPoint(x: px(tx(km)), y: py(base)))
                context.stroke(p, with: .color(Ta33Color.slate100), lineWidth: unit)
            }

            // area fill
            var area = Path()
            area.move(to: CGPoint(x: px(x0), y: py(base)))
            for i in 0..<n { area.addLine(to: CGPoint(x: px(vx(i)), y: py(vy(pts[i])))) }
            area.addLine(to: CGPoint(x: px(x1), y: py(base)))
            area.closeSubpath()
            context.fill(area, with: .color(routeColor.opacity(0.12)))

            // line
            var line = Path()
            for i in 0..<n {
                let pt = CGPoint(x: px(vx(i)), y: py(vy(pts[i])))
                if i == 0 { line.move(to: pt) } else { line.addLine(to: pt) }
            }
            context.stroke(
                line,
                with: .color(routeColor),
                style: StrokeStyle(lineWidth: 2.4 * unit, lineCap: .round, lineJoin: .round)
            )

            // callouts: low at index 0, high at the peak index
            let peakIdx = pts.indices.max(by: { pts[$0] < pts[$1] }) ?? 0
            drawCallout(context, x: px(vx(0)), yPoint: py(vy(pts[0])), yLabel: py(34),
                        meters: Int(profile.lowMeters), unit: unit)
            drawCallout(context, x: px(vx(peakIdx)), yPoint: py(vy(pts[peakIdx])), yLabel: py(20),
                        meters: Int(profile.highMeters), unit: unit)

            // km axis labels - font scales with the viewport, like every geometric element
            for km in ticks {
                let text = Text("\(km) km").font(.system(size: 12 * unit, weight: .black)).foregroundColor(Ta33Color.fgFaint)
                context.draw(text, at: CGPoint(x: px(tx(km)), y: py(158)), anchor: .center)
            }
        }
        .aspectRatio(324.0 / 172.0, contentMode: .fit)
    }

    private func drawCallout(_ context: GraphicsContext, x: Double, yPoint: Double, yLabel: Double, meters: Int, unit: Double) {
        var leader = Path()
        leader.move(to: CGPoint(x: x, y: yPoint))
        leader.addLine(to: CGPoint(x: x, y: yLabel + 6 * unit))
        context.stroke(leader, with: .color(Ta33Color.slate300), lineWidth: unit)

        let ring = CGRect(x: x - 4 * unit, y: yPoint - 4 * unit, width: 8 * unit, height: 8 * unit)
        context.fill(Path(ellipseIn: ring), with: .color(Ta33Color.paper))
        context.stroke(Path(ellipseIn: ring), with: .color(routeColor), lineWidth: 2 * unit)

        let num = Text("\(meters)").font(.system(size: 15 * unit, weight: .black)).foregroundColor(Ta33Color.fgStrong)
        context.draw(num, at: CGPoint(x: x, y: yLabel - 5 * unit), anchor: .center)
        let u = Text("m n. m.").font(.system(size: 8 * unit, weight: .semibold)).foregroundColor(Ta33Color.fgMuted)
        context.draw(u, at: CGPoint(x: x, y: yLabel + 5 * unit), anchor: .center)
    }
}

#Preview("ElevationChart") {
    ElevationChartView(
        profile: RouteCatalog.shared.itineraries.first!.elevation,
        routeColor: Ta33Color.orange
    )
    .padding(Ta33Spacing.x5)
    .background(Ta33Color.cream)
}
