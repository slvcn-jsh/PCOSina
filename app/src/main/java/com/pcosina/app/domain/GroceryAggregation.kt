package com.pcosina.app.domain

import com.pcosina.app.data.model.DummyData
import java.util.Locale
import kotlin.math.roundToInt

data class GroceryListEntry(
    val key: String,
    val name: String,
    val category: String,
    val quantityDisplay: String,
    val estimatedCostPhp: Int,
    val sourceCount: Int,
)

private data class ParsedQuantity(
    val value: Double,
    val unit: String,
    val dimension: QuantityDimension,
)

private enum class QuantityDimension {
    Weight,
    Volume,
    Count,
}

private val quantityPattern = Regex(
    """(?i)(\d+\s+\d+/\d+|\d+/\d+|\d+(?:\.\d+)?)\s*(kg|kilo|kilogram|g|gram|grams|lb|lbs|pound|pounds|oz|ml|l|liter|litre|cup|cups|tbsp|tablespoon|tablespoons|tsp|teaspoon|teaspoons|piece|pieces|pc|pcs|clove|cloves|bunch|bunches|stalk|stalks|can|cans|pack|packs)"""
)

private val unitAliases = mapOf(
    "kilo" to "kg",
    "kilogram" to "kg",
    "gram" to "g",
    "grams" to "g",
    "lbs" to "lb",
    "pound" to "lb",
    "pounds" to "lb",
    "liter" to "l",
    "litre" to "l",
    "cups" to "cup",
    "tablespoon" to "tbsp",
    "tablespoons" to "tbsp",
    "teaspoon" to "tsp",
    "teaspoons" to "tsp",
    "pieces" to "piece",
    "pc" to "piece",
    "pcs" to "piece",
    "cloves" to "clove",
    "bunches" to "bunch",
    "stalks" to "stalk",
    "cans" to "can",
    "packs" to "pack",
)

fun householdSizeLabel(size: Int): String = when (size.coerceIn(1, 6)) {
    1 -> "1 person"
    2 -> "2 people"
    3 -> "3 people"
    else -> "family of ${size.coerceIn(1, 6)}"
}

fun buildGroceryListEntries(
    items: List<DummyData.GroceryItem>,
    householdSize: Int,
): List<GroceryListEntry> {
    val safeHouseholdSize = householdSize.coerceIn(1, 6)
    return items
        .filter { it.name.isNotBlank() }
        .groupBy { normalizeGroceryKey(it.name) }
        .mapNotNull { (key, groupedItems) ->
            val displayName = groupedItems.firstOrNull()?.name?.trim().orEmpty()
            if (displayName.isBlank()) return@mapNotNull null
            val category = groupedItems
                .firstOrNull()
                ?.category
                ?.takeIf { it.isNotBlank() && !it.equals("Needed", ignoreCase = true) }
                ?: PriceCatalog.inferCategory(displayName)
            val scaledSegments = groupedItems
                .flatMap { splitQuantitySegments(scaleQuantityText(it.quantity, safeHouseholdSize)) }
                .filter { it.isNotBlank() }
            val quantityDisplay = aggregateQuantitySegments(scaledSegments)
            val estimatedCost = scaledSegments
                .sumOf { segment -> PriceCatalog.estimatePriceDetail(displayName, segment).first }
                .takeIf { it > 0 }
                ?: PriceCatalog.estimatePriceDetail(displayName, quantityDisplay).first
            GroceryListEntry(
                key = key,
                name = displayName,
                category = category,
                quantityDisplay = quantityDisplay.ifBlank { "As needed" },
                estimatedCostPhp = estimatedCost.coerceAtLeast(5),
                sourceCount = scaledSegments.size.coerceAtLeast(1),
            )
        }
        .sortedWith(compareBy<GroceryListEntry> { it.category }.thenBy { it.name.lowercase(Locale.ENGLISH) })
}

fun scaleQuantityText(quantity: String, householdSize: Int): String {
    val safeHouseholdSize = householdSize.coerceIn(1, 6)
    val trimmed = quantity.trim()
    if (trimmed.isBlank() || safeHouseholdSize <= 1) return trimmed
    val segments = splitQuantitySegments(trimmed)
    if (segments.isEmpty()) return trimmed
    return segments.joinToString(", ") { segment ->
        val parsed = parseQuantitySegment(segment)
        if (parsed == null) segment else "${formatScaledValue(parsed.value * safeHouseholdSize)} ${displayUnit(parsed.unit, parsed.value * safeHouseholdSize)}".trim()
    }
}

fun scaleNutritionPerMeal(value: Int?, householdSize: Int): Int? =
    value?.times(householdSize.coerceIn(1, 6))

private fun splitQuantitySegments(quantity: String): List<String> =
    quantity.split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }

private fun normalizeGroceryKey(raw: String): String =
    raw.trim()
        .lowercase(Locale.ENGLISH)
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun parseNumber(text: String): Double? {
    val cleaned = text.trim()
    if (cleaned.isEmpty()) return null
    if (" " in cleaned) {
        val parts = cleaned.split(" ")
        if (parts.size == 2 && "/" in parts[1]) {
            return (parseNumber(parts[0]) ?: 0.0) + (parseNumber(parts[1]) ?: 0.0)
        }
    }
    if ("/" in cleaned) {
        val parts = cleaned.split("/")
        if (parts.size == 2) {
            val numerator = parts[0].toDoubleOrNull() ?: return null
            val denominator = parts[1].toDoubleOrNull() ?: return null
            if (denominator == 0.0) return null
            return numerator / denominator
        }
    }
    return cleaned.toDoubleOrNull()
}

private fun parseQuantitySegment(segment: String): ParsedQuantity? {
    val match = quantityPattern.find(segment) ?: return null
    val value = parseNumber(match.groupValues.getOrNull(1).orEmpty()) ?: return null
    val normalizedUnit = match.groupValues.getOrNull(2)
        ?.trim()
        ?.lowercase(Locale.ENGLISH)
        ?.let { unitAliases[it] ?: it }
        ?: return null
    val dimension = when (normalizedUnit) {
        "kg", "g", "lb", "oz" -> QuantityDimension.Weight
        "l", "ml", "cup", "tbsp", "tsp" -> QuantityDimension.Volume
        "piece", "clove", "bunch", "stalk", "can", "pack" -> QuantityDimension.Count
        else -> return null
    }
    return ParsedQuantity(value, normalizedUnit, dimension)
}

private fun aggregateQuantitySegments(segments: List<String>): String {
    if (segments.isEmpty()) return ""
    val parsed = segments.mapNotNull(::parseQuantitySegment)
    if (parsed.size != segments.size) {
        return if (segments.distinct().size == 1) segments.first() else "Mixed amounts from ${segments.size} meals"
    }
    val dimensions = parsed.map { it.dimension }.distinct()
    if (dimensions.size != 1) {
        return "Mixed amounts from ${segments.size} meals"
    }
    return when (dimensions.single()) {
        QuantityDimension.Weight -> formatWeight(parsed)
        QuantityDimension.Volume -> formatVolume(parsed)
        QuantityDimension.Count -> formatCount(parsed)
    }
}

private fun formatWeight(values: List<ParsedQuantity>): String {
    val totalKg = values.sumOf { value ->
        when (value.unit) {
            "kg" -> value.value
            "g" -> value.value / 1000.0
            "lb" -> value.value / 2.2046
            "oz" -> value.value / 35.274
            else -> 0.0
        }
    }
    return if (totalKg < 1.0) {
        "${formatScaledValue(totalKg * 1000.0)} g"
    } else {
        "${formatScaledValue(totalKg)} kg"
    }
}

private fun formatVolume(values: List<ParsedQuantity>): String {
    val totalLiters = values.sumOf { value ->
        when (value.unit) {
            "l" -> value.value
            "ml" -> value.value / 1000.0
            "cup" -> value.value * 0.24
            "tbsp" -> value.value * 0.015
            "tsp" -> value.value * 0.005
            else -> 0.0
        }
    }
    return if (totalLiters < 1.0) {
        "${formatScaledValue(totalLiters * 1000.0)} ml"
    } else {
        "${formatScaledValue(totalLiters)} L"
    }
}

private fun formatCount(values: List<ParsedQuantity>): String {
    val units = values.map { it.unit }.distinct()
    if (units.size != 1) {
        return "Mixed amounts from ${values.size} meals"
    }
    val unit = units.single()
    val total = values.sumOf { it.value }
    return "${formatScaledValue(total)} ${displayUnit(unit, total)}"
}

private fun formatScaledValue(value: Double): String {
    val rounded = if (value >= 10) {
        (value * 10.0).roundToInt() / 10.0
    } else {
        (value * 100.0).roundToInt() / 100.0
    }
    return if (rounded % 1.0 == 0.0) rounded.roundToInt().toString() else rounded.toString()
}

private fun displayUnit(unit: String, value: Double): String {
    val singular = when (unit) {
        "piece" -> "pc"
        "clove" -> "clove"
        "bunch" -> "bunch"
        "stalk" -> "stalk"
        "can" -> "can"
        "pack" -> "pack"
        else -> unit
    }
    return if (value == 1.0) singular else when (singular) {
        "pc" -> "pcs"
        "clove" -> "cloves"
        "bunch" -> "bunches"
        "stalk" -> "stalks"
        "can" -> "cans"
        "pack" -> "packs"
        else -> singular
    }
}
