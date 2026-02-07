package com.pcosina.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pcosina.app.data.model.DummyData
import com.pcosina.app.data.repository.UserPreferencesRepository
import com.pcosina.app.data.model.GroceryItemSource
import com.pcosina.app.data.model.GrocerySnapshot
import com.pcosina.app.domain.PriceCatalog
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

    private val _activePlanId = MutableStateFlow<String?>(null)
    val activePlanId: StateFlow<String?> = _activePlanId.asStateFlow()

    private var currentUserId: String = ""
    private val gson = Gson()
    private var snapshots: MutableList<GrocerySnapshot> = mutableListOf()

    fun loadGroceryForUser(userId: String) {
        if (currentUserId == userId) return
        currentUserId = userId
        viewModelScope.launch {
            try {
                val snapshotsJson = repository.getGrocerySnapshotsJson(userId).first()
                val json = repository.getGroceryJson(userId).first()
                val sourcesJson = repository.getGrocerySourcesJson(userId).first()
                val ts = repository.getSavedPlanTimestamp(userId).first()
                val savedActive = repository.getActivePlanId(userId).first()
                _lastPlanTimestamp.value = if (ts > 0) ts else null
                snapshots = if (!snapshotsJson.isNullOrBlank()) {
                    val type = object : TypeToken<List<GrocerySnapshot>>() {}.type
                    gson.fromJson(snapshotsJson, type)
                } else {
                    mutableListOf()
                }
                if (snapshots.isEmpty() && (!json.isNullOrBlank() || !sourcesJson.isNullOrBlank())) {
                    val items = if (!json.isNullOrBlank()) {
                        val type = object : TypeToken<List<DummyData.GroceryItem>>() {}.type
                        gson.fromJson<List<DummyData.GroceryItem>>(json, type)
                    } else emptyList()
                    val sources = if (!sourcesJson.isNullOrBlank()) {
                        val type = object : TypeToken<Map<String, List<GroceryItemSource>>>() {}.type
                        gson.fromJson<Map<String, List<GroceryItemSource>>>(sourcesJson, type)
                    } else emptyMap()
                    snapshots.add(GrocerySnapshot(planId = "legacy", items = items, sources = sources))
                }
                val activeId = savedActive ?: snapshots.lastOrNull()?.planId
                _activePlanId.value = activeId
                val activeSnapshot = snapshots.firstOrNull { it.planId == activeId }
                _groceryItems.value = activeSnapshot?.items ?: emptyList()
                _mealSources.value = activeSnapshot?.sources ?: emptyMap()
            } catch (e: Exception) {
                _groceryItems.value = emptyList()
                _mealSources.value = emptyMap()
                _activePlanId.value = null
                snapshots = mutableListOf()
            }
        }
    }

    private fun persist() {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            val active = _activePlanId.value
            if (active != null) {
                upsertSnapshot(active)
                repository.saveGrocerySnapshotsJson(currentUserId, gson.toJson(snapshots))
            } else {
                repository.saveGroceryJson(currentUserId, gson.toJson(_groceryItems.value))
            }
        }
    }

    private fun persistSources() {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            val active = _activePlanId.value
            if (active != null) {
                upsertSnapshot(active)
                repository.saveGrocerySnapshotsJson(currentUserId, gson.toJson(snapshots))
            } else {
                repository.saveGrocerySourcesJson(currentUserId, gson.toJson(_mealSources.value))
            }
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

    fun setActivePlan(planId: String?) {
        _activePlanId.value = planId
        if (planId == null) {
            _groceryItems.value = emptyList()
            _mealSources.value = emptyMap()
            return
        }
        val snapshot = snapshots.firstOrNull { it.planId == planId }
        _groceryItems.value = snapshot?.items ?: emptyList()
        _mealSources.value = snapshot?.sources ?: emptyMap()
        viewModelScope.launch {
            repository.saveActivePlanId(currentUserId, planId)
        }
    }

    private fun upsertSnapshot(planId: String) {
        val snapshot = GrocerySnapshot(planId, _groceryItems.value, _mealSources.value)
        val idx = snapshots.indexOfFirst { it.planId == planId }
        if (idx >= 0) snapshots[idx] = snapshot else snapshots.add(snapshot)
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
            val category = PriceCatalog.inferCategory(name)
            val price = PriceCatalog.estimatePrice(name)
            DummyData.GroceryItem(
                name = name,
                quantity = quantities.joinToString(", "),
                price = price,
                category = category
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
        _activePlanId.value = null
        snapshots = mutableListOf()
    }

    fun clearForUser() {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            repository.clearGrocerySnapshots(currentUserId)
            _groceryItems.value = emptyList()
            _mealSources.value = emptyMap()
            _activePlanId.value = null
            snapshots = mutableListOf()
        }
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
