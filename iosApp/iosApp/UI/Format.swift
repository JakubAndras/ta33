import Foundation

/// Formátovací utility pro Deník (čeština). Zrcadlí `formatClock` na Androidu.
enum Ta33Format {
    /// Epoch millis → „HH:mm" v lokální zóně zařízení.
    static func clock(_ millis: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(millis) / 1000)
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: date)
    }
}
