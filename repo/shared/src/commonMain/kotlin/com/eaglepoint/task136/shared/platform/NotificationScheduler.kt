package com.eaglepoint.task136.shared.platform

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

expect class NotificationScheduler {
    suspend fun schedule(id: String, title: String, body: String, at: Instant)
}

expect fun createNotificationScheduler(): NotificationScheduler

suspend fun NotificationScheduler.scheduleWithQuietHours(
    id: String,
    title: String,
    body: String,
    at: Instant,
    timeZone: TimeZone,
) {
    val local = at.toLocalDateTime(timeZone)
    val hour = local.time.hour
    if (hour in 21..23 || hour in 0..6) return
    schedule(id, title, body, at)
}
