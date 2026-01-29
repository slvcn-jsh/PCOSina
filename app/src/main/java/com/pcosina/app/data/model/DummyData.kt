package com.pcosina.app.data.model

import androidx.compose.runtime.Immutable

/**
 * UI-first dummy data so every screen renders immediately.
 * No persistence / API yet (non-goals).
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
            title = "High-Protein Greek Yogurt Bowl",
            subtitle = "Berries • chia • cinnamon",
            minutes = 7,
            calories = 320,
            proteinGrams = 28,
            carbsGrams = 32,
            fatsGrams = 10,
            fiberGrams = 9,
            tags = listOf("PCOS-friendly", "High protein", "Quick"),
            ingredients = listOf(
                Ingredient("Greek yogurt (plain)", "200 g"),
                Ingredient("Mixed berries", "1 cup"),
                Ingredient("Chia seeds", "1 tbsp"),
                Ingredient("Cinnamon", "1/2 tsp"),
            ),
            steps = listOf(
                "Add yogurt to a bowl.",
                "Top with berries and chia.",
                "Sprinkle cinnamon and mix lightly.",
            ),
        ),
        Recipe(
            id = "r2",
            title = "Salmon + Quinoa Power Plate",
            subtitle = "Roasted veg • lemon • herbs",
            minutes = 25,
            calories = 540,
            proteinGrams = 38,
            carbsGrams = 44,
            fatsGrams = 22,
            fiberGrams = 8,
            tags = listOf("Omega-3", "Balanced"),
            ingredients = listOf(
                Ingredient("Salmon fillet", "150 g"),
                Ingredient("Quinoa (cooked)", "3/4 cup"),
                Ingredient("Broccoli", "1 cup"),
                Ingredient("Olive oil", "1 tsp"),
                Ingredient("Lemon", "1/2"),
            ),
            steps = listOf(
                "Roast broccoli with olive oil and salt.",
                "Pan-sear salmon 3–4 min per side.",
                "Serve with cooked quinoa; finish with lemon.",
            ),
        ),
        Recipe(
            id = "r3",
            title = "Chicken & Veggie Stir-Fry",
            subtitle = "Low-sugar sauce • crunchy greens",
            minutes = 18,
            calories = 460,
            proteinGrams = 40,
            carbsGrams = 28,
            fatsGrams = 18,
            fiberGrams = 7,
            tags = listOf("Meal prep", "Low GI"),
            ingredients = listOf(
                Ingredient("Chicken breast", "160 g"),
                Ingredient("Bell pepper", "1"),
                Ingredient("Zucchini", "1"),
                Ingredient("Soy sauce (low sodium)", "1 tbsp"),
                Ingredient("Garlic", "2 cloves"),
            ),
            steps = listOf(
                "Slice chicken and vegetables.",
                "Stir-fry chicken until cooked through.",
                "Add veg + sauce; cook until crisp-tender.",
            ),
        ),
        Recipe(
            id = "r4",
            title = "Lentil & Spinach Soup",
            subtitle = "Fiber-forward • cozy • budget",
            minutes = 35,
            calories = 390,
            proteinGrams = 22,
            carbsGrams = 52,
            fatsGrams = 8,
            fiberGrams = 16,
            tags = listOf("High fiber", "Vegetarian"),
            ingredients = listOf(
                Ingredient("Lentils (dry)", "3/4 cup"),
                Ingredient("Spinach", "2 cups"),
                Ingredient("Onion", "1/2"),
                Ingredient("Canned tomatoes", "1 cup"),
                Ingredient("Cumin", "1 tsp"),
            ),
            steps = listOf(
                "Simmer lentils with tomatoes + spices.",
                "Add spinach at the end to wilt.",
                "Season and serve.",
            ),
        ),
        Recipe(
            id = "r5",
            title = "Turkey Lettuce Wraps",
            subtitle = "Fresh crunch • quick lunch",
            minutes = 15,
            calories = 410,
            proteinGrams = 36,
            carbsGrams = 20,
            fatsGrams = 20,
            fiberGrams = 6,
            tags = listOf("Quick", "Low carb"),
            ingredients = listOf(
                Ingredient("Ground turkey", "150 g"),
                Ingredient("Romaine leaves", "6"),
                Ingredient("Cucumber", "1/2"),
                Ingredient("Greek yogurt", "2 tbsp"),
                Ingredient("Lime", "1/2"),
            ),
            steps = listOf(
                "Cook turkey with salt + pepper.",
                "Assemble in romaine leaves with cucumber.",
                "Top with yogurt-lime sauce.",
            ),
        ),
        Recipe(
            id = "r6",
            title = "Egg & Avocado Toast (Whole Grain)",
            subtitle = "Satisfying • steady energy",
            minutes = 10,
            calories = 430,
            proteinGrams = 22,
            carbsGrams = 34,
            fatsGrams = 24,
            fiberGrams = 10,
            tags = listOf("Breakfast", "Balanced"),
            ingredients = listOf(
                Ingredient("Whole grain bread", "2 slices"),
                Ingredient("Eggs", "2"),
                Ingredient("Avocado", "1/2"),
                Ingredient("Cherry tomatoes", "6"),
            ),
            steps = listOf(
                "Toast bread.",
                "Cook eggs to preference.",
                "Mash avocado; assemble and season.",
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
                    PlannedMeal("Lunch", "r3"),
                    PlannedMeal("Dinner", "r2"),
                ),
            ),
            DayPlan(
                dayLabel = "Tue",
                meals = listOf(
                    PlannedMeal("Breakfast", "r6"),
                    PlannedMeal("Lunch", "r5"),
                    PlannedMeal("Dinner", "r4"),
                ),
            ),
            DayPlan(
                dayLabel = "Wed",
                meals = listOf(
                    PlannedMeal("Breakfast", "r1"),
                    PlannedMeal("Lunch", "r2"),
                    PlannedMeal("Dinner", "r3"),
                ),
            ),
            DayPlan(
                dayLabel = "Thu",
                meals = listOf(
                    PlannedMeal("Breakfast", "r6"),
                    PlannedMeal("Lunch", "r4"),
                    PlannedMeal("Dinner", "r5"),
                ),
            ),
            DayPlan(
                dayLabel = "Fri",
                meals = listOf(
                    PlannedMeal("Breakfast", "r1"),
                    PlannedMeal("Lunch", "r3"),
                    PlannedMeal("Dinner", "r2"),
                ),
            ),
            DayPlan(
                dayLabel = "Sat",
                meals = listOf(
                    PlannedMeal("Breakfast", "r6"),
                    PlannedMeal("Lunch", "r5"),
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
        GroceryItem("Eggplant", "4 pcs", 40, "Produce"),
        GroceryItem("Sitaw (String beans)", "300g", 30, "Produce"),
        GroceryItem("Kalabasa (Squash)", "500g", 45, "Produce"),
        GroceryItem("Tomatoes", "6 pcs", 60, "Produce"),
        GroceryItem("Mango", "3 pcs", 120, "Produce"),
        GroceryItem("Banana", "1 bunch", 50, "Produce"),
        // Meat & Seafood
        GroceryItem("Tilapia", "500g", 180, "Meat & Seafood"),
        GroceryItem("Bangus (Milkfish)", "400g", 160, "Meat & Seafood"),
        GroceryItem("Chicken breast", "600g", 210, "Meat & Seafood"),
        GroceryItem("Lean pork", "300g", 150, "Meat & Seafood"),
        // Dry Goods
        GroceryItem("Brown rice", "1 kg", 80, "Dry Goods"),
        GroceryItem("Oatmeal", "500g", 95, "Dry Goods"),
        GroceryItem("Monggo beans", "250g", 40, "Dry Goods"),
        // Spices & Condiments
        GroceryItem("Soy sauce", "1 bottle", 35, "Spices & Condiments"),
        GroceryItem("Fish sauce", "1 bottle", 30, "Spices & Condiments"),
    )

    fun recipeById(id: String): Recipe? = recipes.firstOrNull { it.id == id }
}

