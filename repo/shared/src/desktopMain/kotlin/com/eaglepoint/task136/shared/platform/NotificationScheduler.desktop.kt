package com.eaglepoint.task136.shared.platform

import kotlinx.datetime.Instant

actual class NotificationScheduler {
    actual suspend fun schedule(id: String, title: String, body: String, at: Instant) {
        // Desktop local notifications can be wired here.
    }
}

actual fun createNotificationScheduler(): NotificationScheduler = NotificationScheduler()
