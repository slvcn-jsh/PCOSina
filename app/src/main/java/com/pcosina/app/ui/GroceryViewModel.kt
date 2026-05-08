package com.pcosina.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pcosina.app.data.model.DummyData
import com.pcosina.app.data.model.GroceryItemSource
import com.pcosina.app.data.model.GrocerySnapshot
import com.pcosina.app.data.repository.GroceryLocalRepository
import com.pcosina.app.domain.canonicalGroceryKey
import com.pcosina.app.domain.GroceryRebuildUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GroceryViewModel(private val groceryLocalRepository: GroceryLocalRepository) : ViewModel() {
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
    private val groceryRebuildUseCase = GroceryRebuildUseCase()
    private var snapshots: MutableList<GrocerySnapshot> = mutableListOf()

    private fun normalizedItemKey(name: String): String = canonicalGroceryKey(name)

    fun loadGroceryForUser(userId: String) {
        if (currentUserId == userId) return
        currentUserId = userId
        viewModelScope.launch {
            try {
                val snapshotsJson = groceryLocalRepository.getGrocerySnapshotsJson(userId).first()
                val json = groceryLocalRepository.getGroceryJson(userId).first()
                val sourcesJson = groceryLocalRepository.getGrocerySourcesJson(userId).first()
                val ts = groceryLocalRepository.getSavedPlanTimestamp(userId).first()
                val savedActive = groceryLocalRepository.getActivePlanId(userId).first()
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
                val activeSnapshot = savedActive?.let { id ->
                    snapshots.firstOrNull { it.planId == id }
                } ?: snapshots.lastOrNull()
                val resolvedActiveId = activeSnapshot?.planId
                _activePlanId.value = resolvedActiveId
                _groceryItems.value = activeSnapshot?.items ?: emptyList()
                _mealSources.value = activeSnapshot?.sources ?: emptyMap()
                if (savedActive != resolvedActiveId) {
                    groceryLocalRepository.saveActivePlanId(userId, resolvedActiveId)
                }
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
                groceryLocalRepository.saveGrocerySnapshotsJson(currentUserId, gson.toJson(snapshots))
            } else {
                groceryLocalRepository.saveGroceryJson(currentUserId, gson.toJson(_groceryItems.value))
            }
        }
    }

    private fun persistSources() {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            val active = _activePlanId.value
            if (active != null) {
                upsertSnapshot(active)
                groceryLocalRepository.saveGrocerySnapshotsJson(currentUserId, gson.toJson(snapshots))
            } else {
                groceryLocalRepository.saveGrocerySourcesJson(currentUserId, gson.toJson(_mealSources.value))
            }
        }
    }

    fun addItems(items: List<DummyData.GroceryItem>) {
        _groceryItems.update { current ->
            val newList = current.toMutableList()
            items.forEach { item ->
                val normalizedKey = normalizedItemKey(item.name)
                if (normalizedKey.isBlank()) return@forEach
                if (!newList.any { normalizedItemKey(it.name) == normalizedKey }) {
                    newList.add(item.copy(name = item.name.trim(), quantity = item.quantity.trim()))
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
        if (currentUserId.isNotBlank()) {
            viewModelScope.launch {
                groceryLocalRepository.saveActivePlanId(currentUserId, planId)
            }
        }
        if (planId == null) {
            _groceryItems.value = emptyList()
            _mealSources.value = emptyMap()
            return
        }
        val snapshot = snapshots.firstOrNull { it.planId == planId }
        _groceryItems.value = snapshot?.items ?: emptyList()
        _mealSources.value = snapshot?.sources ?: emptyMap()
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
        _groceryItems.value = groceryRebuildUseCase(_mealSources.value)
    }

    fun removeItem(itemName: String) {
        val normalizedKey = normalizedItemKey(itemName)
        _groceryItems.update { current ->
            current.filter { normalizedItemKey(it.name) != normalizedKey }
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
            groceryLocalRepository.clearGrocerySnapshots(currentUserId)
            _groceryItems.value = emptyList()
            _mealSources.value = emptyMap()
            _activePlanId.value = null
            snapshots = mutableListOf()
        }
    }

    class Factory(private val groceryLocalRepository: GroceryLocalRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GroceryViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return GroceryViewModel(groceryLocalRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
