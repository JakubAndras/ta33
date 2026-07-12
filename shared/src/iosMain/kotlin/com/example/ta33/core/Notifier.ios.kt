package com.example.ta33.core

import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS [Notifier] over UNUserNotificationCenter. Posting an unauthorized notification is silently
 * dropped by the system, so no explicit permission check is needed here (authorization is requested
 * from the SwiftUI layer). Foreground banner presentation is handled by the UN delegate in iOSApp.
 */
class IosNotifier : Notifier {
    override fun notifyDownloadComplete(title: String, body: String) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(UNNotificationSound.defaultSound())
        }
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = "download_done",
            content = content,
            trigger = null,
        )
        UNUserNotificationCenter.currentNotificationCenter()
            .addNotificationRequest(request, withCompletionHandler = null)
    }
}
