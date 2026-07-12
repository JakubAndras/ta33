import SwiftUI
import Shared
import UserNotifications

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

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

/// FR-11b: presents the „download complete" local notification as a banner even when the app is in
/// the foreground (default iOS behavior would suppress it).
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }
}
