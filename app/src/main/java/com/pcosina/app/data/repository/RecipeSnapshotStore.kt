package com.pcosina.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.pcosina.app.data.api.RecipeDetailDto
import com.pcosina.app.data.api.RecipeSummaryDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

interface RecipeSnapshotStore {
    suspend fun replaceAll(recipes: List<RecipeDetailDto>)
    suspend fun upsert(recipe: RecipeDetailDto)
    suspend fun remove(recipeId: String)
    suspend fun getRecipe(recipeId: String): RecipeDetailDto?
    suspend fun getSummaries(mealType: String? = null, limit: Int = 50): List<RecipeSummaryDto>
}

object NoOpRecipeSnapshotStore : RecipeSnapshotStore {
    override suspend fun replaceAll(recipes: List<RecipeDetailDto>) = Unit
    override suspend fun upsert(recipe: RecipeDetailDto) = Unit
    override suspend fun remove(recipeId: String) = Unit
    override suspend fun getRecipe(recipeId: String): RecipeDetailDto? = null
    override suspend fun getSummaries(mealType: String?, limit: Int): List<RecipeSummaryDto> = emptyList()
}

class FileRecipeSnapshotStore(
    context: Context,
    private val gson: Gson = Gson(),
) : RecipeSnapshotStore {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val snapshotFile: File
        get() = File(appContext.filesDir, SNAPSHOT_FILE_NAME)

    override suspend fun replaceAll(recipes: List<RecipeDetailDto>) {
        val normalized = recipes
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .sortedBy { it.id }
        writeSnapshot(RecipeCatalogSnapshot(System.currentTimeMillis(), normalized))
    }

    override suspend fun upsert(recipe: RecipeDetailDto) {
        if (recipe.id.isBlank()) return
        mutex.withLock {
            val current = readSnapshotUnlocked()
            val updated = (current.recipes.filterNot { it.id == recipe.id } + recipe).sortedBy { it.id }
            writeSnapshotUnlocked(RecipeCatalogSnapshot(System.currentTimeMillis(), updated))
        }
    }

    override suspend fun remove(recipeId: String) {
        val token = recipeId.trim()
        if (token.isBlank()) return
        mutex.withLock {
            val current = readSnapshotUnlocked()
            val updated = current.recipes.filterNot { it.id == token }
            writeSnapshotUnlocked(RecipeCatalogSnapshot(System.currentTimeMillis(), updated))
        }
    }

    override suspend fun getRecipe(recipeId: String): RecipeDetailDto? {
        val token = recipeId.trim()
        if (token.isBlank()) return null
        return readSnapshot().recipes.firstOrNull { it.id == token }
    }

    override suspend fun getSummaries(mealType: String?, limit: Int): List<RecipeSummaryDto> {
        val normalizedMealType = mealType?.trim().orEmpty()
        val cappedLimit = limit.coerceIn(1, 500)
        return readSnapshot().recipes.asSequence()
            .filter { recipe ->
                normalizedMealType.isBlank() ||
                    recipe.mealType?.contains(normalizedMealType, ignoreCase = true) == true ||
                    recipe.mealType?.contains("Universal", ignoreCase = true) == true
            }
            .sortedWith(compareBy<RecipeDetailDto> { it.id })
            .take(cappedLimit)
            .map { recipe ->
                RecipeSummaryDto(
                    id = recipe.id,
                    title = recipe.title,
                    mealType = recipe.mealType,
                    minutes = recipe.minutes,
                )
            }
            .toList()
    }

    private suspend fun readSnapshot(): RecipeCatalogSnapshot =
        mutex.withLock { readSnapshotUnlocked() }

    private suspend fun writeSnapshot(snapshot: RecipeCatalogSnapshot) {
        mutex.withLock { writeSnapshotUnlocked(snapshot) }
    }

    private suspend fun readSnapshotUnlocked(): RecipeCatalogSnapshot = withContext(Dispatchers.IO) {
        val file = snapshotFile
        if (!file.exists()) return@withContext RecipeCatalogSnapshot()
        runCatching {
            gson.fromJson(file.readText(Charsets.UTF_8), RecipeCatalogSnapshot::class.java)
        }.getOrNull() ?: RecipeCatalogSnapshot()
    }

    private suspend fun writeSnapshotUnlocked(snapshot: RecipeCatalogSnapshot) = withContext(Dispatchers.IO) {
        val file = snapshotFile
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(gson.toJson(snapshot), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    companion object {
        private const val SNAPSHOT_FILE_NAME = "recipe_catalog_snapshot.json"
    }
}

data class RecipeCatalogSnapshot(
    val syncedAtMs: Long = 0,
    val recipes: List<RecipeDetailDto> = emptyList(),
)
