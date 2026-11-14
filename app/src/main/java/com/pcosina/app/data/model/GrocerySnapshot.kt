package com.pcosina.app.data.model

data class GrocerySnapshot(
    val planId: String,
    val items: List<DummyData.GroceryItem>,
    val sources: Map<String, List<GroceryItemSource>>,
    val checkedItemNames: Set<String>? = null,
    val pantryOptOutNames: Set<String>? = null,
)
