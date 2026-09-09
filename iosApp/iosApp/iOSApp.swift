import ComposeApp
import SwiftUI

@main
struct BertAtlasApp: App {
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.keyboard)
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                MainViewControllerKt.resumeApp()
            } else {
                MainViewControllerKt.pauseApp()
            }
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
