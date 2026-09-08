package com.pcosina.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pcosina.app.R
import android.content.pm.PackageManager

object NotificationHelper {
    const val ReminderChannelId = "pcosina_reminders"
    const val StatusChannelId = "pcosina_status"

    private const val ReminderChannelName = "PCOSina Reminders"
    private const val ReminderChannelDesc = "Meal and weekly routine reminders"
    private const val StatusChannelName = "PCOSina Status"
    private const val StatusChannelDesc = "Plan and sync status updates"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val reminderChannel = NotificationChannel(
            ReminderChannelId,
            ReminderChannelName,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = ReminderChannelDesc
        }
        val statusChannel = NotificationChannel(
            StatusChannelId,
            StatusChannelName,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = StatusChannelDesc
        }
        manager.createNotificationChannel(reminderChannel)
        manager.createNotificationChannel(statusChannel)
    }

    fun canPostNotifications(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showNotification(
        context: Context,
        eventType: String,
        title: String,
        body: String,
        channelId: String = ReminderChannelId
    ): Boolean {
        if (!canPostNotifications(context)) return false
        createChannels(context)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context,
            eventType.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(eventType.hashCode(), notification)
        return true
    }
}
