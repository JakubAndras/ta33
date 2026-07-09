import SwiftUI

/// TA33 typografická škála — zrcadlí `Ta33Type` na Androidu.
///
/// TODO(fonty): design používá **Big Shoulders Display** (display/button) a **Inter** (body).
/// Zatím systémový fallback přes `.system`. Po přidání TTF do bundlu (+ `UIAppFonts` v Info.plist)
/// nahraď `.system(size:weight:)` za `.custom("BigShouldersDisplay-...", size:)`.
enum Ta33Font {
    static let display1 = Font.system(size: 40, weight: .black)
    static let display2 = Font.system(size: 32, weight: .black)
    static let display3 = Font.system(size: 22, weight: .heavy)

    static let h1 = Font.system(size: 24, weight: .bold)
    static let h2 = Font.system(size: 20, weight: .bold)
    static let h3 = Font.system(size: 17, weight: .semibold)

    static let body = Font.system(size: 16, weight: .regular)
    static let bodyStrong = Font.system(size: 16, weight: .semibold)
    static let small = Font.system(size: 14, weight: .regular)
    static let caption = Font.system(size: 12, weight: .semibold)
    static let overline = Font.system(size: 13, weight: .bold)
    static let button = Font.system(size: 16, weight: .heavy)
}
