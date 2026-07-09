import SwiftUI

extension Color {
    /// Hex literal → Color, e.g. `Color(hex: 0xF76A0E)`.
    init(hex: UInt32) {
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: 1.0)
    }
}

/// TA33 barevné tokeny — 1:1 z design systému (`colors_and_type.css`).
/// Zrcadlí `Ta33Palette` + `Ta33Colors` na Androidu. Neber hex přímo v UI, ber přes tyto tokeny.
enum Ta33Color {
    // Brand — orange
    static let orange50 = Color(hex: 0xFFF1E2)
    static let orange100 = Color(hex: 0xFCDEC1)
    static let orange200 = Color(hex: 0xFCC081)
    static let orange300 = Color(hex: 0xFBA452)
    static let orange400 = Color(hex: 0xF8852A)
    static let orange = Color(hex: 0xF76A0E) // PRIMARY
    static let orange600 = Color(hex: 0xC45607)
    static let orange700 = Color(hex: 0x974303)

    // Dark — slate
    static let slate900 = Color(hex: 0x15202B)
    static let slate800 = Color(hex: 0x1C2A36)
    static let slate700 = Color(hex: 0x2A3A48)
    static let slate600 = Color(hex: 0x3E5160)
    static let slate500 = Color(hex: 0x5A6C7A)
    static let slate400 = Color(hex: 0x8492A0)
    static let slate300 = Color(hex: 0xB6BFC8)
    static let slate200 = Color(hex: 0xD2D9DE)
    static let slate100 = Color(hex: 0xE6EAEC)
    static let slate50 = Color(hex: 0xF1F3F4)

    // Surfaces
    static let cream = Color(hex: 0xF7F2EA)
    static let creamDeep = Color(hex: 0xEFE7D7)
    static let paper = Color(hex: 0xFFFFFF)

    // Map
    static let mapTile = Color(hex: 0xDEE7DC)
    static let mapGrid = Color(hex: 0xC9D3C5)

    // Sky
    static let skyTop = Color(hex: 0x4F87A4)
    static let skyBottom = Color(hex: 0x79A4BC)

    // Semantic
    static let success = Color(hex: 0x1FA85A)
    static let successTint = Color(hex: 0xD9F3E3)
    static let warning = Color(hex: 0xE8A92A)
    static let warningTint = Color(hex: 0xFBE9C2)
    static let error = Color(hex: 0xD63A2F)
    static let errorTint = Color(hex: 0xF8D9D5)
    static let info = Color(hex: 0x2E6FB5)
    static let infoTint = Color(hex: 0xD6E5F4)

    // Control-point states
    static let kpLockedBg = slate200
    static let kpLockedFg = slate500
    static let kpActiveBg = orange
    static let kpActiveFg = Color.white
    static let kpDoneBg = success
    static let kpDoneFg = Color.white
    static let kpFinishBg = slate800
    static let kpFinishFg = Color.white

    // Foreground role
    static let fgStrong = slate900
    static let fgDefault = slate800
    static let fgMuted = slate500
    static let fgFaint = slate400
    static let fgOnDark = cream
    static let fgOnDarkMuted = slate300
    static let fgOnOrange = Color.white
    static let fgLink = orange700
}
