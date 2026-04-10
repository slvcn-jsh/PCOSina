package com.pcosina.app

import com.pcosina.app.data.model.FeedbackEntry
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import kotlinx.coroutines.flow.MutableStateFlow

internal fun bindMealPlanCurrentUserIdForTest(
    mealPlanViewModel: MealPlanViewModel,
    userId: String
) {
    val field = MealPlanViewModel::class.java.getDeclaredField("currentUserId")
    field.isAccessible = true
    field.set(mealPlanViewModel, userId)
}

internal fun bindGroceryCurrentUserIdForTest(
    groceryViewModel: GroceryViewModel,
    userId: String
) {
    val field = GroceryViewModel::class.java.getDeclaredField("currentUserId")
    field.isAccessible = true
    field.set(groceryViewModel, userId)
}

internal fun bindProgressCurrentUserIdForTest(
    progressViewModel: ProgressViewModel,
    userId: String
) {
    val field = ProgressViewModel::class.java.getDeclaredField("currentUserId")
    field.isAccessible = true
    field.set(progressViewModel, userId)
}

@Suppress("UNCHECKED_CAST")
internal fun seedProgressFeedbackQueueForTest(
    progressViewModel: ProgressViewModel,
    entries: List<FeedbackEntry>
) {
    val field = ProgressViewModel::class.java.getDeclaredField("_feedbackQueue")
    field.isAccessible = true
    val flow = field.get(progressViewModel) as MutableStateFlow<List<FeedbackEntry>>
    flow.value = entries
}
