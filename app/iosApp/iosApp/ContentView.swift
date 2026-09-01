import UIKit
import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    let graph: any AppGraph
    let aboutPlatform: any AboutPlatform

    func makeUIViewController(context: Self.Context) -> UIViewController {
        MainViewControllerKt.MainViewController(graph: graph, aboutPlatform: aboutPlatform)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    let graph: any AppGraph
    let aboutPlatform: any AboutPlatform

    var body: some View {
        ComposeView(graph: graph, aboutPlatform: aboutPlatform)
            .ignoresSafeArea()
    }
}
