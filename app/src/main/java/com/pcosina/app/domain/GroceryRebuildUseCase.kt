package com.pcosina.app.domain

import com.pcosina.app.data.model.DummyData
import com.pcosina.app.data.model.GroceryItemSource

class GroceryRebuildUseCase {

    operator fun invoke(mealSources: Map<String, List<GroceryItemSource>>): List<DummyData.GroceryItem> {
        val grouped = linkedMapOf<String, MutableList<String>>()
        mealSources.values.flatten().forEach { item ->
            val normalizedKey = canonicalGroceryKey(item.name)
            if (normalizedKey.isBlank()) return@forEach
            val quantity = item.quantity.trim().ifBlank { item.name.trim() }
            grouped.getOrPut(normalizedKey) { mutableListOf() }.add(quantity)
        }
        return grouped.map { (normalizedKey, quantities) ->
            val name = canonicalGroceryName(normalizedKey)
            val aggregatedQuantity = quantities.joinToString(", ")
            val category = PriceCatalog.inferCategory(name)
            val price = PriceCatalog.estimatePriceDetail(name, aggregatedQuantity).first
            DummyData.GroceryItem(
                name = name,
                quantity = aggregatedQuantity,
                price = price,
                category = category,
            )
        }
    }
}
