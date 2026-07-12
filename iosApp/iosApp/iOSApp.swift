import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        KoinKt.doInitKoin()
        KoinKt.seedDevDataIfEmpty() // DEV/TESTING only (gated by DEV_SEED_ENABLED)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
