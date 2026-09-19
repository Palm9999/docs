package com.sitorplay.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sitorplay.app.MainActivity

const val LINEUP_REMINDER_CHANNEL_ID = "lineup_lock_reminders"
private const val NOTIFICATION_ID = 1001

/** Creates the notification channel once at app startup; a no-op if it already exists. */
fun ensureLineupReminderChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val channel = NotificationChannel(
        LINEUP_REMINDER_CHANNEL_ID,
        "Lineup lock reminders",
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = "Reminds you to check your Start/Sit calls before kickoff"
    }
    context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
}

/** Shows the reminder notification. Silently does nothing if the permission isn't granted. */
fun showLineupReminderNotification(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
    }

    val openAppIntent = android.content.Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
        context, 0, openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, LINEUP_REMINDER_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setContentTitle("Set your lineup!")
        .setContentText("Kickoff is coming up — double-check your Start/Sit calls, especially questionable players.")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
}
