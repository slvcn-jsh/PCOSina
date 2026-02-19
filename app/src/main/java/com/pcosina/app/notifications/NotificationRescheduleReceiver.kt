package com.pcosina.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.pcosina.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NotificationRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in SupportedActions) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val userId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                if (userId.isBlank()) {
                    NotificationScheduler.cancelAllForSession(appContext)
                    return@launch
                }
                val repository = UserPreferencesRepository(appContext)
                val prefs = repository.getNotificationPreferences(userId).first()
                NotificationScheduler.rescheduleAll(appContext, userId, prefs)
            } catch (t: Throwable) {
                Log.w(LogTag, "Notification reschedule broadcast handling failed for action=$action", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val LogTag = "PCOSINA-Notification"
        val SupportedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}
