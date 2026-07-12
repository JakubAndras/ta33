import SwiftUI
import Shared

/// Deník (RD-01) - kanonický `VariantPrehled`. Pozoruje sdílený `DenikViewModel` přes `DenikModel`.
struct DenikView: View {
    @StateObject private var model = DenikModel()

    var body: some View {
        Group {
            if let state = model.state, !state.loading {
                DenikVariantPrehledView(state: state, onSwitch: model.toggle)
            } else {
                DenikLoadingView()
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        // Cream vyplní i pod plovoucí lištu (iOS 26), ale obsah respektuje spodní safe area
        // lišty - poslední (cíl) uzel se pak scrolluje nad lištu a neprosvítá zpod skla.
        .background(Ta33Color.cream.ignoresSafeArea())
        .task { await model.observe() }
    }
}
