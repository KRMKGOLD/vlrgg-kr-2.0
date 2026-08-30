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

        guard let favoriteDataStoreDirectory = try? FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        else {
            fatalError("Unable to initialize favorites storage.")
        }
        let favoriteDataStore = FavoriteDataStore_iosKt.createFavoriteDataStore(
            path: favoriteDataStoreDirectory
                .appendingPathComponent("favorites.preferences_pb")
                .path,
            scope: nil
        )
        graph = AppGraphKt.createAppGraph(
            apiBaseUrl: apiBaseUrl,
            favoriteDataStore: favoriteDataStore
        )
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
