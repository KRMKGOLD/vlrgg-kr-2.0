import UIKit
import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    let graph: any AppGraph

    func makeUIViewController(context: Self.Context) -> UIViewController {
        MainViewControllerKt.MainViewController(graph: graph)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    let graph: any AppGraph

    var body: some View {
        ComposeView(graph: graph)
            .ignoresSafeArea()
    }
}
