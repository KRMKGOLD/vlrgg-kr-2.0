import Foundation
import SwiftUI
import Shared

private final class AppRuntimeOwner: ObservableObject {
    let graph: any AppGraph

    init(bundle: Bundle = .main) {
        guard
            let apiBaseUrl = bundle.object(forInfoDictionaryKey: "API_BASE_URL") as? String,
            !apiBaseUrl.isEmpty
        else {
            fatalError("API_BASE_URL is missing from Info.plist")
        }

        graph = AppGraphKt.createAppGraph(apiBaseUrl: apiBaseUrl)
    }
}

@main
struct iOSApp: App {
    @StateObject private var runtime = AppRuntimeOwner()

    var body: some Scene {
        WindowGroup {
            ContentView(graph: runtime.graph)
        }
    }
}
