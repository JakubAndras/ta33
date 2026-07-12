import SwiftUI
import Shared

/// Směrová šipka (`DirArrow`) — kreslená nativním SwiftUI `Canvas`, škálovaná z návrhového
/// viewportu 24×24 (stroke 2.4, round cap/join). Základní up/down/left/right (a straight) je šipka
/// nahoru orotovaná; `leftUp` a `leftRight` jsou dedikované dvouhlavé glyfy. Zrcadlí Android `DirArrow`.
struct DirArrowView: View {
    let dir: TurnDirection
    var size: CGFloat = 24
    var color: Color = Ta33Color.fgStrong

    var body: some View {
        Canvas { ctx, canvasSize in
            let u = min(canvasSize.width, canvasSize.height) / 24.0
            let style = StrokeStyle(lineWidth: 2.4 * u, lineCap: .round, lineJoin: .round)
            func p(_ build: (inout Path) -> Void) -> Path { var path = Path(); build(&path); return path }
            func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * u, y: y * u) }

            switch dir {
            case .leftUp:
                let left = p { path in
                    path.move(to: pt(4, 12)); path.addLine(to: pt(14, 12))
                    path.move(to: pt(8, 8)); path.addLine(to: pt(4, 12)); path.addLine(to: pt(8, 16))
                }
                let up = p { path in
                    path.move(to: pt(18, 20)); path.addLine(to: pt(18, 8))
                    path.move(to: pt(14, 12)); path.addLine(to: pt(18, 8)); path.addLine(to: pt(22, 12))
                }
                ctx.stroke(left, with: .color(color), style: style)
                ctx.stroke(up, with: .color(color), style: style)
            case .leftRight:
                let arrow = p { path in
                    path.move(to: pt(3, 12)); path.addLine(to: pt(21, 12))
                    path.move(to: pt(7, 8)); path.addLine(to: pt(3, 12)); path.addLine(to: pt(7, 16))
                    path.move(to: pt(17, 8)); path.addLine(to: pt(21, 12)); path.addLine(to: pt(17, 16))
                }
                ctx.stroke(arrow, with: .color(color), style: style)
            default:
                let rot: Angle
                switch dir {
                case .right: rot = .degrees(90)
                case .down: rot = .degrees(180)
                case .left: rot = .degrees(270)
                default: rot = .degrees(0) // up, straight
                }
                let arrow = p { path in
                    path.move(to: pt(12, 20)); path.addLine(to: pt(12, 4))
                    path.move(to: pt(7, 9)); path.addLine(to: pt(12, 4)); path.addLine(to: pt(17, 9))
                }
                let center = CGPoint(x: canvasSize.width / 2, y: canvasSize.height / 2)
                var rotated = ctx
                rotated.translateBy(x: center.x, y: center.y)
                rotated.rotate(by: rot)
                rotated.translateBy(x: -center.x, y: -center.y)
                rotated.stroke(arrow, with: .color(color), style: style)
            }
        }
        .frame(width: size, height: size)
    }
}

#Preview("DirArrow") {
    HStack(spacing: Ta33Spacing.x3) {
        DirArrowView(dir: .up)
        DirArrowView(dir: .right)
        DirArrowView(dir: .down)
        DirArrowView(dir: .left)
        DirArrowView(dir: .leftUp)
        DirArrowView(dir: .leftRight)
        DirArrowView(dir: .straight)
    }
    .padding(Ta33Spacing.x5)
    .background(Ta33Color.creamDeep)
}
