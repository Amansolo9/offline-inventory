package com.eaglepoint.task136.shared.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.datetime.Instant

actual class NotificationScheduler {
    actual suspend fun schedule(id: String, title: String, body: String, at: Instant) {
        val context = AndroidPlatformContext.require()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val requestCode = id.hashCode()
        val intent = Intent(context, NotificationAlarmReceiver::class.java)
            .putExtra(NotificationAlarmReceiver.EXTRA_ID, requestCode)
            .putExtra(NotificationAlarmReceiver.EXTRA_TITLE, title)
            .putExtra(NotificationAlarmReceiver.EXTRA_BODY, body)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            at.toEpochMilliseconds(),
            pendingIntent,
        )
    }
}

actual fun createNotificationScheduler(): NotificationScheduler = NotificationScheduler()
