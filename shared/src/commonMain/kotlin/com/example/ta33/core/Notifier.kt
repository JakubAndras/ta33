package com.example.ta33.core

/**
 * Sends local notifications (FR-11b). Platform-native per implementation (iOS UNUserNotificationCenter,
 * Android NotificationManagerCompat). Posting is a no-op when the OS notification permission is not
 * granted - callers do not need to check permission first.
 */
interface Notifier {
    /** Posts a local "offline package downloaded" notification. No-op if not permitted. */
    fun notifyDownloadComplete(title: String, body: String)
}
