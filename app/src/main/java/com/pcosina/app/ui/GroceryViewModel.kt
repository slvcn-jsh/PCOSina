package com.pcosina.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pcosina.app.data.model.DummyData
import com.pcosina.app.data.model.GroceryItemSource
import com.pcosina.app.data.model.GrocerySnapshot
import com.pcosina.app.data.model.PersonalPriceOverride
import com.pcosina.app.data.repository.GroceryLocalRepository
import com.pcosina.app.data.repository.UserPreferencesGroceryLocalRepository
import com.pcosina.app.data.repository.UserPreferencesRepository
import com.pcosina.app.domain.canonicalGroceryKey
import com.pcosina.app.domain.GroceryRebuildUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GroceryViewModel(private val groceryLocalRepository: GroceryLocalRepository) : ViewModel() {
    constructor(userPreferencesRepository: UserPreferencesRepository) : this(
        UserPreferencesGroceryLocalRepository(userPreferencesRepository)
    )

    private val _groceryItems = MutableStateFlow<List<DummyData.GroceryItem>>(emptyList())
    val groceryItems: StateFlow<List<DummyData.GroceryItem>> = _groceryItems.asStateFlow()

    private val _lastPlanTimestamp = MutableStateFlow<Long?>(null)
    val lastPlanTimestamp: StateFlow<Long?> = _lastPlanTimestamp.asStateFlow()

    private val _mealSources = MutableStateFlow<Map<String, List<GroceryItemSource>>>(emptyMap())
    val mealSources: StateFlow<Map<String, List<GroceryItemSource>>> = _mealSources.asStateFlow()

    private val _activePlanId = MutableStateFlow<String?>(null)
    val activePlanId: StateFlow<String?> = _activePlanId.asStateFlow()

    private val _checkedItemNames = MutableStateFlow<Set<String>>(emptySet())
    val checkedItemNames: StateFlow<Set<String>> = _checkedItemNames.asStateFlow()

    private val _pantryOptOutNames = MutableStateFlow<Set<String>>(emptySet())
    val pantryOptOutNames: StateFlow<Set<String>> = _pantryOptOutNames.asStateFlow()

    private val _personalPriceOverrides = MutableStateFlow<List<PersonalPriceOverride>>(emptyList())
    val personalPriceOverrides: StateFlow<List<PersonalPriceOverride>> = _personalPriceOverrides.asStateFlow()

    private val _loadedUserId = MutableStateFlow<String?>(null)
    val loadedUserId: StateFlow<String?> = _loadedUserId.asStateFlow()

    private var currentUserId: String = ""
    private val gson = Gson()
    private val groceryRebuildUseCase = GroceryRebuildUseCase()
    private val persistenceMutex = Mutex()
    private var snapshots: MutableList<GrocerySnapshot> = mutableListOf()

    private fun normalizedItemKey(name: String): String = canonicalGroceryKey(name)

    fun loadGroceryForUser(userId: String) {
        if (currentUserId == userId) return
        currentUserId = userId
        _loadedUserId.value = null
        viewModelScope.launch {
            try {
                val snapshotsJson = groceryLocalRepository.getGrocerySnapshotsJson(userId).first()
                val json = groceryLocalRepository.getGroceryJson(userId).first()
                val sourcesJson = groceryLocalRepository.getGrocerySourcesJson(userId).first()
                val ts = groceryLocalRepository.getSavedPlanTimestamp(userId).first()
                val savedActive = groceryLocalRepository.getActivePlanId(userId).first()
                val personalPricesJson = groceryLocalRepository.getPersonalPriceOverridesJson(userId).first()
                if (currentUserId != userId) return@launch
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
                _checkedItemNames.value = activeSnapshot?.checkedItemNames.orEmpty()
                _pantryOptOutNames.value = activeSnapshot?.pantryOptOutNames.orEmpty()
                _personalPriceOverrides.value = parsePersonalPriceOverrides(personalPricesJson)
                pruneChecklistToCurrentItems()
                if (savedActive != resolvedActiveId) {
                    groceryLocalRepository.saveActivePlanId(userId, resolvedActiveId)
                }
            } catch (e: Exception) {
                if (currentUserId != userId) return@launch
                _groceryItems.value = emptyList()
                _mealSources.value = emptyMap()
                _activePlanId.value = null
                _checkedItemNames.value = emptySet()
                _pantryOptOutNames.value = emptySet()
                _personalPriceOverrides.value = emptyList()
                snapshots = mutableListOf()
            } finally {
                if (currentUserId == userId) {
                    _loadedUserId.value = userId
                }
            }
        }
    }

    private fun persist() {
        val userId = currentUserId.takeIf { it.isNotBlank() } ?: return
        val active = _activePlanId.value
        if (active != null) {
            upsertSnapshot(active)
            val payload = gson.toJson(snapshots)
            viewModelScope.launch {
                persistenceMutex.withLock {
                    groceryLocalRepository.saveGrocerySnapshotsJson(userId, payload)
                }
            }
        } else {
            val payload = gson.toJson(_groceryItems.value)
            viewModelScope.launch {
                persistenceMutex.withLock {
                    groceryLocalRepository.saveGroceryJson(userId, payload)
                }
            }
        }
    }

    private fun persistSources() {
        val userId = currentUserId.takeIf { it.isNotBlank() } ?: return
        val active = _activePlanId.value
        if (active != null) {
            upsertSnapshot(active)
            val payload = gson.toJson(snapshots)
            viewModelScope.launch {
                persistenceMutex.withLock {
                    groceryLocalRepository.saveGrocerySnapshotsJson(userId, payload)
                }
            }
        } else {
            val payload = gson.toJson(_mealSources.value)
            viewModelScope.launch {
                persistenceMutex.withLock {
                    groceryLocalRepository.saveGrocerySourcesJson(userId, payload)
                }
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
        val previousPlanId = _activePlanId.value
        val previousSnapshotPayload = if (previousPlanId != null && previousPlanId != planId) {
            upsertSnapshot(previousPlanId)
            gson.toJson(snapshots)
        } else {
            null
        }
        _activePlanId.value = planId
        val userId = currentUserId
        if (userId.isNotBlank()) {
            viewModelScope.launch {
                persistenceMutex.withLock {
                    if (previousSnapshotPayload != null) {
                        groceryLocalRepository.saveGrocerySnapshotsJson(userId, previousSnapshotPayload)
                    }
                    groceryLocalRepository.saveActivePlanId(userId, planId)
                }
            }
        }
        if (planId == null) {
            _groceryItems.value = emptyList()
            _mealSources.value = emptyMap()
            _checkedItemNames.value = emptySet()
            _pantryOptOutNames.value = emptySet()
            return
        }
        val snapshot = snapshots.firstOrNull { it.planId == planId }
        _groceryItems.value = snapshot?.items ?: emptyList()
        _mealSources.value = snapshot?.sources ?: emptyMap()
        _checkedItemNames.value = snapshot?.checkedItemNames.orEmpty()
        _pantryOptOutNames.value = snapshot?.pantryOptOutNames.orEmpty()
        pruneChecklistToCurrentItems()
    }

    private fun upsertSnapshot(planId: String) {
        val snapshot = GrocerySnapshot(
            planId = planId,
            items = _groceryItems.value,
            sources = _mealSources.value,
            checkedItemNames = _checkedItemNames.value,
            pantryOptOutNames = _pantryOptOutNames.value,
        )
        val idx = snapshots.indexOfFirst { it.planId == planId }
        if (idx >= 0) snapshots[idx] = snapshot else snapshots.add(snapshot)
    }

    fun hasSourcesForMeal(mealId: String): Boolean {
        return _mealSources.value.containsKey(mealId)
    }

    private fun rebuildFromSources() {
        _groceryItems.value = groceryRebuildUseCase(_mealSources.value)
        pruneChecklistToCurrentItems()
    }

    fun removeItem(itemName: String) {
        val normalizedKey = normalizedItemKey(itemName)
        _groceryItems.update { current ->
            current.filter { normalizedItemKey(it.name) != normalizedKey }
        }
        pruneChecklistToCurrentItems()
        persist()
    }

    private fun pruneChecklistToCurrentItems() {
        val validKeys = _groceryItems.value
            .map { item -> normalizedItemKey(item.name) }
            .filter { it.isNotBlank() }
            .toSet()
        _checkedItemNames.value = _checkedItemNames.value
            .filter { normalizedItemKey(it) in validKeys }
            .toSet()
        _pantryOptOutNames.value = _pantryOptOutNames.value
            .filter { normalizedItemKey(it) in validKeys }
            .toSet()
    }

    fun updateChecklistState(
        checkedItemNames: Set<String>,
        pantryOptOutNames: Set<String>,
    ) {
        _checkedItemNames.value = checkedItemNames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        _pantryOptOutNames.value = pantryOptOutNames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        persist()
    }

    suspend fun upsertPersonalPriceOverride(value: PersonalPriceOverride): Boolean {
        val userId = currentUserId.takeIf { it.isNotBlank() } ?: return false
        val normalized = value.copy(
            canonicalKey = canonicalGroceryKey(value.canonicalKey.ifBlank { value.ingredientName }),
            ingredientName = value.ingredientName.trim(),
            unit = value.unit.trim().lowercase(),
            marketType = value.marketType.trim().lowercase(),
            location = value.location.trim().ifBlank { "NCR" },
        )
        if (
            normalized.canonicalKey.isBlank() ||
            normalized.ingredientName.isBlank() ||
            normalized.pricePhp <= 0.0 ||
            normalized.unit !in setOf("kg", "l", "piece")
        ) return false
        val previous = _personalPriceOverrides.value
        val updated = (previous.filterNot { it.canonicalKey == normalized.canonicalKey } + normalized)
            .sortedBy { it.ingredientName.lowercase() }
        _personalPriceOverrides.value = updated
        return runCatching {
            persistenceMutex.withLock {
                groceryLocalRepository.savePersonalPriceOverridesJson(userId, gson.toJson(updated))
            }
        }.onFailure {
            _personalPriceOverrides.value = previous
        }.isSuccess
    }

    suspend fun replaceSyncedPersonalPrices(values: List<PersonalPriceOverride>): Boolean {
        val pending = _personalPriceOverrides.value.filter {
            it.syncStatus == "Pending" || it.syncStatus == "Failed" || it.syncStatus == "DeletePending"
        }
        val pendingKeys = pending.map { it.canonicalKey }.toSet()
        val merged = values.filterNot { it.canonicalKey in pendingKeys } + pending
        return persistPersonalPriceOverrides(merged)
    }

    suspend fun removePersonalPriceOverride(canonicalKey: String): Boolean =
        persistPersonalPriceOverrides(
            _personalPriceOverrides.value.filterNot { it.canonicalKey == canonicalGroceryKey(canonicalKey) }
        )

    private suspend fun persistPersonalPriceOverrides(values: List<PersonalPriceOverride>): Boolean {
        val userId = currentUserId.takeIf { it.isNotBlank() } ?: return false
        val previous = _personalPriceOverrides.value
        val normalized = values.distinctBy { it.canonicalKey }
        _personalPriceOverrides.value = normalized
        return runCatching {
            persistenceMutex.withLock {
                groceryLocalRepository.savePersonalPriceOverridesJson(userId, gson.toJson(normalized))
            }
        }.onFailure {
            _personalPriceOverrides.value = previous
        }.isSuccess
    }

    private fun parsePersonalPriceOverrides(raw: String?): List<PersonalPriceOverride> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<PersonalPriceOverride>>() {}.type
            gson.fromJson<List<PersonalPriceOverride>>(raw, type)
        }.getOrDefault(emptyList())
            .filter { it.canonicalKey.isNotBlank() && it.ingredientName.isNotBlank() && it.pricePhp > 0.0 }
            .distinctBy { it.canonicalKey }
    }

    /**
     * Bullet-Proof Reset: Wipes memory and stops tracking.
     */
    fun reset() {
        currentUserId = ""
        _loadedUserId.value = null
        _groceryItems.value = emptyList()
        _lastPlanTimestamp.value = null
        _mealSources.value = emptyMap()
        _activePlanId.value = null
        _checkedItemNames.value = emptySet()
        _pantryOptOutNames.value = emptySet()
        _personalPriceOverrides.value = emptyList()
        snapshots = mutableListOf()
    }

    fun clearForUser() {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            groceryLocalRepository.clearGrocerySnapshots(currentUserId)
            groceryLocalRepository.clearPersonalPriceOverrides(currentUserId)
            _groceryItems.value = emptyList()
            _mealSources.value = emptyMap()
            _activePlanId.value = null
            _checkedItemNames.value = emptySet()
            _pantryOptOutNames.value = emptySet()
            _personalPriceOverrides.value = emptyList()
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
