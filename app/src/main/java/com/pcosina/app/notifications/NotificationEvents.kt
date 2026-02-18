package com.pcosina.app.notifications

object NotificationEvents {
    const val MealBreakfast = "meal_breakfast"
    const val MealLunch = "meal_lunch"
    const val MealDinner = "meal_dinner"
    const val WeeklyReset = "weekly_reset"
    const val StreakNudge = "streak_nudge"
    const val InactivityNudge = "inactivity_nudge"
    const val PlanReady = "plan_ready"
    const val GrocerySyncSuccess = "grocery_sync_success"
    const val GrocerySyncFailure = "grocery_sync_failure"
    const val DebugTest = "debug_test"

    const val TagRoot = "pcosina_notification"
    const val TagMeals = "pcosina_notification_meals"
    const val TagWeekly = "pcosina_notification_weekly"
    const val TagEngagement = "pcosina_notification_engagement"
    val SchedulerTags = listOf(TagMeals, TagWeekly, TagEngagement)

    const val WorkMealBreakfast = "pcosina_work_meal_breakfast"
    const val WorkMealLunch = "pcosina_work_meal_lunch"
    const val WorkMealDinner = "pcosina_work_meal_dinner"
    const val WorkWeeklyReset = "pcosina_work_weekly_reset"
    const val WorkEngagement = "pcosina_work_engagement"
}
