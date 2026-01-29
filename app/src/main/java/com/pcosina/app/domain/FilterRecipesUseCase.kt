package com.pcosina.app.domain

import com.pcosina.app.data.model.Recipe

/**
 * Placeholder only (non-goal: real filtering logic / persistence).
 */
class FilterRecipesUseCase {
    fun filter(
        recipes: List<Recipe>,
        query: String,
        tags: Set<String>,
    ): List<Recipe> = recipes
}

