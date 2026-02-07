package com.pcosina.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pcosina.app.data.model.DummyData
import com.pcosina.app.data.repository.UserPreferencesRepository
import com.pcosina.app.data.model.GroceryItemSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GroceryViewModel(private val repository: UserPreferencesRepository) : ViewModel() {
    private val _groceryItems = MutableStateFlow<List<DummyData.GroceryItem>>(emptyList())
    val groceryItems: StateFlow<List<DummyData.GroceryItem>> = _groceryItems.asStateFlow()

    private val _lastPlanTimestamp = MutableStateFlow<Long?>(null)
    val lastPlanTimestamp: StateFlow<Long?> = _lastPlanTimestamp.asStateFlow()

    private val _mealSources = MutableStateFlow<Map<String, List<GroceryItemSource>>>(emptyMap())
    val mealSources: StateFlow<Map<String, List<GroceryItemSource>>> = _mealSources.asStateFlow()

    private var currentUserId: String = ""
    private val gson = Gson()

    fun loadGroceryForUser(userId: String) {
        if (currentUserId == userId) return
        currentUserId = userId
        viewModelScope.launch {
            try {
                val json = repository.getGroceryJson(userId).first()
                val sourcesJson = repository.getGrocerySourcesJson(userId).first()
                val ts = repository.getSavedPlanTimestamp(userId).first()
                _lastPlanTimestamp.value = if (ts > 0) ts else null
                if (!json.isNullOrBlank()) {
                    val type = object : TypeToken<List<DummyData.GroceryItem>>() {}.type
                    _groceryItems.value = gson.fromJson(json, type)
                } else {
                    _groceryItems.value = emptyList()
                }
                if (!sourcesJson.isNullOrBlank()) {
                    val type = object : TypeToken<Map<String, List<GroceryItemSource>>>() {}.type
                    _mealSources.value = gson.fromJson(sourcesJson, type)
                } else {
                    _mealSources.value = emptyMap()
                }
            } catch (e: Exception) {
                _groceryItems.value = emptyList()
                _mealSources.value = emptyMap()
            }
        }
    }

    private fun persist() {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            repository.saveGroceryJson(currentUserId, gson.toJson(_groceryItems.value))
        }
    }

    private fun persistSources() {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            repository.saveGrocerySourcesJson(currentUserId, gson.toJson(_mealSources.value))
        }
    }

    fun addItems(items: List<DummyData.GroceryItem>) {
        _groceryItems.update { current ->
            val newList = current.toMutableList()
            items.forEach { item ->
                if (!newList.any { it.name == item.name }) {
                    newList.add(item)
                }
            }
            newList
        }
        persist()
    }

    fun setPlanSources(sources: Map<String, List<GroceryItemSource>>) {
        _mealSources.value = sources
        rebuildFromSources()
        persistSources()
        persist()
    }

    fun replaceMealItems(mealId: String, items: List<GroceryItemSource>) {
        val updated = _mealSources.value.toMutableMap()
        if (items.isEmpty()) {
            updated.remove(mealId)
        } else {
            updated[mealId] = items
        }
        _mealSources.value = updated
        rebuildFromSources()
        persistSources()
        persist()
    }

    fun hasSourcesForMeal(mealId: String): Boolean {
        return _mealSources.value.containsKey(mealId)
    }

    private fun rebuildFromSources() {
        val grouped = linkedMapOf<String, MutableList<String>>()
        _mealSources.value.values.flatten().forEach { item ->
            val key = item.name.trim().lowercase()
            if (key.isBlank()) return@forEach
            grouped.getOrPut(key) { mutableListOf() }.add(item.quantity)
        }
        _groceryItems.value = grouped.map { (key, quantities) ->
            val name = key.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            DummyData.GroceryItem(
                name = name,
                quantity = quantities.joinToString(", "),
                price = 0,
                category = "Needed"
            )
        }
    }

    fun removeItem(itemName: String) {
        _groceryItems.update { current ->
            current.filter { it.name != itemName }
        }
        persist()
    }

    /**
     * Bullet-Proof Reset: Wipes memory and stops tracking.
     */
    fun reset() {
        currentUserId = ""
        _groceryItems.value = emptyList()
        _lastPlanTimestamp.value = null
        _mealSources.value = emptyMap()
    }

    class Factory(private val repository: UserPreferencesRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GroceryViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return GroceryViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
