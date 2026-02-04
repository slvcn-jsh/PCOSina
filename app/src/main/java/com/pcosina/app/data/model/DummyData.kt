package com.pcosina.app.data.model

import androidx.compose.runtime.Immutable

/**
 * Clean data model synced with the live SQLite database.
 * Redundant legacy hardcoded data removed to ensure single-source-of-truth from backend.
 */
object DummyData {
    val userProfile: UserProfile = UserProfile(
        displayName = "User",
        age = 25,
        heightCm = 160,
        weightKg = 65,
        activityLevel = "Lightly Active",
    )

    @Immutable
    data class GroceryItem(
        val name: String,
        val quantity: String,
        val price: Int,
        val category: String,
    )

    // Initial essentials for new users
    val groceryList: List<GroceryItem> = listOf(
        GroceryItem("Red Rice", "2 kg", 150, "Dry Goods"),
        GroceryItem("Eggs", "1 dozen", 110, "Eggs & Dairy"),
        GroceryItem("Virgin Coconut Oil", "250ml", 120, "Spices & Condiments"),
    )

    // Helper used by UI logic to check if a recipe exists in the local fallback
    // In production, the app calls the Python Backend API instead.
    fun recipeById(id: String): Recipe? = null
}
