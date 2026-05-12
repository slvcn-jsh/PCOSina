package com.pcosina.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pcosina.app.data.api.AdminPriceRuleUpsertDto
import com.pcosina.app.data.api.AdminRecipeUpsertDto
import com.pcosina.app.data.api.AdminPriceRuleDto
import com.pcosina.app.data.api.IngredientDto
import com.pcosina.app.data.api.RecipeDetailDto
import com.pcosina.app.domain.PriceCatalog
import com.pcosina.app.domain.PriceRule
import com.pcosina.app.domain.canonicalGroceryKey
import com.pcosina.app.domain.canonicalGroceryName
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.theme.PcosinaBlushSurface
import com.pcosina.app.ui.theme.PcosinaDeepRose
import com.pcosina.app.ui.theme.PcosinaInfo
import com.pcosina.app.ui.theme.PcosinaMuted
import com.pcosina.app.ui.theme.PcosinaPink
import com.pcosina.app.ui.theme.PcosinaSuccess
import com.pcosina.app.ui.theme.PcosinaSurface
import com.pcosina.app.ui.theme.PcosinaSurfaceAlt
import kotlinx.coroutines.launch
import java.util.Locale

private data class AdminDashboardCardModel(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val status: String,
    val actionLabel: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

private data class AdminPriceReviewRow(
    val id: String?,
    val keywords: List<String>,
    val pricePhp: Int,
    val priceMinPhp: Int?,
    val priceMaxPhp: Int?,
    val category: String,
    val unit: String,
    val active: Boolean,
    val notes: String?,
    val source: String,
)

private data class GroceryMappingReviewRow(
    val canonicalKey: String,
    val canonicalName: String,
    val rawExamples: List<String>,
    val category: String,
    val unit: String,
    val sourceCount: Int,
    val pricePhp: Int,
    val priceStatus: String,
    val quantityExamples: List<String>,
)

private data class DatasetHealth(
    val recipeCount: Int,
    val priceRuleCount: Int,
    val unsupportedMealTypes: Set<String>,
    val recipesMissingIngredients: Int,
    val recipesMissingNutrition: Int,
    val ingredientsWithoutDirectPrice: Int,
)

private data class RecipeEditorState(
    val existingId: String?,
    val id: String,
    val title: String,
    val mealType: String,
    val calories: String,
    val proteinGrams: String,
    val carbsGrams: String,
    val fatsGrams: String,
    val fiberGrams: String,
    val minutes: String,
    val tags: String,
    val ingredients: String,
    val steps: String,
)

private data class PriceRuleEditorState(
    val existingId: String?,
    val id: String,
    val keywords: String,
    val pricePhp: String,
    val priceMinPhp: String,
    val priceMaxPhp: String,
    val category: String,
    val unit: String,
    val active: Boolean,
    val notes: String,
)

@Composable
fun OperatorDashboardScreen(
    signedInEmail: String?,
    backendBaseUrl: String,
    schemaVersion: String,
    buildType: String,
    onOpenRecipes: () -> Unit,
    onOpenPrices: () -> Unit,
    onOpenGroceryPantry: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenMethodology: () -> Unit,
    onOpenMoreTools: () -> Unit,
    onOpenSystemInfo: () -> Unit,
    onOpenAdminSettings: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryCards = listOf(
        AdminDashboardCardModel(
            icon = Icons.Filled.RestaurantMenu,
            title = "Meal & Recipe Management",
            body = "Review real backend recipe data, supported meal labels, ingredients, nutrition fields, and mobile edit readiness.",
            status = "P0 Dataset review",
            actionLabel = "Open",
            onClick = onOpenRecipes,
        ),
        AdminDashboardCardModel(
            icon = Icons.Filled.ShoppingCart,
            title = "Ingredient & Price Management",
            body = "Review backend price rules, categories, units, active status, and estimated pricing coverage.",
            status = "P0 Maintenance",
            actionLabel = "Open",
            onClick = onOpenPrices,
        ),
        AdminDashboardCardModel(
            icon = Icons.Filled.Tune,
            title = "Nutrition & Constraint Rules",
            body = "Inspect hard constraints, soft objectives, fallback behavior, and safe planning boundaries.",
            status = "P0 Rules",
            actionLabel = "Review",
            onClick = onOpenRules,
        ),
        AdminDashboardCardModel(
            icon = Icons.Filled.Settings,
            title = "Admin Settings",
            body = "Switch admin account, review maintenance notes, and keep admin state separated from client flow.",
            status = "P0 Security",
            actionLabel = "Open",
            onClick = onOpenAdminSettings,
        ),
    )
    val secondaryCards = listOf(
        AdminDashboardCardModel(
            icon = Icons.Filled.Info,
            title = "Grocery & Pantry Review",
            body = "Review canonical ingredient mapping, grocery aggregation, missing prices, and pantry-boundary notes.",
            status = "P1 Review",
            actionLabel = "Open",
            onClick = onOpenGroceryPantry,
        ),
        AdminDashboardCardModel(
            icon = Icons.Filled.Link,
            title = "Methodology & Pipeline",
            body = "See the two-stage filtering and optimizer-backed pipeline, data boundaries, and online/offline split.",
            status = "P1 Methodology",
            actionLabel = "Open",
            onClick = onOpenMethodology,
        ),
        AdminDashboardCardModel(
            icon = Icons.Filled.Settings,
            title = "Methodology & Tools",
            body = "Open operator methodology and internal maintenance helpers without entering the client app surface.",
            status = "P1 Admin-only",
            actionLabel = "Open",
            onClick = onOpenMoreTools,
        ),
        AdminDashboardCardModel(
            icon = Icons.Filled.Info,
            title = "System Data & Schema",
            body = "Check backend, schema, auth state, notification permission, dataset counts, and health warnings.",
            status = "P1 System",
            actionLabel = "Open",
            onClick = onOpenSystemInfo,
        ),
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(PcosinaSurface)
            .statusBarsPadding()
    ) {
        val twoColumns = maxWidth >= 620.dp
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                AdminHeroHeader(
                    title = "Admin Dashboard",
                    subtitle = "Maintain meals, ingredients, prices, and system data.",
                    signedInEmail = signedInEmail,
                    badge = "Admin access verified",
                    modifier = Modifier.testTag("operator_dashboard_header"),
                )
            }
            item {
                AdminIdentityCard(
                    signedInEmail = signedInEmail,
                    backendBaseUrl = backendBaseUrl,
                    schemaVersion = schemaVersion,
                    buildType = buildType,
                    onSignOut = onSignOut,
                )
            }
            item {
                AdminMetricRow(
                    items = listOf(
                        "Recipes" to "Backend",
                        "Prices" to "Admin rules",
                        "Admin" to "Verified",
                    )
                )
            }
            item {
                AdminSectionTitle("Priority Maintenance")
            }
            items(primaryCards.chunked(if (twoColumns) 2 else 1)) { rowCards ->
                AdminCardRow(rowCards, twoColumns)
            }
            item {
                AdminSectionTitle("Review & Validation")
            }
            items(secondaryCards.chunked(if (twoColumns) 2 else 1)) { rowCards ->
                AdminCardRow(rowCards, twoColumns)
            }
            item {
                AdminNoteCard(
                    icon = Icons.Filled.Warning,
                    title = "Maintenance boundary",
                    body = "This mobile admin area edits recipe and price-rule datasets through authenticated backend CRUD. Constraint, schema, and methodology screens remain review-only because they protect solver behavior.",
                    status = "Backend sync enabled",
                )
            }
        }
    }
}

@Composable
fun OperatorRecipeDatasetScreen(
    mealPlanViewModel: MealPlanViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var noticeMessage by remember { mutableStateOf<String?>(null) }
    var recipes by remember { mutableStateOf<List<RecipeDetailDto>>(emptyList()) }
    var selectedMealType by rememberSaveable { mutableStateOf("All") }
    var selectedStatus by rememberSaveable { mutableStateOf("All") }
    var query by rememberSaveable { mutableStateOf("") }
    var reloadNonce by remember { mutableStateOf(0) }
    var editorState by remember { mutableStateOf<RecipeEditorState?>(null) }
    var deleteTarget by remember { mutableStateOf<RecipeDetailDto?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(mealPlanViewModel, reloadNonce) {
        loading = true
        errorMessage = null
        mealPlanViewModel.getAdminRecipes(limit = 500)
            .onSuccess { recipes = it.sortedWith(compareBy({ mealTypeRank(it.mealType) }, { it.title })) }
            .onFailure { error -> errorMessage = error.message ?: "Admin recipe dataset review is unavailable." }
        loading = false
    }

    val mealLabels = remember(recipes) {
        recipes
            .mapNotNull { normalizeMealType(it.mealType) }
            .distinct()
            .sortedWith(compareBy({ mealTypeRank(it) }, { it }))
    }
    val unsupportedMealTypes = remember(recipes) { recipes.mapNotNull { normalizeMealType(it.mealType) }.filterNot(::isSupportedMealType).toSet() }
    val filtered = recipes.filter { recipe ->
        val mealType = normalizeMealType(recipe.mealType)
        val needsReview = recipe.ingredients.isEmpty() ||
            recipe.calories == null ||
            recipe.proteinGrams == null ||
            recipe.carbsGrams == null ||
            recipe.fatsGrams == null ||
            mealType == null ||
            !isSupportedMealType(mealType)
        val matchesMeal = selectedMealType == "All" || mealType.equals(selectedMealType, ignoreCase = true)
        val matchesStatus = selectedStatus == "All" || (selectedStatus == "Needs review" && needsReview) || (selectedStatus == "Complete" && !needsReview)
        val matchesQuery = query.isBlank() ||
            recipe.title.contains(query, ignoreCase = true) ||
            recipe.id.contains(query, ignoreCase = true) ||
            recipe.ingredients.any { it.name.contains(query, ignoreCase = true) }
        matchesMeal && matchesStatus && matchesQuery
    }

    AdminReviewScaffold(
        title = "Meal & Recipe Management",
        subtitle = "Authenticated review of real backend recipe records and supported meal labels.",
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            AdminNoteCard(
                icon = Icons.Filled.Info,
                title = "Backend-synced editor",
                body = "Recipes load from /admin/recipes. New, edit, and delete actions persist to the backend and reload the dataset after save. Keep edits factual because this dataset feeds planner feasibility, grocery aggregation, and macro reporting.",
                status = "CRUD enabled",
            )
        }
        noticeMessage?.let { notice ->
            item {
                AdminNoteCard(
                    icon = Icons.Filled.CheckCircle,
                    title = "Last action",
                    body = notice,
                    status = "Synced",
                )
            }
        }
        item {
            if (unsupportedMealTypes.isEmpty()) {
                AdminNoteCard(
                    icon = Icons.Filled.CheckCircle,
                    title = "Meal labels",
                    body = "Loaded dataset uses supported labels only: Breakfast, Lunch, Dinner, and Universal when present. Snack is not shown as an admin filter because the current mobile plan flow uses three main meals.",
                    status = "Snack removed",
                )
            } else {
                AdminNoteCard(
                    icon = Icons.Filled.Warning,
                    title = "Unsupported meal labels found",
                    body = "Dataset includes: ${unsupportedMealTypes.joinToString()}. Review backend data before using these records in the mobile planner.",
                    status = "Needs review",
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        noticeMessage = null
                        editorState = newRecipeEditorState()
                    },
                    enabled = !saving,
                ) {
                    Text("New recipe")
                }
                OutlinedButton(
                    onClick = {
                        noticeMessage = null
                        reloadNonce += 1
                    },
                    enabled = !saving,
                ) {
                    Text("Reload")
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                label = { Text("Search recipe, ID, or ingredient") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            FilterButtonRow(
                values = listOf("All") + mealLabels,
                selected = selectedMealType,
                onSelect = { selectedMealType = it },
            )
        }
        item {
            FilterButtonRow(
                values = listOf("All", "Needs review", "Complete"),
                selected = selectedStatus,
                onSelect = { selectedStatus = it },
            )
        }
        item {
            AdminMetricRow(
                items = listOf(
                    "Loaded" to recipes.size.toString(),
                    "Shown" to filtered.size.toString(),
                    "Editable" to "Mobile + backend",
                )
            )
        }
        if (loading) {
            item { LoadingCard("Loading admin recipe records") }
        } else if (errorMessage != null && recipes.isEmpty()) {
            item {
                AdminNoteCard(
                    icon = Icons.Filled.Warning,
                    title = "Recipe dataset unavailable",
                    body = errorMessage.orEmpty(),
                    status = "Admin auth needed",
                )
            }
        } else {
            items(filtered.take(120), key = { it.id }) { recipe ->
                RecipeReviewCard(
                    recipe = recipe,
                    onEdit = {
                        noticeMessage = null
                        editorState = recipe.toRecipeEditorState()
                    },
                    onDelete = {
                        noticeMessage = null
                        deleteTarget = recipe
                    },
                    saving = saving,
                )
            }
        }
    }

    editorState?.let { state ->
        RecipeEditorDialog(
            state = state,
            saving = saving,
            onStateChange = { editorState = it },
            onDismiss = {
                if (!saving) editorState = null
            },
            onSave = recipeSave@{
                val current = editorState ?: return@recipeSave
                val payload = current.toRecipeUpsertOrError()
                val request = payload.getOrElse { error ->
                    noticeMessage = error.message ?: "Recipe form is invalid."
                    return@recipeSave
                }
                saving = true
                scope.launch {
                    mealPlanViewModel.saveAdminRecipe(request)
                        .onSuccess { saved ->
                            noticeMessage = "Recipe '${saved.title}' saved to backend."
                            editorState = null
                            reloadNonce += 1
                        }
                        .onFailure { error ->
                            noticeMessage = error.message ?: "Recipe save failed."
                        }
                    saving = false
                }
            },
        )
    }

    deleteTarget?.let { recipe ->
        ConfirmDeleteDialog(
            title = "Delete recipe?",
            body = "Delete '${recipe.title}' from the backend recipe dataset? This cannot be undone from mobile.",
            saving = saving,
            onDismiss = {
                if (!saving) deleteTarget = null
            },
            onConfirm = {
                saving = true
                scope.launch {
                    mealPlanViewModel.deleteAdminRecipe(recipe.id)
                        .onSuccess {
                            noticeMessage = "Recipe '${recipe.title}' deleted from backend."
                            deleteTarget = null
                            reloadNonce += 1
                        }
                        .onFailure { error ->
                            noticeMessage = error.message ?: "Recipe delete failed."
                        }
                    saving = false
                }
            },
        )
    }
}

@Composable
fun OperatorPriceManagementScreen(
    mealPlanViewModel: MealPlanViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var noticeMessage by remember { mutableStateOf<String?>(null) }
    var priceRows by remember { mutableStateOf<List<AdminPriceReviewRow>>(emptyList()) }
    var selectedCategory by rememberSaveable { mutableStateOf("All") }
    var selectedStatus by rememberSaveable { mutableStateOf("All") }
    var query by rememberSaveable { mutableStateOf("") }
    var reloadNonce by remember { mutableStateOf(0) }
    var editorState by remember { mutableStateOf<PriceRuleEditorState?>(null) }
    var deleteTarget by remember { mutableStateOf<AdminPriceReviewRow?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(mealPlanViewModel, reloadNonce) {
        loading = true
        errorMessage = null
        mealPlanViewModel.getAdminPriceRules(limit = 500)
            .onSuccess { rules ->
                priceRows = rules.map(::adminPriceRuleToReviewRow)
                    .sortedWith(compareBy({ it.category }, { it.keywords.firstOrNull().orEmpty() }))
            }
            .onFailure { error ->
                errorMessage = error.message ?: "Backend price rules are unavailable."
                priceRows = PriceCatalog.reviewRules().map(::localPriceRuleToReviewRow)
                    .sortedWith(compareBy({ it.category }, { it.keywords.firstOrNull().orEmpty() }))
            }
        loading = false
    }

    val categories = remember(priceRows) { listOf("All") + priceRows.map { it.category }.distinct().sorted() }
    val filtered = priceRows.filter { row ->
        val matchesCategory = selectedCategory == "All" || row.category == selectedCategory
        val matchesStatus = selectedStatus == "All" ||
            (selectedStatus == "Active" && row.active) ||
            (selectedStatus == "Inactive" && !row.active)
        val matchesQuery = query.isBlank() ||
            row.category.contains(query, ignoreCase = true) ||
            row.unit.contains(query, ignoreCase = true) ||
            row.keywords.any { it.contains(query, ignoreCase = true) }
        matchesCategory && matchesStatus && matchesQuery
    }

    AdminReviewScaffold(
        title = "Ingredient & Price Management",
        subtitle = "Review real price-rule data, canonical keywords, categories, units, and estimate coverage.",
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            AdminNoteCard(
                icon = Icons.Filled.Warning,
                title = "Estimated pricing boundary",
                body = "Prices are estimates and may vary by store, location, and date. Mobile edits persist to backend price rules and reload the operator dataset after save.",
                status = if (errorMessage == null) "Backend data" else "Local fallback",
            )
        }
        noticeMessage?.let { notice ->
            item {
                AdminNoteCard(
                    icon = Icons.Filled.CheckCircle,
                    title = "Last action",
                    body = notice,
                    status = "Synced",
                )
            }
        }
        if (errorMessage != null) {
            item {
                AdminNoteCard(
                    icon = Icons.Filled.Info,
                    title = "Backend price rules not loaded",
                    body = "${errorMessage.orEmpty()} Showing Android local baseline rules so maintainers can still review budgeting behavior.",
                    status = "Fallback review",
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        noticeMessage = null
                        editorState = newPriceRuleEditorState()
                    },
                    enabled = !saving && errorMessage == null,
                ) {
                    Text("New rule")
                }
                OutlinedButton(
                    onClick = {
                        noticeMessage = null
                        reloadNonce += 1
                    },
                    enabled = !saving,
                ) {
                    Text("Reload")
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                label = { Text("Search ingredient, category, or unit") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            FilterButtonRow(
                values = categories,
                selected = selectedCategory,
                onSelect = { selectedCategory = it },
            )
        }
        item {
            FilterButtonRow(
                values = listOf("All", "Active", "Inactive"),
                selected = selectedStatus,
                onSelect = { selectedStatus = it },
            )
        }
        item {
            AdminMetricRow(
                items = listOf(
                    "Rules" to priceRows.size.toString(),
                    "Shown" to filtered.size.toString(),
                    "Ranges" to priceRows.count { it.hasPersistedRange }.toString(),
                    "Persistence" to if (errorMessage == null) "Mobile + backend" else "Local fallback",
                )
            )
        }
        if (loading) {
            item { LoadingCard("Loading price rules") }
        } else {
            items(filtered, key = { it.id ?: it.category + it.keywords.joinToString("|") }) { rule ->
                PriceRuleReviewCard(
                    rule = rule,
                    onEdit = if (rule.id != null && errorMessage == null) {
                        {
                            noticeMessage = null
                            editorState = rule.toPriceRuleEditorState()
                        }
                    } else {
                        null
                    },
                    onDelete = if (rule.id != null && errorMessage == null) {
                        {
                            noticeMessage = null
                            deleteTarget = rule
                        }
                    } else {
                        null
                    },
                    saving = saving,
                )
            }
        }
    }

    editorState?.let { state ->
        PriceRuleEditorDialog(
            state = state,
            saving = saving,
            onStateChange = { editorState = it },
            onDismiss = {
                if (!saving) editorState = null
            },
            onSave = priceSave@{
                val current = editorState ?: return@priceSave
                val payload = current.toPriceRuleUpsertOrError()
                val request = payload.getOrElse { error ->
                    noticeMessage = error.message ?: "Price rule form is invalid."
                    return@priceSave
                }
                saving = true
                scope.launch {
                    mealPlanViewModel.saveAdminPriceRule(request)
                        .onSuccess { saved ->
                            noticeMessage = "Price rule '${saved.keywords.joinToString()}' saved to backend."
                            editorState = null
                            reloadNonce += 1
                        }
                        .onFailure { error ->
                            noticeMessage = error.message ?: "Price rule save failed."
                        }
                    saving = false
                }
            },
        )
    }

    deleteTarget?.let { rule ->
        ConfirmDeleteDialog(
            title = "Delete price rule?",
            body = "Delete '${rule.keywords.joinToString(", ")}' from the backend price dataset? This cannot be undone from mobile.",
            saving = saving,
            onDismiss = {
                if (!saving) deleteTarget = null
            },
            onConfirm = priceDelete@{
                val ruleId = rule.id ?: return@priceDelete
                saving = true
                scope.launch {
                    mealPlanViewModel.deleteAdminPriceRule(ruleId)
                        .onSuccess {
                            noticeMessage = "Price rule '${rule.keywords.joinToString()}' deleted from backend."
                            deleteTarget = null
                            reloadNonce += 1
                        }
                        .onFailure { error ->
                            noticeMessage = error.message ?: "Price rule delete failed."
                        }
                    saving = false
                }
            },
        )
    }
}

@Composable
fun OperatorGroceryPantryReviewScreen(
    mealPlanViewModel: MealPlanViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var recipes by remember { mutableStateOf<List<RecipeDetailDto>>(emptyList()) }
    var priceRows by remember { mutableStateOf<List<AdminPriceReviewRow>>(emptyList()) }
    var selectedStatus by rememberSaveable { mutableStateOf("All") }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(mealPlanViewModel) {
        loading = true
        errorMessage = null
        val recipeResult = mealPlanViewModel.getAdminRecipes(limit = 500)
        val priceResult = mealPlanViewModel.getAdminPriceRules(limit = 500)
        recipes = recipeResult.getOrElse { emptyList() }
        priceRows = priceResult.getOrElse { emptyList() }.map(::adminPriceRuleToReviewRow)
        if (priceRows.isEmpty()) {
            priceRows = PriceCatalog.reviewRules().map(::localPriceRuleToReviewRow)
        }
        errorMessage = recipeResult.exceptionOrNull()?.message
        loading = false
    }

    val rows = remember(recipes, priceRows) { buildGroceryMappingRows(recipes, priceRows) }
    val filtered = rows.filter { row ->
        val missingPrice = row.priceStatus != "Direct price rule"
        val matchesStatus = selectedStatus == "All" ||
            (selectedStatus == "Missing price" && missingPrice) ||
            (selectedStatus == "Mapped" && !missingPrice)
        val matchesQuery = query.isBlank() ||
            row.canonicalName.contains(query, ignoreCase = true) ||
            row.rawExamples.any { it.contains(query, ignoreCase = true) } ||
            row.category.contains(query, ignoreCase = true)
        matchesStatus && matchesQuery
    }

    AdminReviewScaffold(
        title = "Grocery & Pantry Review",
        subtitle = "Review how recipe ingredients are canonicalized, grouped, priced, and prepared for grocery aggregation.",
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            AdminNoteCard(
                icon = Icons.Filled.Info,
                title = "Separate from price management",
                body = "Ingredient & Price Management reviews master price rules. This screen reviews computed behavior: raw recipe ingredient names, canonical names, direct price matches, and grocery aggregation risks. Pantry quantities are user-specific and are not exposed in admin review.",
                status = "Computed review",
            )
        }
        if (errorMessage != null) {
            item {
                AdminNoteCard(
                    icon = Icons.Filled.Warning,
                    title = "Recipe source unavailable",
                    body = errorMessage.orEmpty(),
                    status = "Needs admin auth",
                )
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                label = { Text("Search raw, canonical, or category") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            FilterButtonRow(
                values = listOf("All", "Missing price", "Mapped"),
                selected = selectedStatus,
                onSelect = { selectedStatus = it },
            )
        }
        item {
            AdminMetricRow(
                items = listOf(
                    "Canonical" to rows.size.toString(),
                    "Shown" to filtered.size.toString(),
                    "Gaps" to rows.count { it.priceStatus != "Direct price rule" }.toString(),
                )
            )
        }
        if (loading) {
            item { LoadingCard("Loading grocery mapping review") }
        } else {
            items(filtered.take(140), key = { it.canonicalKey }) { row ->
                GroceryMappingCard(row = row)
            }
        }
    }
}

@Composable
fun OperatorRulesScreen(
    onBack: () -> Unit,
    onOpenMethodology: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hardRules = listOf(
        "Allergies, restrictions, and excluded ingredients are filtered before candidate selection.",
        "Budget ceilings are enforced when the user provides a budget.",
        "Meal slots are constrained to supported labels used by the active planner flow.",
        "Nutrition targets and safety bounds are applied where profile inputs provide enough data.",
        "Cooking-time limits can remove candidates that exceed the user's stated time preference.",
    )
    val softRules = listOf(
        "Pantry overlap is used as a preference/reward so feasible pantry-friendly meals are favored.",
        "Variety and repetition rules reduce repeated recipes across the weekly plan.",
        "Preference matches and explainability reasons can rank otherwise feasible candidates.",
    )
    val fallbackRules = listOf(
        "If no safe plan is found, the app should explain infeasibility and suggest relaxing constraints.",
        "ML is assistive only and must not override allergies, exclusions, budget ceilings, or nutritional bounds.",
        "The app remains a wellness decision-support prototype and does not diagnose or treat PCOS.",
    )

    AdminReviewScaffold(
        title = "Nutrition & Constraint Rules",
        subtitle = "Clear review of hard rules, soft priorities, and fallback behavior.",
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            AdminNoteCard(
                icon = Icons.Filled.CheckCircle,
                title = "Planning contract",
                body = "PCOSINA uses deterministic filtering and optimizer-backed meal selection. Hard constraints stay non-negotiable.",
                status = "Defense-safe",
            )
        }
        item { AdminSectionTitle("Hard Constraints") }
        items(hardRules, key = { it }) { body ->
            AdminNoteCard(Icons.Filled.Tune, "Hard constraint", body, "Hard")
        }
        item { AdminSectionTitle("Soft Objectives") }
        items(softRules, key = { it }) { body ->
            AdminNoteCard(Icons.Filled.Info, "Optimization preference", body, "Soft")
        }
        item { AdminSectionTitle("Fallback & Safety") }
        items(fallbackRules, key = { it }) { body ->
            AdminNoteCard(Icons.Filled.Warning, "Boundary", body, "Safety")
        }
        item {
            Button(
                onClick = onOpenMethodology,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PcosinaDeepRose)
            ) {
                Text("Open methodology pipeline", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
fun OperatorMethodologyPipelineScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pipeline = listOf(
        "User profile and preferences" to "Age, household size, budget, restrictions, pantry items, cooking time, and goals are collected through the client flow.",
        "Dataset preprocessing" to "Recipes, ingredient names, price rules, and pantry terms are normalized into planner-friendly records.",
        "Stage 1 filtering" to "The system removes candidates that violate hard constraints such as allergies, exclusions, meal type, budget, and feasibility rules.",
        "Stage 2 optimization" to "The optimizer selects a weekly set of feasible meals using nutrition, budget, pantry, and variety objectives.",
        "Plan and grocery generation" to "Selected recipes become a weekly plan, then ingredients are canonicalized and aggregated into a grocery list.",
        "Self-reported progress" to "Meal logs record whether meals were marked as eaten. This is adherence tracking, not clinical outcome tracking.",
    )
    val boundaries = listOf(
        "Local" to "Saved profile, pantry, reminders, progress logs, and cached plan state are handled on-device.",
        "Backend" to "Plan generation, recipe data, admin recipe records, admin price rules, schema checks, and authenticated admin access are served by the backend.",
        "Firebase" to "Google identity and Firebase ID tokens authorize normal and admin requests. Raw tokens are never shown in the UI.",
    )

    AdminReviewScaffold(
        title = "Methodology & Pipeline",
        subtitle = "Two-stage planning flow, data boundaries, and validation-safe wording.",
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            AdminNoteCard(
                icon = Icons.Filled.Info,
                title = "Scope",
                body = "This screen is for admin/developer validation. It explains system behavior without claiming medical diagnosis, treatment, or clinical improvement.",
                status = "Methodology",
            )
        }
        item { AdminSectionTitle("Planning Flow") }
        items(pipeline, key = { it.first }) { (title, body) ->
            AdminNoteCard(Icons.Filled.CheckCircle, title, body, "Pipeline")
        }
        item { AdminSectionTitle("Online / Offline Boundary") }
        items(boundaries, key = { it.first }) { (title, body) ->
            AdminNoteCard(Icons.Filled.Link, title, body, "Boundary")
        }
    }
}

@Composable
fun OperatorSystemInfoScreen(
    mealPlanViewModel: MealPlanViewModel,
    signedInEmail: String?,
    backendBaseUrl: String,
    schemaVersion: String,
    buildType: String,
    operatorVerified: Boolean,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var recipes by remember { mutableStateOf<List<RecipeDetailDto>>(emptyList()) }
    var priceRows by remember { mutableStateOf<List<AdminPriceReviewRow>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(mealPlanViewModel) {
        loading = true
        val recipeResult = mealPlanViewModel.getAdminRecipes(limit = 500)
        val priceResult = mealPlanViewModel.getAdminPriceRules(limit = 500)
        recipes = recipeResult.getOrElse { emptyList() }
        priceRows = priceResult.getOrElse { emptyList() }.map(::adminPriceRuleToReviewRow)
        errorMessage = recipeResult.exceptionOrNull()?.message ?: priceResult.exceptionOrNull()?.message
        loading = false
    }

    val notificationStatus = remember {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            "Not required before Android 13"
        } else if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            "Granted"
        } else {
            "Not granted"
        }
    }
    val health = remember(recipes, priceRows) {
        buildDatasetHealth(recipes, priceRows)
    }
    val systemItems = listOf(
        "Admin" to if (operatorVerified) "Verified by backend" else "Not verified",
        "Signed in" to signedInEmail.orEmpty().ifBlank { "Google account unavailable" },
        "Backend" to backendBaseUrl.trim().trimEnd('/').ifBlank { "Not configured" },
        "Schema" to schemaVersion,
        "Build" to buildType,
        "Notifications" to notificationStatus,
        "Auth source" to "Firebase Google token",
        "Secrets" to "Not displayed in mobile UI",
    )

    AdminReviewScaffold(
        title = "System Data & Schema",
        subtitle = "Lightweight admin status, dataset counts, and maintenance warnings.",
        onBack = onBack,
        modifier = modifier,
    ) {
        if (loading) {
            item { LoadingCard("Loading dataset status") }
        }
        item {
            AdminMetricRow(
                items = listOf(
                    "Recipes" to health.recipeCount.toString(),
                    "Prices" to health.priceRuleCount.toString(),
                    "Missing price" to health.ingredientsWithoutDirectPrice.toString(),
                )
            )
        }
        if (errorMessage != null) {
            item {
                AdminNoteCard(Icons.Filled.Warning, "Dataset status limited", errorMessage.orEmpty(), "Needs admin auth")
            }
        }
        if (health.unsupportedMealTypes.isNotEmpty()) {
            item {
                AdminNoteCard(
                    icon = Icons.Filled.Warning,
                    title = "Unsupported meal labels",
                    body = health.unsupportedMealTypes.joinToString(),
                    status = "Dataset warning",
                )
            }
        }
        item {
            AdminMetricRow(
                items = listOf(
                    "No ingredients" to health.recipesMissingIngredients.toString(),
                    "No nutrition" to health.recipesMissingNutrition.toString(),
                    "Unsupported" to health.unsupportedMealTypes.size.toString(),
                )
            )
        }
        items(systemItems, key = { it.first }) { item ->
            SystemInfoRow(label = item.first, value = item.second)
        }
        item {
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Text(
                    text = "Sign out / switch account",
                    modifier = Modifier.padding(start = 8.dp),
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
fun OperatorAdminSettingsScreen(
    signedInEmail: String?,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AdminReviewScaffold(
        title = "Admin Settings",
        subtitle = "Account switching, security boundaries, and mobile maintenance notes.",
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            AdminNoteCard(
                icon = Icons.Filled.CheckCircle,
                title = "Admin access verified",
                body = "Signed in as: ${signedInEmail.orEmpty().ifBlank { "Google account" }}",
                status = "Backend-authorized",
            )
        }
        item {
            AdminNoteCard(
                icon = Icons.Filled.Warning,
                title = "Route separation",
                body = "Admin accounts stay in Admin Dashboard routes. Normal client onboarding, plan, grocery, and progress screens are guarded from becoming the primary admin flow.",
                status = "Security",
            )
        }
        item {
            AdminNoteCard(
                icon = Icons.Filled.Info,
                title = "Persistent editing",
                body = "Backend CRUD exists for recipes and price rules. Mobile editing should add validation, confirmation, and audit-safe save UX before being enabled.",
                status = "Future mobile edit",
            )
        }
        item {
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Text(
                    text = "Sign out / switch account",
                    modifier = Modifier.padding(start = 8.dp),
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun AdminCardRow(rowCards: List<AdminDashboardCardModel>, twoColumns: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        rowCards.forEach { card ->
            AdminToolCard(
                model = card,
                modifier = Modifier.weight(1f),
            )
        }
        if (twoColumns && rowCards.size == 1) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun AdminHeroHeader(
    title: String,
    subtitle: String,
    signedInEmail: String?,
    badge: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.16f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PcosinaBlushSurface.copy(alpha = 0.35f))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = PcosinaDeepRose,
                    contentColor = Color.White,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(25.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = PcosinaDeepRose,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PcosinaMuted,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(text = badge, containerColor = PcosinaSuccess.copy(alpha = 0.12f), contentColor = PcosinaSuccess)
                Text(
                    text = signedInEmail.orEmpty().ifBlank { "Google admin account" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = PcosinaMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AdminReviewScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PcosinaSurface)
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Box {
                AdminHeroHeader(
                    title = title,
                    subtitle = subtitle,
                    signedInEmail = null,
                    badge = "Admin tools",
                )
                IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PcosinaDeepRose,
                    )
                }
            }
        }
        content()
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun AdminIdentityCard(
    signedInEmail: String?,
    backendBaseUrl: String,
    schemaVersion: String,
    buildType: String,
    onSignOut: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.18f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = PcosinaSuccess.copy(alpha = 0.12f),
                    contentColor = PcosinaSuccess,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Admin access verified",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = PcosinaDeepRose,
                    )
                    Text(
                        text = "Signed in as: ${signedInEmail.orEmpty().ifBlank { "Google account" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PcosinaMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            AdminMetricRow(
                items = listOf(
                    "Backend" to backendBaseUrl.trim().trimEnd('/').substringAfter("https://"),
                    "Schema" to schemaVersion,
                    "Build" to buildType,
                )
            )
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Text(
                    text = "Sign out / switch account",
                    modifier = Modifier.padding(start = 8.dp),
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun AdminToolCard(
    model: AdminDashboardCardModel,
    modifier: Modifier = Modifier,
) {
    val accent = when {
        model.status.contains("P0") -> PcosinaDeepRose
        model.status.contains("P1") -> PcosinaInfo
        model.status.contains("Future") -> PcosinaMuted
        else -> PcosinaSuccess
    }
    Card(
        modifier = modifier.fillMaxWidth().heightIn(min = 198.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.20f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.11f),
                    contentColor = accent,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
                ) {
                    Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        Icon(model.icon, contentDescription = null, modifier = Modifier.size(21.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(text = model.status, containerColor = accent.copy(alpha = 0.10f), contentColor = accent)
                    Text(
                        text = model.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = PcosinaDeepRose,
                    )
                }
            }
            Text(
                text = model.body,
                style = MaterialTheme.typography.bodySmall,
                color = PcosinaMuted,
            )
            Button(
                onClick = model.onClick,
                enabled = model.enabled,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PcosinaDeepRose,
                    disabledContainerColor = PcosinaSurfaceAlt,
                    disabledContentColor = PcosinaMuted,
                ),
            ) {
                Text(model.actionLabel, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun AdminNoteCard(
    icon: ImageVector,
    title: String,
    body: String,
    status: String,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.16f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = CircleShape,
                color = PcosinaBlushSurface,
                contentColor = PcosinaDeepRose,
            ) {
                Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(text = status)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose,
                )
                Text(text = body, style = MaterialTheme.typography.bodySmall, color = PcosinaMuted)
            }
        }
    }
}

@Composable
private fun RecipeReviewCard(
    recipe: RecipeDetailDto,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    saving: Boolean,
) {
    val mealType = normalizeMealType(recipe.mealType) ?: "Unlabeled"
    val nutrition = listOfNotNull(
        recipe.calories?.let { "Cal $it" },
        recipe.proteinGrams?.let { "Protein ${it}g" },
        recipe.carbsGrams?.let { "Carbs ${it}g" },
        recipe.fatsGrams?.let { "Fat ${it}g" },
        recipe.fiberGrams?.let { "Fiber ${it}g" },
    ).joinToString(" | ").ifBlank { "Nutrition incomplete" }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.14f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = recipe.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose,
                )
                StatusPill(text = mealType, containerColor = PcosinaSurfaceAlt)
            }
            Text(
                text = "ID: ${recipe.id}",
                style = MaterialTheme.typography.labelSmall,
                color = PcosinaMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AdminMetricRow(
                items = listOf(
                    "Time" to (recipe.minutes?.let { "$it min" } ?: "Not set"),
                    "Ingredients" to recipe.ingredients.size.toString(),
                    "Steps" to recipe.steps.size.toString(),
                )
            )
            Text(text = nutrition, style = MaterialTheme.typography.bodySmall, color = PcosinaMuted)
            if (recipe.ingredients.isNotEmpty()) {
                Text(
                    text = recipe.ingredients.take(4).joinToString { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = PcosinaMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (recipe.tags.isNotEmpty()) {
                Text(
                    text = "Tags: ${recipe.tags.take(5).joinToString()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = PcosinaMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onEdit, enabled = !saving) {
                    Text("Edit")
                }
                OutlinedButton(onClick = onDelete, enabled = !saving) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun PriceRuleReviewCard(
    rule: AdminPriceReviewRow,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    saving: Boolean,
) {
    val estimateLabel = if (rule.hasPersistedRange) "Range midpoint" else "Estimate"
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PcosinaInfo.copy(alpha = 0.16f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = rule.keywords.joinToString(", "),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose,
                )
                StatusPill(text = if (rule.active) "Active" else "Inactive", containerColor = if (rule.active) PcosinaSuccess.copy(alpha = 0.10f) else PcosinaSurfaceAlt)
            }
            AdminMetricRow(
                items = listOf(
                    estimateLabel to "PHP ${rule.pricePhp}",
                    "Range" to rule.priceRangeLabel,
                    "Unit" to rule.unit,
                )
            )
            Text(
                text = "${rule.category} | ${rule.source}",
                style = MaterialTheme.typography.bodySmall,
                color = PcosinaMuted,
            )
            if (!rule.notes.isNullOrBlank()) {
                Text(
                    text = rule.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = PcosinaMuted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onEdit?.invoke() },
                    enabled = !saving && onEdit != null,
                ) {
                    Text("Edit")
                }
                OutlinedButton(
                    onClick = { onDelete?.invoke() },
                    enabled = !saving && onDelete != null,
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun RecipeEditorDialog(
    state: RecipeEditorState,
    saving: Boolean,
    onStateChange: (RecipeEditorState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.existingId == null) "New recipe" else "Edit recipe") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 540.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = state.id,
                    onValueChange = { onStateChange(state.copy(id = it)) },
                    label = { Text("Recipe ID") },
                    singleLine = true,
                    enabled = state.existingId == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.title,
                    onValueChange = { onStateChange(state.copy(title = it)) },
                    label = { Text("Meal name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.mealType,
                    onValueChange = { onStateChange(state.copy(mealType = it)) },
                    label = { Text("Meal type: Breakfast, Lunch, Dinner, or Universal") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AdminCompactFieldRow(
                    left = {
                        OutlinedTextField(
                            value = state.calories,
                            onValueChange = { onStateChange(state.copy(calories = it.onlyDigits())) },
                            label = { Text("Calories") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    right = {
                        OutlinedTextField(
                            value = state.minutes,
                            onValueChange = { onStateChange(state.copy(minutes = it.onlyDigits())) },
                            label = { Text("Minutes") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )
                AdminCompactFieldRow(
                    left = {
                        OutlinedTextField(
                            value = state.proteinGrams,
                            onValueChange = { onStateChange(state.copy(proteinGrams = it.onlyDigits())) },
                            label = { Text("Protein g") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    right = {
                        OutlinedTextField(
                            value = state.carbsGrams,
                            onValueChange = { onStateChange(state.copy(carbsGrams = it.onlyDigits())) },
                            label = { Text("Carbs g") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )
                AdminCompactFieldRow(
                    left = {
                        OutlinedTextField(
                            value = state.fatsGrams,
                            onValueChange = { onStateChange(state.copy(fatsGrams = it.onlyDigits())) },
                            label = { Text("Fat g") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    right = {
                        OutlinedTextField(
                            value = state.fiberGrams,
                            onValueChange = { onStateChange(state.copy(fiberGrams = it.onlyDigits())) },
                            label = { Text("Fiber g") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )
                OutlinedTextField(
                    value = state.tags,
                    onValueChange = { onStateChange(state.copy(tags = it)) },
                    label = { Text("Tags, comma separated") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.ingredients,
                    onValueChange = { onStateChange(state.copy(ingredients = it)) },
                    label = { Text("Ingredients, one per line: name | quantity") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.steps,
                    onValueChange = { onStateChange(state.copy(steps = it)) },
                    label = { Text("Steps, one per line") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !saving) {
                Text(if (saving) "Saving..." else "Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !saving) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun PriceRuleEditorDialog(
    state: PriceRuleEditorState,
    saving: Boolean,
    onStateChange: (PriceRuleEditorState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.existingId == null) "New price rule" else "Edit price rule") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = state.id,
                    onValueChange = { onStateChange(state.copy(id = it)) },
                    label = { Text("Rule ID") },
                    singleLine = true,
                    enabled = state.existingId == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.keywords,
                    onValueChange = { onStateChange(state.copy(keywords = it)) },
                    label = { Text("Keywords, comma separated") },
                    modifier = Modifier.fillMaxWidth(),
                )
                AdminCompactFieldRow(
                    left = {
                        OutlinedTextField(
                            value = state.pricePhp,
                            onValueChange = { onStateChange(state.copy(pricePhp = it.onlyDigits())) },
                            label = { Text("Estimate PHP") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    right = {
                        OutlinedTextField(
                            value = state.unit,
                            onValueChange = { onStateChange(state.copy(unit = it)) },
                            label = { Text("Unit") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )
                AdminCompactFieldRow(
                    left = {
                        OutlinedTextField(
                            value = state.priceMinPhp,
                            onValueChange = { onStateChange(state.copy(priceMinPhp = it.onlyDigits())) },
                            label = { Text("Min PHP") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    right = {
                        OutlinedTextField(
                            value = state.priceMaxPhp,
                            onValueChange = { onStateChange(state.copy(priceMaxPhp = it.onlyDigits())) },
                            label = { Text("Max PHP") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )
                OutlinedTextField(
                    value = state.category,
                    onValueChange = { onStateChange(state.copy(category = it)) },
                    label = { Text("Category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.active,
                        onCheckedChange = { onStateChange(state.copy(active = it)) },
                    )
                    Text("Active rule", color = PcosinaDeepRose)
                }
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = { onStateChange(state.copy(notes = it)) },
                    label = { Text("Notes / source") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Prices are estimates and may vary by store, location, and date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PcosinaMuted,
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !saving) {
                Text(if (saving) "Saving..." else "Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !saving) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    body: String,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, color = PcosinaDeepRose) },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !saving) {
                Text(if (saving) "Deleting..." else "Delete")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !saving) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun AdminCompactFieldRow(
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(modifier = Modifier.weight(1f)) { left() }
        Box(modifier = Modifier.weight(1f)) { right() }
    }
}

@Composable
private fun GroceryMappingCard(row: GroceryMappingReviewRow) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.14f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = row.canonicalName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose,
                )
                StatusPill(
                    text = row.priceStatus,
                    containerColor = if (row.priceStatus == "Direct price rule") PcosinaSuccess.copy(alpha = 0.10f) else PcosinaBlushSurface,
                    contentColor = if (row.priceStatus == "Direct price rule") PcosinaSuccess else PcosinaDeepRose,
                )
            }
            AdminMetricRow(
                items = listOf(
                    "Category" to row.category,
                    "Unit" to row.unit,
                    "Cost" to "PHP ${row.pricePhp}",
                )
            )
            Text(
                text = "Raw examples: ${row.rawExamples.joinToString()}",
                style = MaterialTheme.typography.bodySmall,
                color = PcosinaMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Quantity examples: ${row.quantityExamples.joinToString()}",
                style = MaterialTheme.typography.bodySmall,
                color = PcosinaMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SystemInfoRow(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(0.42f),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = PcosinaDeepRose,
            )
            Text(
                text = value,
                modifier = Modifier.weight(0.58f),
                style = MaterialTheme.typography.bodySmall,
                color = PcosinaMuted,
            )
        }
    }
}

@Composable
private fun AdminMetricRow(items: List<Pair<String, String>>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (label, value) ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = PcosinaSurfaceAlt,
                border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.12f)),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = PcosinaMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = PcosinaDeepRose,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterButtonRow(
    values: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 4.dp),
    ) {
        items(values, key = { it }) { value ->
            val isSelected = value == selected
            Surface(
                modifier = Modifier
                    .heightIn(min = 42.dp)
                    .clickable { onSelect(value) },
                shape = RoundedCornerShape(999.dp),
                color = if (isSelected) PcosinaDeepRose else Color.White,
                contentColor = if (isSelected) Color.White else PcosinaDeepRose,
                border = BorderStroke(1.dp, PcosinaPink.copy(alpha = if (isSelected) 0.0f else 0.22f)),
            ) {
                Text(
                    text = value,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LoadingCard(message: String) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 3.dp)
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = PcosinaMuted)
        }
    }
}

@Composable
private fun AdminSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
        color = PcosinaDeepRose,
    )
}

@Composable
private fun StatusPill(
    text: String,
    containerColor: Color = PcosinaBlushSurface,
    contentColor: Color = PcosinaDeepRose,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.14f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun adminPriceRuleToReviewRow(rule: AdminPriceRuleDto): AdminPriceReviewRow =
    AdminPriceReviewRow(
        id = rule.id,
        keywords = rule.keywords.filter { it.isNotBlank() }.ifEmpty { listOf(rule.id) },
        pricePhp = rule.pricePhp.coerceAtLeast(0),
        priceMinPhp = rule.priceMinPhp?.coerceAtLeast(0),
        priceMaxPhp = rule.priceMaxPhp?.coerceAtLeast(0),
        category = rule.category.ifBlank { "Uncategorized" },
        unit = rule.unit?.takeIf { it.isNotBlank() } ?: "unit not set",
        active = rule.active,
        notes = rule.notes,
        source = "Backend admin price rule",
    )

private fun localPriceRuleToReviewRow(rule: PriceRule): AdminPriceReviewRow =
    AdminPriceReviewRow(
        id = null,
        keywords = rule.keywords.filter { it.isNotBlank() },
        pricePhp = rule.pricePhp.coerceAtLeast(0),
        priceMinPhp = null,
        priceMaxPhp = null,
        category = rule.category.ifBlank { "Uncategorized" },
        unit = rule.unit.ifBlank { "unit not set" },
        active = true,
        notes = "${rule.sourceLabel}; confidence: ${rule.confidence}",
        source = "Android local baseline",
    )

private fun newRecipeEditorState(): RecipeEditorState =
    RecipeEditorState(
        existingId = null,
        id = "",
        title = "",
        mealType = "Breakfast",
        calories = "",
        proteinGrams = "",
        carbsGrams = "",
        fatsGrams = "",
        fiberGrams = "",
        minutes = "25",
        tags = "",
        ingredients = "",
        steps = "",
    )

private fun RecipeDetailDto.toRecipeEditorState(): RecipeEditorState =
    RecipeEditorState(
        existingId = id,
        id = id,
        title = title,
        mealType = mealType.orEmpty(),
        calories = calories?.toString().orEmpty(),
        proteinGrams = proteinGrams?.toString().orEmpty(),
        carbsGrams = carbsGrams?.toString().orEmpty(),
        fatsGrams = fatsGrams?.toString().orEmpty(),
        fiberGrams = fiberGrams?.toString().orEmpty(),
        minutes = minutes?.toString().orEmpty(),
        tags = tags.joinToString(", "),
        ingredients = ingredients.joinToString("\n") { "${it.name} | ${it.quantity}" },
        steps = steps.joinToString("\n"),
    )

private fun RecipeEditorState.toRecipeUpsertOrError(): Result<AdminRecipeUpsertDto> {
    val normalizedId = id.trim().ifBlank { null }
    val normalizedTitle = title.trim()
    val normalizedMealType = normalizeMealType(mealType) ?: ""
    if (existingId == null && normalizedId.isNullOrBlank()) {
        return Result.failure(IllegalArgumentException("Recipe ID is required for new recipes."))
    }
    if (normalizedTitle.isBlank()) {
        return Result.failure(IllegalArgumentException("Recipe name is required."))
    }
    if (!isSupportedMealType(normalizedMealType)) {
        return Result.failure(IllegalArgumentException("Meal type must be Breakfast, Lunch, Dinner, or Universal."))
    }
    val parsedCalories = calories.positiveIntOrNull() ?: return Result.failure(IllegalArgumentException("Calories must be greater than 0."))
    val parsedProtein = proteinGrams.positiveIntOrNull() ?: return Result.failure(IllegalArgumentException("Protein must be greater than 0."))
    val parsedCarbs = carbsGrams.positiveIntOrNull() ?: return Result.failure(IllegalArgumentException("Carbs must be greater than 0."))
    val parsedFats = fatsGrams.positiveIntOrNull() ?: return Result.failure(IllegalArgumentException("Fat must be greater than 0."))
    val parsedFiber = fiberGrams.nonNegativeIntOrNull() ?: return Result.failure(IllegalArgumentException("Fiber must be 0 or greater."))
    val parsedMinutes = minutes.positiveIntOrNull() ?: return Result.failure(IllegalArgumentException("Cooking time must be greater than 0."))
    val parsedIngredients = ingredients.lines()
        .mapNotNull { line ->
            val cleaned = line.trim()
            if (cleaned.isBlank()) return@mapNotNull null
            val parts = cleaned.split("|", limit = 2).map { it.trim() }
            IngredientDto(
                name = parts.getOrNull(0).orEmpty(),
                quantity = parts.getOrNull(1).orEmpty(),
            )
        }
        .filter { it.name.isNotBlank() }
    if (parsedIngredients.isEmpty()) {
        return Result.failure(IllegalArgumentException("At least one ingredient is required."))
    }
    val parsedSteps = steps.lines().map { it.trim() }.filter { it.isNotBlank() }
    if (parsedSteps.isEmpty()) {
        return Result.failure(IllegalArgumentException("At least one cooking step is required."))
    }
    return Result.success(
        AdminRecipeUpsertDto(
            id = existingId ?: normalizedId,
            title = normalizedTitle,
            mealType = normalizedMealType,
            calories = parsedCalories,
            proteinGrams = parsedProtein,
            carbsGrams = parsedCarbs,
            fatsGrams = parsedFats,
            fiberGrams = parsedFiber,
            tags = tags.csvItems(),
            minutes = parsedMinutes,
            ingredients = parsedIngredients,
            steps = parsedSteps,
        )
    )
}

private fun newPriceRuleEditorState(): PriceRuleEditorState =
    PriceRuleEditorState(
        existingId = null,
        id = "",
        keywords = "",
        pricePhp = "",
        priceMinPhp = "",
        priceMaxPhp = "",
        category = "Others",
        unit = "",
        active = true,
        notes = "",
    )

private fun AdminPriceReviewRow.toPriceRuleEditorState(): PriceRuleEditorState =
    PriceRuleEditorState(
        existingId = id,
        id = id.orEmpty(),
        keywords = keywords.joinToString(", "),
        pricePhp = pricePhp.toString(),
        priceMinPhp = priceMinPhp?.toString().orEmpty(),
        priceMaxPhp = priceMaxPhp?.toString().orEmpty(),
        category = category,
        unit = unit,
        active = active,
        notes = notes.orEmpty(),
    )

private fun PriceRuleEditorState.toPriceRuleUpsertOrError(): Result<AdminPriceRuleUpsertDto> {
    val normalizedId = id.trim().ifBlank { null }
    val parsedKeywords = keywords.csvItems()
    if (existingId == null && normalizedId.isNullOrBlank()) {
        return Result.failure(IllegalArgumentException("Rule ID is required for new price rules."))
    }
    if (parsedKeywords.isEmpty()) {
        return Result.failure(IllegalArgumentException("At least one keyword is required."))
    }
    val estimate = pricePhp.positiveIntOrNull() ?: return Result.failure(IllegalArgumentException("Estimate price must be greater than 0."))
    var minPrice = priceMinPhp.optionalPositiveIntOrNull()
    var maxPrice = priceMaxPhp.optionalPositiveIntOrNull()
    if (minPrice != null && maxPrice != null && maxPrice < minPrice) {
        val tmp = minPrice
        minPrice = maxPrice
        maxPrice = tmp
    }
    val normalizedCategory = category.trim().ifBlank { "Others" }
    return Result.success(
        AdminPriceRuleUpsertDto(
            id = existingId ?: normalizedId,
            keywords = parsedKeywords,
            pricePhp = estimate,
            priceMinPhp = minPrice,
            priceMaxPhp = maxPrice,
            category = normalizedCategory,
            unit = unit.trim().ifBlank { null },
            active = active,
            notes = notes.trim().ifBlank { null },
        )
    )
}

private fun buildGroceryMappingRows(
    recipes: List<RecipeDetailDto>,
    priceRows: List<AdminPriceReviewRow>,
): List<GroceryMappingReviewRow> {
    val ingredients = recipes.flatMap { recipe ->
        recipe.ingredients.map { ingredient -> ingredient to recipe.title }
    }
    return ingredients
        .filter { (ingredient, _) -> ingredient.name.isNotBlank() }
        .groupBy { (ingredient, _) -> canonicalGroceryKey(ingredient.name).ifBlank { ingredient.name.lowercase(Locale.ENGLISH) } }
        .map { (key, grouped) ->
            val rawNames = grouped.map { it.first.name }.distinct().take(4)
            val quantities = grouped.map { it.first.quantity.ifBlank { "As needed" } }.distinct().take(4)
            val canonicalName = canonicalGroceryName(rawNames.firstOrNull().orEmpty()).ifBlank { rawNames.firstOrNull().orEmpty() }
            val matched = matchPriceRule(canonicalName, priceRows)
            val estimate = PriceCatalog.estimatePriceExplanation(canonicalName, quantities.firstOrNull().orEmpty())
            GroceryMappingReviewRow(
                canonicalKey = key,
                canonicalName = canonicalName,
                rawExamples = rawNames,
                category = matched?.category ?: estimate.category,
                unit = matched?.unit ?: "inferred",
                sourceCount = grouped.size,
                pricePhp = matched?.pricePhp ?: estimate.pricePhp,
                priceStatus = if (matched != null) "Direct price rule" else "Category fallback",
                quantityExamples = quantities,
            )
        }
        .sortedWith(compareBy({ it.priceStatus != "Direct price rule" }, { it.category }, { it.canonicalName }))
}

private fun matchPriceRule(name: String, priceRows: List<AdminPriceReviewRow>): AdminPriceReviewRow? {
    val normalized = name.lowercase(Locale.ENGLISH)
    val key = canonicalGroceryKey(name).lowercase(Locale.ENGLISH)
    return priceRows.firstOrNull { row ->
        row.keywords.any { keyword ->
            val normalizedKeyword = keyword.lowercase(Locale.ENGLISH)
            normalized.contains(normalizedKeyword) || key.contains(normalizedKeyword)
        }
    }
}

private fun buildDatasetHealth(
    recipes: List<RecipeDetailDto>,
    priceRows: List<AdminPriceReviewRow>,
): DatasetHealth {
    val groceryRows = buildGroceryMappingRows(recipes, priceRows)
    return DatasetHealth(
        recipeCount = recipes.size,
        priceRuleCount = priceRows.size,
        unsupportedMealTypes = recipes.mapNotNull { normalizeMealType(it.mealType) }.filterNot(::isSupportedMealType).toSet(),
        recipesMissingIngredients = recipes.count { it.ingredients.isEmpty() },
        recipesMissingNutrition = recipes.count {
            it.calories == null || it.proteinGrams == null || it.carbsGrams == null || it.fatsGrams == null
        },
        ingredientsWithoutDirectPrice = groceryRows.count { it.priceStatus != "Direct price rule" },
    )
}

private fun normalizeMealType(value: String?): String? {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    return trimmed.lowercase(Locale.ENGLISH)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
}

private fun isSupportedMealType(value: String): Boolean =
    value.equals("Breakfast", ignoreCase = true) ||
        value.equals("Lunch", ignoreCase = true) ||
        value.equals("Dinner", ignoreCase = true) ||
        value.equals("Universal", ignoreCase = true)

private fun mealTypeRank(value: String?): Int {
    return when (normalizeMealType(value)) {
        "Breakfast" -> 0
        "Lunch" -> 1
        "Dinner" -> 2
        "Universal" -> 3
        null -> 8
        else -> 9
    }
}

private val AdminPriceReviewRow.hasPersistedRange: Boolean
    get() = priceMinPhp != null && priceMaxPhp != null && priceMaxPhp >= priceMinPhp

private val AdminPriceReviewRow.priceRangeLabel: String
    get() {
        val minPrice = priceMinPhp
        val maxPrice = priceMaxPhp
        return if (minPrice != null && maxPrice != null && maxPrice >= minPrice) {
            "PHP $minPrice-$maxPrice"
        } else {
            "Exact estimate only"
        }
    }

private fun String.onlyDigits(): String = filter { it.isDigit() }

private fun String.csvItems(): List<String> =
    split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

private fun String.positiveIntOrNull(): Int? =
    trim().toIntOrNull()?.takeIf { it > 0 }

private fun String.nonNegativeIntOrNull(): Int? =
    trim().toIntOrNull()?.takeIf { it >= 0 }

private fun String.optionalPositiveIntOrNull(): Int? {
    val clean = trim()
    if (clean.isBlank()) return null
    return clean.toIntOrNull()?.takeIf { it > 0 }
}
