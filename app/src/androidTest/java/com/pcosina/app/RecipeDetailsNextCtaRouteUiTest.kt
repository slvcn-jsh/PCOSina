package com.pcosina.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pcosina.app.ui.screens.RecipeImpactSummary
import com.pcosina.app.ui.screens.RecipeImpactSummarySection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipeDetailsNextCtaRouteUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nextCta_clickRoutesToExpectedDestination() {
        val expectedRoute = "recipe_details/next_recipe_42"
        composeRule.setContent {
            var routedTo by remember { mutableStateOf("") }
            MaterialTheme {
                RecipeImpactSummarySection(
                    summary = RecipeImpactSummary(
                        headline = "You're on track today.",
                        detailLine = "Protein ≈42/85g • Fiber ≈12/25g",
                        nextSuggestion = "Next: Dinner • Salmon and Veg Bowl.",
                        nextRoute = expectedRoute,
                        nextCtaLabel = "Open Next Meal"
                    ),
                    detailsExpanded = true,
                    onToggleDetails = {},
                    onNavigateToRoute = { route -> routedTo = route }
                )
                Text("route:$routedTo")
            }
        }

        composeRule.onNodeWithTag("recipe_impact_next_cta").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("route:$expectedRoute").assertIsDisplayed()
    }
}
