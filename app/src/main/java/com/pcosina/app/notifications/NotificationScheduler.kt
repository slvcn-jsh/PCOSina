package com.pcosina.app.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.pcosina.app.data.repository.NotificationLocalRepository
import com.pcosina.app.data.repository.UserPreferencesNotificationLocalRepository
import com.pcosina.app.data.model.NotificationPreferences
import com.pcosina.app.data.repository.UserPreferencesRepository
import com.pcosina.app.util.safeUserLogScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

object NotificationScheduler {
    private const val LogTag = "PCOSINA-Notification"
    private const val InputUserId = "user_id"
    private const val InputEventType = "event_type"
    private const val InputHour = "hour"
    private const val InputMinute = "minute"

    private fun notificationLocalRepository(context: Context): NotificationLocalRepository =
        UserPreferencesNotificationLocalRepository(UserPreferencesRepository(context))

    suspend fun rescheduleAll(
        context: Context,
        userId: String,
        prefs: NotificationPreferences
    ) {
        if (userId.isBlank()) return
        if (!prefs.masterEnabled) {
            cancelAll(context, userId)
            return
        }
        if (!NotificationHelper.canPostNotifications(context)) {
            Log.d(LogTag, "reschedule skipped: posting blocked, keeping existing work")
            return
        }
        cancelAll(context, userId)

        if (prefs.mealRemindersEnabled) {
            enqueueMealReminder(
                context = context,
                userId = userId,
                eventType = NotificationEvents.MealBreakfast,
                hour = prefs.breakfastHour,
                minute = prefs.breakfastMinute
            )
            enqueueMealReminder(
                context = context,
                userId = userId,
                eventType = NotificationEvents.MealLunch,
                hour = prefs.lunchHour,
                minute = prefs.lunchMinute
            )
            enqueueMealReminder(
                context = context,
                userId = userId,
                eventType = NotificationEvents.MealDinner,
                hour = prefs.dinnerHour,
                minute = prefs.dinnerMinute
            )
        }

        if (prefs.weeklyResetEnabled) {
            enqueueWeeklyReset(context, userId, prefs)
        }

        if (prefs.streakNudgesEnabled || prefs.inactivityNudgesEnabled) {
            enqueueEngagementCheck(context, userId)
        }
    }

    suspend fun notifyPlanReady(context: Context, userId: String) {
        val repository = notificationLocalRepository(context)
        val prefs = repository.getNotificationPreferences(userId).first()
        dispatchAndTrackNotification(
            context = context,
            repository = repository,
            userId = userId,
            prefs = prefs,
            eventType = NotificationEvents.PlanReady,
            title = "Plan ready",
            body = "Your weekly plan is ready to review.",
            channelId = NotificationHelper.StatusChannelId
        )
    }

    suspend fun notifyGrocerySyncResult(context: Context, userId: String, success: Boolean) {
        val repository = notificationLocalRepository(context)
        val prefs = repository.getNotificationPreferences(userId).first()
        val type = if (success) NotificationEvents.GrocerySyncSuccess else NotificationEvents.GrocerySyncFailure
        val title = if (success) "Grocery sync complete" else "Grocery sync failed"
        val body = if (success) {
            "Your grocery list has been updated."
        } else {
            "We couldn't sync groceries. Retry when online."
        }
        dispatchAndTrackNotification(
            context = context,
            repository = repository,
            userId = userId,
            prefs = prefs,
            eventType = type,
            title = title,
            body = body,
            channelId = NotificationHelper.StatusChannelId
        )
    }

    suspend fun notifyDebugTest(context: Context, userId: String) {
        if (userId.isBlank()) return
        val repository = notificationLocalRepository(context)
        val prefs = repository.getNotificationPreferences(userId).first()
        val title = "Notification test"
        val body = "This is a local notification test from PCOSina."
        dispatchAndTrackNotification(
            context = context,
            repository = repository,
            userId = userId,
            prefs = prefs,
            eventType = NotificationEvents.DebugTest,
            title = title,
            body = body,
            channelId = NotificationHelper.StatusChannelId
        )
    }

    suspend fun notifyMealReminderNow(
        context: Context,
        userId: String,
        eventType: String = NotificationEvents.MealLunch
    ): Boolean {
        if (userId.isBlank()) return false
        val repository = notificationLocalRepository(context)
        val prefs = repository.getNotificationPreferences(userId).first()
        if (!prefs.masterEnabled || !prefs.mealRemindersEnabled) return false
        val lastFired = repository.getNotificationLastFired(userId, eventType)
        val nowMs = System.currentTimeMillis()
        if ((nowMs - lastFired) < TimeUnit.HOURS.toMillis(18)) {
            Log.d(LogTag, "manual meal reminder blocked by 18h cap for type=$eventType")
            return false
        }
        val profile = repository.getUserProfile(userId).first()
        val goalTrack = NotificationGoalPolicy.fromGoal(profile.goal)
        val body = NotificationGoalPolicy.mealReminderBody(goalTrack)
        return dispatchAndTrackNotification(
            context = context,
            repository = repository,
            userId = userId,
            prefs = prefs,
            eventType = eventType,
            title = "Meal check-in time",
            body = body,
            channelId = NotificationHelper.ReminderChannelId
        )
    }

    suspend fun notifyWeeklyResetNow(context: Context, userId: String): Boolean {
        if (userId.isBlank()) return false
        val repository = notificationLocalRepository(context)
        val prefs = repository.getNotificationPreferences(userId).first()
        if (!prefs.masterEnabled || !prefs.weeklyResetEnabled) return false
        val eventType = NotificationEvents.WeeklyReset
        val lastFired = repository.getNotificationLastFired(userId, eventType)
        val nowMs = System.currentTimeMillis()
        if ((nowMs - lastFired) < TimeUnit.DAYS.toMillis(7)) {
            Log.d(LogTag, "manual weekly reset blocked by 7-day cap")
            return false
        }
        return dispatchAndTrackNotification(
            context = context,
            repository = repository,
            userId = userId,
            prefs = prefs,
            eventType = eventType,
            title = "New week check-in",
            body = "Your next week is ready to plan. Generate your plan when you’re ready.",
            channelId = NotificationHelper.ReminderChannelId
        )
    }

    fun cancelAll(context: Context, userId: String) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork("${NotificationEvents.WorkMealBreakfast}_$userId")
        manager.cancelUniqueWork("${NotificationEvents.WorkMealLunch}_$userId")
        manager.cancelUniqueWork("${NotificationEvents.WorkMealDinner}_$userId")
        manager.cancelUniqueWork("${NotificationEvents.WorkWeeklyReset}_$userId")
        manager.cancelUniqueWork("${NotificationEvents.WorkEngagement}_$userId")
    }

    fun cancelAllForSession(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(NotificationEvents.TagRoot)
    }

    suspend fun getScheduledWorkSummaries(context: Context): List<String> {
        val manager = WorkManager.getInstance(context)
        return withContext(Dispatchers.IO) {
            NotificationEvents.SchedulerTags.flatMap { tag ->
                runCatching { manager.getWorkInfosByTag(tag).get() }.getOrNull().orEmpty()
            }.filter { info ->
                info.state == androidx.work.WorkInfo.State.ENQUEUED ||
                    info.state == androidx.work.WorkInfo.State.RUNNING
            }.sortedBy { info -> info.id.toString() }
                .map { info ->
                    val tag = info.tags.firstOrNull { it.startsWith("pcosina_notification_") } ?: "notification"
                    "$tag → ${info.state.name.lowercase(Locale.ENGLISH)}"
                }
        }
    }

    private fun enqueueMealReminder(
        context: Context,
        userId: String,
        eventType: String,
        hour: Int,
        minute: Int
    ) {
        val uniqueWorkName = mealWorkNameForEvent(userId, eventType) ?: run {
            Log.w(LogTag, "Skipped schedule for unknown meal eventType=$eventType")
            return
        }
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        val delay = computeDelayToLocalTime(hour = safeHour, minute = safeMinute)
        val request = OneTimeWorkRequestBuilder<MealReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(InputUserId, userId)
                    .putString(InputEventType, eventType)
                    .putInt(InputHour, safeHour)
                    .putInt(InputMinute, safeMinute)
                    .build()
            )
            .addTag(NotificationEvents.TagRoot)
            .addTag(NotificationEvents.TagMeals)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun enqueueWeeklyReset(
        context: Context,
        userId: String,
        prefs: NotificationPreferences
    ) {
        val nextWeeklyDelay = computeDelayToNextWeeklyReset(
            dayOfWeek = prefs.weeklyResetDayOfWeek,
            hour = prefs.weeklyResetHour,
            minute = prefs.weeklyResetMinute
        )
        val request = OneTimeWorkRequestBuilder<WeeklyResetWorker>()
            .setInitialDelay(nextWeeklyDelay, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(InputUserId, userId)
                    .putString(InputEventType, NotificationEvents.WeeklyReset)
                    .build()
            )
            .addTag(NotificationEvents.TagRoot)
            .addTag(NotificationEvents.TagWeekly)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${NotificationEvents.WorkWeeklyReset}_$userId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun enqueueEngagementCheck(context: Context, userId: String) {
        val delay = computeDelayToLocalTime(hour = 18, minute = 0)
        val request = OneTimeWorkRequestBuilder<EngagementNudgeWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(InputUserId, userId)
                    .build()
            )
            .addTag(NotificationEvents.TagRoot)
            .addTag(NotificationEvents.TagEngagement)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${NotificationEvents.WorkEngagement}_$userId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun computeDelayToLocalTime(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return Duration.between(now, next).toMillis().coerceAtLeast(0L)
    }

    private fun computeDelayToNextWeeklyReset(
        dayOfWeek: Int,
        hour: Int,
        minute: Int
    ): Long {
        val targetDay = runCatching { DayOfWeek.of(dayOfWeek.coerceIn(1, 7)) }.getOrDefault(DayOfWeek.MONDAY)
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        val now = LocalDateTime.now()
        val nextWeekly = now.toLocalDate()
            .with(TemporalAdjusters.nextOrSame(targetDay))
            .atTime(LocalTime.of(safeHour, safeMinute))
            .let { if (it.isAfter(now)) it else it.plusWeeks(1) }
        return Duration.between(now, nextWeekly).toMillis().coerceAtLeast(0L)
    }

    fun nextScheduledTimes(prefs: NotificationPreferences): List<String> {
        if (!prefs.masterEnabled) return listOf("Notifications are currently disabled.")
        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.ENGLISH)
        val lines = mutableListOf<String>()
        if (prefs.mealRemindersEnabled) {
            val breakfast = nextRunAt(now, prefs.breakfastHour, prefs.breakfastMinute)
            val lunch = nextRunAt(now, prefs.lunchHour, prefs.lunchMinute)
            val dinner = nextRunAt(now, prefs.dinnerHour, prefs.dinnerMinute)
            lines += "Breakfast → ${breakfast.format(formatter)}"
            lines += "Lunch → ${lunch.format(formatter)}"
            lines += "Dinner → ${dinner.format(formatter)}"
        }
        if (prefs.weeklyResetEnabled) {
            val weekly = nextRunAtDayTime(
                now = now,
                dayOfWeek = prefs.weeklyResetDayOfWeek,
                hour = prefs.weeklyResetHour,
                minute = prefs.weeklyResetMinute
            )
            lines += "Weekly reset → ${weekly.format(formatter)}"
        }
        if (prefs.streakNudgesEnabled || prefs.inactivityNudgesEnabled) {
            val engagement = nextRunAt(now, 18, 0)
            lines += "Engagement check → ${engagement.format(formatter)}"
        }
        return lines
    }

    private fun nextRunAt(now: LocalDateTime, hour: Int, minute: Int): LocalDateTime {
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next
    }

    private fun nextRunAtDayTime(
        now: LocalDateTime,
        dayOfWeek: Int,
        hour: Int,
        minute: Int
    ): LocalDateTime {
        val targetDay = runCatching { DayOfWeek.of(dayOfWeek.coerceIn(1, 7)) }.getOrDefault(DayOfWeek.MONDAY)
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        return now.toLocalDate()
            .with(TemporalAdjusters.nextOrSame(targetDay))
            .atTime(safeHour, safeMinute)
            .let { if (it.isAfter(now)) it else it.plusWeeks(1) }
    }

    private fun mealWorkNameForEvent(userId: String, eventType: String): String? = when (eventType) {
        NotificationEvents.MealBreakfast -> "${NotificationEvents.WorkMealBreakfast}_$userId"
        NotificationEvents.MealLunch -> "${NotificationEvents.WorkMealLunch}_$userId"
        NotificationEvents.MealDinner -> "${NotificationEvents.WorkMealDinner}_$userId"
        else -> null
    }

    private fun mealTimeForEvent(
        prefs: NotificationPreferences,
        eventType: String
    ): Pair<Int, Int>? = when (eventType) {
        NotificationEvents.MealBreakfast -> prefs.breakfastHour to prefs.breakfastMinute
        NotificationEvents.MealLunch -> prefs.lunchHour to prefs.lunchMinute
        NotificationEvents.MealDinner -> prefs.dinnerHour to prefs.dinnerMinute
        else -> null
    }

    private fun isUserSessionValid(userId: String): Boolean {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        return !currentUid.isNullOrBlank() && currentUid == userId
    }

    internal fun isNowWithinQuietHours(
        prefs: NotificationPreferences,
        now: LocalDateTime = LocalDateTime.now()
    ): Boolean {
        if (!prefs.quietHoursEnabled) return false
        return isWithinQuietHours(
            nowHour = now.hour,
            nowMinute = now.minute,
            quietStartHour = prefs.quietStartHour,
            quietStartMinute = prefs.quietStartMinute,
            quietEndHour = prefs.quietEndHour,
            quietEndMinute = prefs.quietEndMinute
        )
    }

    internal fun isTypeEnabled(
        prefs: NotificationPreferences,
        eventType: String
    ): Boolean = when (eventType) {
        NotificationEvents.MealBreakfast,
        NotificationEvents.MealLunch,
        NotificationEvents.MealDinner -> prefs.mealRemindersEnabled
        NotificationEvents.PlanReady -> prefs.planReadyEnabled
        NotificationEvents.GrocerySyncSuccess,
        NotificationEvents.GrocerySyncFailure -> prefs.grocerySyncEnabled
        NotificationEvents.WeeklyReset -> prefs.weeklyResetEnabled
        NotificationEvents.StreakNudge -> prefs.streakNudgesEnabled
        NotificationEvents.InactivityNudge -> prefs.inactivityNudgesEnabled
        NotificationEvents.DebugTest -> true
        else -> false
    }

    private suspend fun dispatchAndTrackNotification(
        context: Context,
        repository: NotificationLocalRepository,
        userId: String,
        prefs: NotificationPreferences,
        eventType: String,
        title: String,
        body: String,
        channelId: String
    ): Boolean {
        if (!isUserSessionValid(userId)) {
            Log.d(LogTag, "dispatch blocked: user session invalid for type=$eventType")
            return false
        }
        if (!prefs.masterEnabled) {
            Log.d(LogTag, "dispatch blocked: master disabled for type=$eventType")
            return false
        }
        if (!isTypeEnabled(prefs, eventType)) {
            Log.d(LogTag, "dispatch blocked: type disabled for type=$eventType")
            return false
        }
        if (isNowWithinQuietHours(prefs) && eventType != NotificationEvents.DebugTest) {
            Log.d(LogTag, "dispatch blocked: quiet hours for type=$eventType")
            return false
        }
        val delivered = NotificationHelper.showNotification(
            context = context,
            eventType = eventType,
            title = title,
            body = body,
            channelId = channelId
        )
        if (delivered) {
            Log.i(LogTag, "notification delivered type=$eventType ${safeUserLogScope(userId)}")
            repository.markNotificationDelivered(
                userId = userId,
                type = eventType,
                title = title,
                body = body
            )
        } else {
            Log.d(LogTag, "dispatch blocked: OS posting unavailable for type=$eventType")
        }
        return delivered
    }

    private fun isWithinQuietHours(
        nowHour: Int,
        nowMinute: Int,
        quietStartHour: Int,
        quietStartMinute: Int,
        quietEndHour: Int,
        quietEndMinute: Int
    ): Boolean {
        val now = nowHour * 60 + nowMinute
        val start = quietStartHour * 60 + quietStartMinute
        val end = quietEndHour * 60 + quietEndMinute
        return if (start <= end) {
            now in start until end
        } else {
            now >= start || now < end
        }
    }

    class MealReminderWorker(
        context: Context,
        params: WorkerParameters
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val userId = inputData.getString(InputUserId).orEmpty()
            if (!isUserSessionValid(userId)) return Result.success()
            val repository = notificationLocalRepository(applicationContext)
            val prefs = repository.getNotificationPreferences(userId).first()
            if (!prefs.masterEnabled || !prefs.mealRemindersEnabled) return Result.success()
            val eventType = inputData.getString(InputEventType).orEmpty()
            if (eventType.isBlank()) return Result.success()
            val mealTime = mealTimeForEvent(prefs, eventType) ?: return Result.success()

            if (!isNowWithinQuietHours(prefs)) {
                val lastFired = repository.getNotificationLastFired(userId, eventType)
                val nowMs = System.currentTimeMillis()
                if ((nowMs - lastFired) >= TimeUnit.HOURS.toMillis(18)) {
                    val profile = repository.getUserProfile(userId).first()
                    val goalTrack = NotificationGoalPolicy.fromGoal(profile.goal)
                    val body = NotificationGoalPolicy.mealReminderBody(goalTrack)
                    dispatchAndTrackNotification(
                        context = applicationContext,
                        repository = repository,
                        userId = userId,
                        prefs = prefs,
                        eventType = eventType,
                        title = "Meal check-in time",
                        body = body,
                        channelId = NotificationHelper.ReminderChannelId
                    )
                }
            }

            enqueueMealReminder(
                context = applicationContext,
                userId = userId,
                eventType = eventType,
                hour = mealTime.first,
                minute = mealTime.second
            )
            return Result.success()
        }
    }

    class WeeklyResetWorker(
        context: Context,
        params: WorkerParameters
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val userId = inputData.getString(InputUserId).orEmpty()
            if (!isUserSessionValid(userId)) return Result.success()
            val repository = notificationLocalRepository(applicationContext)
            val prefs = repository.getNotificationPreferences(userId).first()
            if (!prefs.masterEnabled || !prefs.weeklyResetEnabled) return Result.success()

            val eventType = NotificationEvents.WeeklyReset
            val lastFired = repository.getNotificationLastFired(userId, eventType)
            val nowMs = System.currentTimeMillis()
            if ((nowMs - lastFired) < TimeUnit.DAYS.toMillis(7)) {
                enqueueWeeklyReset(applicationContext, userId, prefs)
                return Result.success()
            }

            val title = "New week check-in"
            val body = "Your next week is ready to plan. Generate your plan when you’re ready."
            dispatchAndTrackNotification(
                context = applicationContext,
                repository = repository,
                userId = userId,
                prefs = prefs,
                eventType = eventType,
                title = title,
                body = body,
                channelId = NotificationHelper.ReminderChannelId
            )
            enqueueWeeklyReset(applicationContext, userId, prefs)
            return Result.success()
        }
    }

    class EngagementNudgeWorker(
        context: Context,
        params: WorkerParameters
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val userId = inputData.getString(InputUserId).orEmpty()
            if (!isUserSessionValid(userId)) return Result.success()
            val repository = notificationLocalRepository(applicationContext)
            val prefs = repository.getNotificationPreferences(userId).first()
            if (!prefs.masterEnabled) return Result.success()
            if (!prefs.streakNudgesEnabled && !prefs.inactivityNudgesEnabled) return Result.success()

            if (!isNowWithinQuietHours(prefs)) {
                val today = LocalDate.now()
                val lastLogDate = repository.getMostRecentDailyLogDate(userId)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val daysInactive = if (lastLogDate == null) 999 else Duration.between(
                    lastLogDate.atStartOfDay(),
                    today.atStartOfDay()
                ).toDays().toInt()
                val profile = repository.getUserProfile(userId).first()
                val goalTrack = NotificationGoalPolicy.fromGoal(profile.goal)

                if (prefs.inactivityNudgesEnabled && daysInactive >= 3) {
                    val eventType = NotificationEvents.InactivityNudge
                    val lastFired = repository.getNotificationLastFired(userId, eventType)
                    val nowMs = System.currentTimeMillis()
                    if ((nowMs - lastFired) >= TimeUnit.DAYS.toMillis(3)) {
                        val title = "Quick check-in"
                        val body = NotificationGoalPolicy.inactivityBody(goalTrack)
                        dispatchAndTrackNotification(
                            context = applicationContext,
                            repository = repository,
                            userId = userId,
                            prefs = prefs,
                            eventType = eventType,
                            title = title,
                            body = body,
                            channelId = NotificationHelper.ReminderChannelId
                        )
                    }
                } else if (prefs.streakNudgesEnabled) {
                    val eventType = NotificationEvents.StreakNudge
                    val lastFired = repository.getNotificationLastFired(userId, eventType)
                    val nowMs = System.currentTimeMillis()
                    if ((nowMs - lastFired) >= NotificationGoalPolicy.streakMinIntervalMs(goalTrack)) {
                        val title = "Keep your routine going"
                        val body = NotificationGoalPolicy.streakBody(goalTrack)
                        dispatchAndTrackNotification(
                            context = applicationContext,
                            repository = repository,
                            userId = userId,
                            prefs = prefs,
                            eventType = eventType,
                            title = title,
                            body = body,
                            channelId = NotificationHelper.ReminderChannelId
                        )
                    }
                }
            }

            enqueueEngagementCheck(applicationContext, userId)
            return Result.success()
        }
    }
}
