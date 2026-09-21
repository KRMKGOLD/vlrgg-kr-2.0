import Foundation
import FirebaseCore
import FirebaseCrashlytics
import SwiftUI
import Shared

private final class AppRuntimeOwner: ObservableObject {
    let graph: any AppGraph
    let aboutPlatform: any AboutPlatform

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
        aboutPlatform = IosAboutPlatform(bundle: bundle)
    }
}

@main
struct iOSApp: App {
    @StateObject private var runtime: AppRuntimeOwner

    init() {
        let bundle = Bundle.main
        if bundle.object(forInfoDictionaryKey: "FIREBASE_CRASHLYTICS_ENABLED") as? Bool == true {
            FirebaseApp.configure()
            let crashlytics = Crashlytics.crashlytics()
#if DEBUG
            let arguments = ProcessInfo.processInfo.arguments
            if arguments.contains("--firebase-crashlytics-disable-collection") {
                crashlytics.setCrashlyticsCollectionEnabled(false)
            } else {
                crashlytics.setCrashlyticsCollectionEnabled(true)
                if arguments.contains("--firebase-crashlytics-test-crash") {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                        fatalError("Firebase Crashlytics validation crash")
                    }
                }
            }
            NSLog("[CrashlyticsValidation] collectionEnabled=%@", crashlytics.isCrashlyticsCollectionEnabled() ? "true" : "false")
#else
            crashlytics.setCrashlyticsCollectionEnabled(true)
#endif
        }
        _runtime = StateObject(wrappedValue: AppRuntimeOwner(bundle: bundle))
    }

    var body: some Scene {
        WindowGroup {
            ContentView(graph: runtime.graph, aboutPlatform: runtime.aboutPlatform)
        }
    }
}

private final class IosAboutPlatform: NSObject, AboutPlatform {
    private let bundle: Bundle

    init(bundle: Bundle) {
        self.bundle = bundle
    }

    var buildVersion: String? {
        let shortVersion = bundle.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String
        let build = bundle.object(forInfoDictionaryKey: "CFBundleVersion") as? String
        let values = [shortVersion, build].compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        return values.isEmpty ? nil : values.joined(separator: " (") + (values.count > 1 ? ")" : "")
    }

    func openUrl(url: String, onResult: @escaping (KotlinBoolean) -> Void) {
        guard let destination = URL(string: url), UIApplication.shared.canOpenURL(destination) else {
            onResult(KotlinBoolean(bool: false))
            return
        }
        UIApplication.shared.open(destination, options: [:]) { success in
            onResult(KotlinBoolean(bool: success))
        }
    }
}
