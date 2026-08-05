import SwiftUI
import Shared

@main
struct iOSApp: App {
    private let appGraph = AppGraphKt.createAppGraph()

    var body: some Scene {
        WindowGroup {
            ContentView(graph: appGraph)
        }
    }
}
