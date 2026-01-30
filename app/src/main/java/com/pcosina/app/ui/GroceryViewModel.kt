package com.pcosina.app.ui

import androidx.lifecycle.ViewModel
import com.pcosina.app.data.model.DummyData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class GroceryViewModel : ViewModel() {
    private val _groceryItems = MutableStateFlow<List<DummyData.GroceryItem>>(emptyList())
    val groceryItems: StateFlow<List<DummyData.GroceryItem>> = _groceryItems.asStateFlow()

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
    }

    fun removeItem(itemName: String) {
        _groceryItems.update { current ->
            current.filter { it.name != itemName }
        }
    }
}
