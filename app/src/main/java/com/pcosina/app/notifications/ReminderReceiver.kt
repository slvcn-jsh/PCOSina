package com.pcosina.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        NotificationHelper.createChannel(context)
        NotificationHelper.showReminder(context)
    }
}
