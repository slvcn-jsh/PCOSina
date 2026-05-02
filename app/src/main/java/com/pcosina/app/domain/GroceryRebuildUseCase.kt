package com.pcosina.app.domain

import com.pcosina.app.data.model.DummyData
import com.pcosina.app.data.model.GroceryItemSource

class GroceryRebuildUseCase {

    operator fun invoke(mealSources: Map<String, List<GroceryItemSource>>): List<DummyData.GroceryItem> {
        val grouped = linkedMapOf<String, MutableList<String>>()
        mealSources.values.flatten().forEach { item ->
            val normalizedKey = normalizeItemKey(item.name)
            if (normalizedKey.isBlank()) return@forEach
            grouped.getOrPut(normalizedKey) { mutableListOf() }.add(item.quantity.trim())
        }
        return grouped.map { (normalizedKey, quantities) ->
            val name = normalizedKey.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase() else char.toString()
            }
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

    private fun normalizeItemKey(name: String): String = name.trim().lowercase()
}
