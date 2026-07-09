import SwiftUI
import Shared

struct ContentView: View {
    var body: some View {
        VStack(spacing: Ta33Spacing.x4) {
            Text("TA33")
                .font(Ta33Font.display1)
                .foregroundStyle(Ta33Color.fgStrong)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Ta33Color.cream)
    }
}
