package com.pcosina.app.data.model

import androidx.compose.runtime.Immutable

/**
 * UI-first dummy data localized for the Filipino context (Pinggang Pinoy).
 * Focused on PCOS-friendly, low-GI, and high-fiber local ingredients.
 */
object DummyData {
    val userProfile: UserProfile = UserProfile(
        displayName = "Maria",
        age = 25,
        heightCm = 160,
        weightKg = 65,
        goal = "Support PCOS symptom management",
        activityLevel = "Lightly Active",
    )

    @Immutable
    data class MacroProgress(
        val protein: Float,
        val carbs: Float,
        val fats: Float,
        val fiber: Float,
    )

    val macroProgress: MacroProgress = MacroProgress(
        protein = 0.72f,
        carbs = 0.58f,
        fats = 0.44f,
        fiber = 0.63f,
    )

    val recipes: List<Recipe> = listOf(
        Recipe(
            id = "r1",
            title = "Red Rice Lugaw with Ginger & Egg",
            subtitle = "Warm • Digestion-friendly • Low GI",
            minutes = 20,
            calories = 280,
            proteinGrams = 14,
            carbsGrams = 42,
            fatsGrams = 6,
            fiberGrams = 5,
            tags = listOf("Breakfast", "PCOS-friendly", "Low GI"),
            ingredients = listOf(
                Ingredient("Red rice", "1/2 cup"),
                Ingredient("Ginger", "2-inch thumb"),
                Ingredient("Garlic", "3 cloves"),
                Ingredient("Hard-boiled egg", "1 pc"),
                Ingredient("Spring onions", "1 stalk"),
            ),
            steps = listOf(
                "Boil red rice with plenty of water and ginger until soft.",
                "Sauté garlic until golden brown for topping.",
                "Serve warm with a boiled egg and spring onions.",
            ),
        ),
        Recipe(
            id = "r2",
            title = "Grilled Bangus with Ensaladang Talong",
            subtitle = "Omega-3 rich • Filipino favorite",
            minutes = 25,
            calories = 420,
            proteinGrams = 35,
            carbsGrams = 12,
            fatsGrams = 24,
            fiberGrams = 6,
            tags = listOf("Lunch", "Omega-3", "High Protein"),
            ingredients = listOf(
                Ingredient("Bangus fillet", "150 g"),
                Ingredient("Eggplant", "1 large"),
                Ingredient("Tomatoes", "2 pcs"),
                Ingredient("Red onion", "1/2 pc"),
                Ingredient("Calamansi juice", "1 tbsp"),
            ),
            steps = listOf(
                "Grill bangus until cooked through.",
                "Char the eggplant, peel skin, and mash with tomatoes and onions.",
                "Season with calamansi and a pinch of salt.",
            ),
        ),
        Recipe(
            id = "r3",
            title = "Chicken Tinola with Malunggay",
            subtitle = "Immune-boosting • Glow foods",
            minutes = 30,
            calories = 350,
            proteinGrams = 32,
            carbsGrams = 15,
            fatsGrams = 18,
            fiberGrams = 4,
            tags = listOf("Dinner", "Glow", "Low Carb"),
            ingredients = listOf(
                Ingredient("Chicken breast (skinless)", "160 g"),
                Ingredient("Sayote", "1/2 pc"),
                Ingredient("Malunggay leaves", "1 cup"),
                Ingredient("Ginger", "1 thumb"),
                Ingredient("Lemongrass", "1 stalk"),
            ),
            steps = listOf(
                "Sauté ginger and onion, add chicken and sear.",
                "Add water and lemongrass, simmer until chicken is tender.",
                "Add sayote, then Malunggay leaves just before serving.",
            ),
        ),
        Recipe(
            id = "r4",
            title = "Monggo Guisado with Tinapa",
            subtitle = "High fiber • Plant-based protein",
            minutes = 35,
            calories = 380,
            proteinGrams = 22,
            carbsGrams = 48,
            fatsGrams = 12,
            fiberGrams = 14,
            tags = listOf("Lunch", "High Fiber", "Budget-friendly"),
            ingredients = listOf(
                Ingredient("Monggo beans (boiled)", "1 cup"),
                Ingredient("Tinapa (smoked fish) flakes", "2 tbsp"),
                Ingredient("Ampalaya leaves", "1 cup"),
                Ingredient("Garlic", "2 cloves"),
                Ingredient("Tomatoes", "1 pc"),
            ),
            steps = listOf(
                "Sauté garlic, onions, and tomatoes.",
                "Add boiled monggo and tinapa flakes, simmer.",
                "Stir in ampalaya leaves and cook for 1 minute.",
            ),
        ),
        Recipe(
            id = "r5",
            title = "Lean Pork Adobo with Cauliflower Rice",
            subtitle = "Low carb • Traditional taste",
            minutes = 40,
            calories = 410,
            proteinGrams = 38,
            carbsGrams = 10,
            fatsGrams = 22,
            fiberGrams = 4,
            tags = listOf("Dinner", "Traditional", "Low Carb"),
            ingredients = listOf(
                Ingredient("Lean pork loin cubes", "150 g"),
                Ingredient("Soy sauce (low sodium)", "2 tbsp"),
                Ingredient("Vinegar", "1 tbsp"),
                Ingredient("Garlic", "4 cloves"),
                Ingredient("Cauliflower (grated)", "2 cups"),
            ),
            steps = listOf(
                "Marinate pork in soy sauce, vinegar, and garlic.",
                "Simmer pork until tender and sauce reduces.",
                "Sauté grated cauliflower briefly as a rice substitute.",
            ),
        ),
        Recipe(
            id = "r6",
            title = "Pinoy Scrambled Eggs with Kamatis",
            subtitle = "Quick • Protein-packed",
            minutes = 10,
            calories = 260,
            proteinGrams = 18,
            carbsGrams = 8,
            fatsGrams = 16,
            fiberGrams = 2,
            tags = listOf("Breakfast", "Quick", "Simple"),
            ingredients = listOf(
                Ingredient("Eggs", "2 pcs"),
                Ingredient("Tomatoes", "2 pcs"),
                Ingredient("Onions", "1/2 pc"),
                Ingredient("Spinach or Dahon ng Sili", "1/2 cup"),
            ),
            steps = listOf(
                "Sauté onions and tomatoes until soft.",
                "Pour in beaten eggs and scramble.",
                "Toss in greens at the end until wilted.",
            ),
        ),
    )

    val weeklyMealPlan: MealPlan = MealPlan(
        weekLabel = "This week",
        days = listOf(
            DayPlan(
                dayLabel = "Mon",
                meals = listOf(
                    PlannedMeal("Breakfast", "r1"),
                    PlannedMeal("Lunch", "r4"),
                    PlannedMeal("Dinner", "r2"),
                ),
            ),
            DayPlan(
                dayLabel = "Tue",
                meals = listOf(
                    PlannedMeal("Breakfast", "r6"),
                    PlannedMeal("Lunch", "r2"),
                    PlannedMeal("Dinner", "r3"),
                ),
            ),
            DayPlan(
                dayLabel = "Wed",
                meals = listOf(
                    PlannedMeal("Breakfast", "r1"),
                    PlannedMeal("Lunch", "r2"),
                    PlannedMeal("Dinner", "r5"),
                ),
            ),
            DayPlan(
                dayLabel = "Thu",
                meals = listOf(
                    PlannedMeal("Breakfast", "r6"),
                    PlannedMeal("Lunch", "r4"),
                    PlannedMeal("Dinner", "r3"),
                ),
            ),
            DayPlan(
                dayLabel = "Fri",
                meals = listOf(
                    PlannedMeal("Breakfast", "r1"),
                    PlannedMeal("Lunch", "r4"),
                    PlannedMeal("Dinner", "r2"),
                ),
            ),
            DayPlan(
                dayLabel = "Sat",
                meals = listOf(
                    PlannedMeal("Breakfast", "r6"),
                    PlannedMeal("Lunch", "r2"),
                    PlannedMeal("Dinner", "r4"),
                ),
            ),
            DayPlan(
                dayLabel = "Sun",
                meals = listOf(
                    PlannedMeal("Breakfast", "r1"),
                    PlannedMeal("Lunch", "r2"),
                    PlannedMeal("Dinner", "r3"),
                ),
            ),
        ),
    )

    @Immutable
    data class GroceryItem(
        val name: String,
        val quantity: String,
        val price: Int,
        val category: String,
    )

    val groceryList: List<GroceryItem> = listOf(
        // Produce
        GroceryItem("Sayote", "2 pcs", 30, "Produce"),
        GroceryItem("Sitaw (String beans)", "300g", 30, "Produce"),
        GroceryItem("Malunggay leaves", "2 bunches", 20, "Produce"),
        GroceryItem("Tomatoes", "10 pcs", 60, "Produce"),
        GroceryItem("Ginger", "100g", 25, "Produce"),
        GroceryItem("Garlic", "3 bulbs", 45, "Produce"),
        GroceryItem("Eggplant", "3 pcs", 40, "Produce"),
        // Meat & Seafood
        GroceryItem("Bangus (Milkfish)", "2 pcs", 180, "Meat & Seafood"),
        GroceryItem("Chicken breast", "1kg", 240, "Meat & Seafood"),
        GroceryItem("Lean pork loin", "500g", 175, "Meat & Seafood"),
        GroceryItem("Tinapa flakes", "1 pack", 55, "Meat & Seafood"),
        // Eggs & Dairy
        GroceryItem("Eggs", "1 dozen", 110, "Eggs & Dairy"),
        // Dry Goods
        GroceryItem("Red rice", "2 kg", 150, "Dry Goods"),
        GroceryItem("Monggo beans", "250g", 40, "Dry Goods"),
        // Spices & Condiments
        GroceryItem("Soy sauce (Low sodium)", "1 bottle", 45, "Spices & Condiments"),
        GroceryItem("Vinegar", "1 bottle", 30, "Spices & Condiments"),
    )

    fun recipeById(id: String): Recipe? = recipes.firstOrNull { it.id == id }
}
