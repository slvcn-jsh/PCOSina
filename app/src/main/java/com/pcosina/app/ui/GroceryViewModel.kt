package com.pcosina.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pcosina.app.data.model.DummyData
import com.pcosina.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GroceryViewModel(private val repository: UserPreferencesRepository) : ViewModel() {
    private val _groceryItems = MutableStateFlow<List<DummyData.GroceryItem>>(emptyList())
    val groceryItems: StateFlow<List<DummyData.GroceryItem>> = _groceryItems.asStateFlow()

    private var currentUserId: String = ""
    private val gson = Gson()

    fun loadGroceryForUser(userId: String) {
        if (currentUserId == userId) return
        currentUserId = userId
        viewModelScope.launch {
            try {
                val json = repository.getGroceryJson(userId).first()
                if (!json.isNullOrBlank()) {
                    val type = object : TypeToken<List<DummyData.GroceryItem>>() {}.type
                    _groceryItems.value = gson.fromJson(json, type)
                } else {
                    _groceryItems.value = emptyList()
                }
            } catch (e: Exception) {
                _groceryItems.value = emptyList()
            }
        }
    }

    private fun persist() {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            repository.saveGroceryJson(currentUserId, gson.toJson(_groceryItems.value))
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
