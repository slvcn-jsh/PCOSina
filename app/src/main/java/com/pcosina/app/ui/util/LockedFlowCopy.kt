package com.pcosina.app.ui.util

object LockedFlowCopy {
    const val LearnMoreLabel = "Learn more"
    const val LearnMoreTitle = "Learn more"

    const val DashboardSnapshotLockedText = "Unlocks after Step 3: Generate Plan."
    const val DashboardSnapshotLockedDialog =
        "Finish profile and goal setup, then generate your first plan to unlock weekly snapshot insights."
    const val DashboardQuickSnapshotHintBeforePlan = "Unlocks after your first plan."
    const val DashboardQuickSnapshotHintAfterPlan = "Unlocks after weekly review."
    const val DashboardAdvancedLockedText = "Unlocks in Step 6: Tracking."
    const val DashboardAdvancedLockedDialog =
        "After adding groceries and logging check-offs, advanced macro and solver trend metrics appear here."

    const val ProgressLockedText = "Generate a plan to start tracking progress."
    const val ProgressLockedDialogTitle = "Why tracking is locked"
    const val ProgressLockedDialogBody =
        "Tracking unlocks after you generate your first weekly plan, so adherence and reflection map to actual meals."

    const val GroceryLockedText = "Generate a plan to unlock grocery lists."
    const val GroceryLockedDialogTitle = "Why grocery is locked"
    const val GroceryLockedDialogBody =
        "Grocery categories come from your generated weekly plan, so the list unlocks after Step 3."

    data class LockedStateCopy(
        val cardText: String,
        val dialogTitle: String,
        val dialogBody: String
    )

    fun dashboardSnapshotLocked(): LockedStateCopy = LockedStateCopy(
        cardText = DashboardSnapshotLockedText,
        dialogTitle = LearnMoreTitle,
        dialogBody = DashboardSnapshotLockedDialog
    )

    fun dashboardAdvancedLocked(): LockedStateCopy = LockedStateCopy(
        cardText = DashboardAdvancedLockedText,
        dialogTitle = LearnMoreTitle,
        dialogBody = DashboardAdvancedLockedDialog
    )

    fun progressLocked(): LockedStateCopy = LockedStateCopy(
        cardText = ProgressLockedText,
        dialogTitle = ProgressLockedDialogTitle,
        dialogBody = ProgressLockedDialogBody
    )

    fun groceryLocked(): LockedStateCopy = LockedStateCopy(
        cardText = GroceryLockedText,
        dialogTitle = GroceryLockedDialogTitle,
        dialogBody = GroceryLockedDialogBody
    )
}
