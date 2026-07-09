import SwiftUI
import Shared

/// Formátování pro tab „Přehled" (čeština). Zrcadlí Android `formatKm` + `packageStatus*`.

/// Vzdálenost v km s desetinnou čárkou: `33,2`; celé číslo bez desetin: `33`.
func kmLabel(_ km: Double) -> String {
    if km.truncatingRemainder(dividingBy: 1) == 0 {
        return String(Int(km))
    }
    return String(km).replacingOccurrences(of: ".", with: ",")
}

/// Stav offline balíčku dat akce (Etapa 1) → český label.
func packageStatusLabel(_ status: PreparationStatus) -> String {
    switch status {
    case .notStarted: return "Nestaženo"
    case .preparing: return "Stahuje se"
    case .ready: return "Staženo"
    case .error: return "Chyba"
    default: return "Nestaženo"
    }
}

/// Barevná role stavu balíčku: success / warning / error.
func packageStatusColor(_ status: PreparationStatus) -> Color {
    switch status {
    case .ready: return Ta33Color.success
    case .error: return Ta33Color.error
    case .notStarted, .preparing: return Ta33Color.warning
    default: return Ta33Color.warning
    }
}
