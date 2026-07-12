import Foundation

/// Formátování pro tab „Profil" (čeština). Zrcadlí Android `formatKm`.

/// Vzdálenost v km s desetinnou čárkou: `33,2`; celé číslo bez desetin: `33`.
func kmLabel(_ km: Double) -> String {
    if km.truncatingRemainder(dividingBy: 1) == 0 {
        return String(Int(km))
    }
    return String(km).replacingOccurrences(of: ".", with: ",")
}
