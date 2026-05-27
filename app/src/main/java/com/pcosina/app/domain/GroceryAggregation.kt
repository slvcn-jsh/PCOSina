package com.pcosina.app.domain

import com.pcosina.app.data.model.DummyData
import com.pcosina.app.data.model.PantryEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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

enum class PantryCoverageStatus {
    Full,
    Partial,
    NameOnly,
}

data class PantryCoverage(
    val itemName: String,
    val status: PantryCoverageStatus,
    val pantryQuantityDisplay: String?,
    val remainingQuantityDisplay: String?,
    val detail: String,
) {
    val autoCovered: Boolean = status == PantryCoverageStatus.Full
}

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

private data class GroceryCanonical(
    val key: String,
    val displayName: String,
)

private data class GroceryBaseQuantity(
    val value: Double,
    val unit: GroceryBaseUnit,
)

private enum class GroceryBaseUnit {
    Gram,
    Milliliter,
    Count,
}

private val quantityPattern = Regex(
    """(?i)(\d+\s+\d+/\d+|\d+/\d+|\d+(?:\.\d+)?)\s*(kg|kilo|kilogram|g|gram|grams|lb|lbs|pound|pounds|oz|ml|l|liter|litre|cup|cups|tbsp|tablespoon|tablespoons|tsp|teaspoon|teaspoons|piece|pieces|piraso|pc|pcs|clove|cloves|bunch|bunches|tali|stalk|stalks|can|cans|pack|packs|head|heads)"""
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
    "piraso" to "piece",
    "pc" to "piece",
    "pcs" to "piece",
    "cloves" to "clove",
    "bunches" to "bunch",
    "tali" to "bunch",
    "stalks" to "stalk",
    "cans" to "can",
    "packs" to "pack",
    "heads" to "head",
)

private val ingredientSynonyms = mapOf(
    "bawang" to "garlic",
    "itlog" to "egg",
    "eggs" to "egg",
    "sibuyas" to "onion",
    "kamatis" to "tomato",
    "luya" to "ginger",
    "manok" to "chicken",
    "baboy" to "pork",
    "liempo" to "pork belly",
    "baka" to "beef",
    "hipon" to "shrimp",
    "bangus" to "fish",
    "tilapia" to "fish",
    "galunggong" to "fish",
    "bigas" to "rice",
    "gatas" to "milk",
    "keso" to "cheese",
    "repolyo" to "cabbage",
    "talong" to "eggplant",
    "kalabasa" to "squash",
    "suka" to "vinegar",
    "toyo" to "soy sauce",
    "patis" to "fish sauce",
    "asin" to "salt",
    "paminta" to "pepper",
)

private val descriptorWords = setOf(
    "fresh", "minced", "chopped", "sliced", "diced", "crushed", "ground", "whole", "small", "medium",
    "large", "raw", "cooked", "lean", "skinless", "boneless", "optional", "about", "approx", "approximately",
    "peeled", "grated", "shredded", "thinly", "finely", "ripe", "dried", "drained", "canned", "native"
)

private val knownIngredientPhrases = listOf(
    "soy sauce",
    "fish sauce",
    "coconut milk",
    "olive oil",
    "coconut oil",
    "pork belly",
    "chicken breast",
    "brown rice",
    "white rice",
    "bell pepper",
)

private val knownIngredientTokens = setOf(
    "garlic", "bawang", "egg", "eggs", "itlog", "onion", "sibuyas", "tomato", "kamatis", "ginger", "luya",
    "chicken", "manok", "beef", "pork", "baboy", "liempo", "fish", "tilapia", "bangus", "galunggong",
    "shrimp", "hipon", "rice", "bigas", "milk", "gatas", "cheese", "keso", "cabbage", "repolyo",
    "pechay", "spinach", "kale", "malunggay", "kangkong", "okra", "ampalaya", "eggplant", "talong",
    "sayote", "squash", "kalabasa", "chili", "sili", "oil", "vinegar", "suka", "toyo", "patis",
    "salt", "asin", "pepper", "paminta", "banana", "apple", "orange", "oat", "bread", "flour",
    "pasta", "noodles", "tuna", "sardines", "water", "coffee", "tea"
)

private val countUnitWords = setOf("piece", "clove", "bunch", "stalk", "can", "pack", "head")

private val ingredientPieceWeightGrams = mapOf(
    "garlic:clove" to 5.0,
    "garlic:head" to 45.0,
    "garlic:piece" to 5.0,
    "egg:piece" to 55.0,
    "onion:piece" to 110.0,
    "tomato:piece" to 90.0,
    "ginger:piece" to 20.0,
    "banana:piece" to 100.0,
    "chicken:piece" to 180.0,
    "pork:piece" to 150.0,
    "fish:piece" to 180.0,
    "pechay:bunch" to 180.0,
    "kangkong:bunch" to 180.0,
    "malunggay:bunch" to 80.0,
)

private val displayNameOverrides = mapOf(
    "egg" to "Eggs",
)

fun buildGroceryListEntries(
    items: List<DummyData.GroceryItem>,
): List<GroceryListEntry> {
    return items
        .filter { it.name.isNotBlank() }
        .groupBy { canonicalGroceryIngredient(it.name).key }
        .mapNotNull { (key, groupedItems) ->
            val canonical = groupedItems
                .map { canonicalGroceryIngredient(it.name) }
                .firstOrNull { it.key == key }
            val displayName = canonical?.displayName.orEmpty()
            if (displayName.isBlank()) return@mapNotNull null
            val category = groupedItems
                .firstOrNull()
                ?.category
                ?.takeIf { it.isNotBlank() && !it.equals("Needed", ignoreCase = true) }
                ?: PriceCatalog.inferCategory(displayName)
            val sourceSegments = groupedItems.flatMap { item ->
                quantitySegmentsForItem(item.name, item.quantity)
            }
            val primaryUserSegments = sourceSegments
                .flatMap { splitQuantitySegments(it) }
                .filter { it.isNotBlank() }
            val quantityDisplay = aggregateQuantitySegments(displayName, category, primaryUserSegments)
            val estimatedCost = primaryUserSegments
                .sumOf { segment -> PriceCatalog.estimatePriceDetail(displayName, segment).first }
                .takeIf { it > 0 }
                ?: PriceCatalog.estimatePriceDetail(displayName, quantityDisplay).first
            GroceryListEntry(
                key = key,
                name = displayName,
                category = category,
                quantityDisplay = quantityDisplay.ifBlank { "As needed" },
                estimatedCostPhp = estimatedCost.coerceAtLeast(5),
                sourceCount = primaryUserSegments.size.coerceAtLeast(1),
            )
        }
        .sortedWith(compareBy<GroceryListEntry> { it.category }.thenBy { it.name.lowercase(Locale.ENGLISH) })
}

private fun splitQuantitySegments(quantity: String): List<String> =
    quantity.split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }

fun canonicalGroceryName(raw: String): String = canonicalGroceryIngredient(raw).displayName

fun canonicalGroceryKey(raw: String): String = canonicalGroceryIngredient(raw).key

fun groceryNamesMatch(left: String, right: String): Boolean {
    val leftKey = canonicalGroceryKey(left)
    val rightKey = canonicalGroceryKey(right)
    if (leftKey.isBlank() || rightKey.isBlank()) return false
    if (leftKey == rightKey) return true
    val leftTokens = leftKey.split(" ").filter { it.isNotBlank() }.toSet()
    val rightTokens = rightKey.split(" ").filter { it.isNotBlank() }.toSet()
    if (leftTokens.size <= 1 || rightTokens.size <= 1) return false
    return leftTokens.containsAll(rightTokens) || rightTokens.containsAll(leftTokens)
}

fun buildPantryCoverage(
    groceryEntries: List<GroceryListEntry>,
    pantryEntries: List<PantryEntry>,
    today: LocalDate = LocalDate.now(),
): Map<String, PantryCoverage> {
    val activePantry = pantryEntries.filterNot { entry ->
        val expiry = entry.expiryDate
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull() }
        expiry != null && expiry.isBefore(today)
    }
    return groceryEntries.mapNotNull { grocery ->
        val matchedPantry = activePantry.filter { pantry ->
            groceryNamesMatch(pantry.name, grocery.name)
        }
        if (matchedPantry.isEmpty()) return@mapNotNull null
        val needed = parseQuantitySegment(grocery.quantityDisplay)
            ?.toBaseQuantity(grocery.name, grocery.category)
        val pantryQuantities = matchedPantry.mapNotNull { pantry ->
            pantryQuantitySegments(pantry).mapNotNull { segment ->
                parseQuantitySegment(segment)?.toBaseQuantity(grocery.name, grocery.category)
            }
                .filter { needed == null || it.unit == needed.unit }
                .takeIf { it.isNotEmpty() }
                ?.let { quantities ->
                    GroceryBaseQuantity(quantities.sumOf { it.value }, quantities.first().unit)
                }
        }
        val pantryQuantity = pantryQuantities
            .takeIf { it.isNotEmpty() }
            ?.let { quantities ->
                val unit = quantities.first().unit
                val sameUnit = quantities.filter { it.unit == unit }
                if (sameUnit.size == quantities.size) GroceryBaseQuantity(sameUnit.sumOf { it.value }, unit) else null
            }

        val coverage = when {
            needed != null && pantryQuantity != null && pantryQuantity.unit == needed.unit && pantryQuantity.value >= needed.value * 0.98 -> {
                PantryCoverage(
                    itemName = grocery.name,
                    status = PantryCoverageStatus.Full,
                    pantryQuantityDisplay = formatBaseQuantity(listOf(pantryQuantity)),
                    remainingQuantityDisplay = null,
                    detail = "Pantry quantity covers the planned amount.",
                )
            }
            needed != null && pantryQuantity != null && pantryQuantity.unit == needed.unit && pantryQuantity.value > 0.0 -> {
                val remaining = GroceryBaseQuantity((needed.value - pantryQuantity.value).coerceAtLeast(0.0), needed.unit)
                PantryCoverage(
                    itemName = grocery.name,
                    status = PantryCoverageStatus.Partial,
                    pantryQuantityDisplay = formatBaseQuantity(listOf(pantryQuantity)),
                    remainingQuantityDisplay = formatBaseQuantity(listOf(remaining)),
                    detail = "Pantry covers ${formatBaseQuantity(listOf(pantryQuantity))}; still buy ${formatBaseQuantity(listOf(remaining))}.",
                )
            }
            else -> PantryCoverage(
                itemName = grocery.name,
                status = PantryCoverageStatus.NameOnly,
                pantryQuantityDisplay = null,
                remainingQuantityDisplay = grocery.quantityDisplay,
                detail = "Pantry name matches, but quantity is not saved.",
            )
        }
        grocery.name to coverage
    }.toMap()
}

private fun normalizeTokenText(raw: String): String =
    raw
        .lowercase(Locale.ENGLISH)
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun canonicalGroceryIngredient(raw: String): GroceryCanonical {
    val normalized = normalizeTokenText(raw.replace(quantityPattern, " "))
    if (normalized.isBlank()) return GroceryCanonical("", "")
    val noCountUnits = normalized
        .split(" ")
        .filterNot { it in countUnitWords || it in unitAliases.keys || it in descriptorWords }
        .joinToString(" ")
    val synonymPhrase = ingredientSynonyms[noCountUnits]
    val searchable = synonymPhrase ?: noCountUnits.ifBlank { normalized }
    val knownPhrase = knownIngredientPhrases.firstOrNull { phrase ->
        Regex("""(^|\s)${Regex.escape(phrase)}(\s|$)""").containsMatchIn(searchable)
    }
    val selected = knownPhrase ?: searchable
        .split(" ")
        .map { ingredientSynonyms[it] ?: it }
        .firstOrNull { it in knownIngredientTokens || ingredientSynonyms.containsValue(it) }
        ?: searchable
    val canonical = ingredientSynonyms[selected] ?: selected
    val key = normalizeTokenText(canonical)
    return GroceryCanonical(key, displayNameOverrides[key] ?: key.toTitleCase())
}

private fun String.toTitleCase(): String =
    split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.ENGLISH) else char.toString()
            }
        }

private fun quantitySegmentsForItem(name: String, quantity: String): List<String> {
    val explicit = splitQuantitySegments(quantity)
    if (explicit.isNotEmpty() && explicit.any { quantityPattern.containsMatchIn(it) }) {
        return explicit
    }
    val fromName = quantityPattern.find(name)?.value?.trim()
    if (!fromName.isNullOrBlank()) return listOf(fromName)
    return explicit.ifEmpty { listOf("1 piece") }
}

private fun pantryQuantitySegments(entry: PantryEntry): List<String> {
    val quantity = entry.quantity?.trim().orEmpty()
    if (quantity.isNotBlank()) {
        val explicit = splitQuantitySegments(quantity)
        if (explicit.any { quantityPattern.containsMatchIn(it) }) return explicit
    }
    val fromName = quantityPattern.find(entry.name)?.value?.trim()
    return if (fromName.isNullOrBlank()) emptyList() else listOf(fromName)
}

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
        "piece", "clove", "bunch", "stalk", "can", "pack", "head" -> QuantityDimension.Count
        else -> return null
    }
    return ParsedQuantity(value, normalizedUnit, dimension)
}

private fun aggregateQuantitySegments(
    displayName: String,
    category: String,
    segments: List<String>,
): String {
    if (segments.isEmpty()) return ""
    val parsed = segments.mapNotNull(::parseQuantitySegment)
    if (parsed.size != segments.size) {
        return if (segments.distinct().size == 1) segments.first() else "Mixed amounts from ${segments.size} meals"
    }
    val baseQuantities = parsed.mapNotNull { it.toBaseQuantity(displayName, category) }
    if (baseQuantities.size == parsed.size && baseQuantities.map { it.unit }.distinct().size == 1) {
        return formatBaseQuantity(baseQuantities)
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

private fun ParsedQuantity.toBaseQuantity(displayName: String, category: String): GroceryBaseQuantity? {
    val key = canonicalGroceryKey(displayName)
    return when (unit) {
        "kg" -> GroceryBaseQuantity(value * 1000.0, GroceryBaseUnit.Gram)
        "g" -> GroceryBaseQuantity(value, GroceryBaseUnit.Gram)
        "lb" -> GroceryBaseQuantity(value * 453.592, GroceryBaseUnit.Gram)
        "oz" -> GroceryBaseQuantity(value * 28.3495, GroceryBaseUnit.Gram)
        "l" -> GroceryBaseQuantity(value * 1000.0, GroceryBaseUnit.Milliliter)
        "ml" -> GroceryBaseQuantity(value, GroceryBaseUnit.Milliliter)
        "cup" -> volumeSpoonToBase(value, 240.0, key, category)
        "tbsp" -> volumeSpoonToBase(value, 15.0, key, category)
        "tsp" -> volumeSpoonToBase(value, 5.0, key, category)
        "piece", "clove", "bunch", "stalk", "can", "pack", "head" -> {
            if (key == "egg" && unit == "piece") {
                return GroceryBaseQuantity(value, GroceryBaseUnit.Count)
            }
            val grams = ingredientPieceWeightGrams["$key:$unit"]
                ?: if (unit == "clove" && key == "garlic") 5.0 else null
                ?: defaultPieceWeightGrams(category, unit)
            GroceryBaseQuantity(value * grams, GroceryBaseUnit.Gram)
        }
        else -> null
    }
}

private fun volumeSpoonToBase(
    value: Double,
    mlPerUnit: Double,
    key: String,
    category: String,
): GroceryBaseQuantity {
    val volumeMl = value * mlPerUnit
    val liquid = category == "Beverages" ||
        key.contains("oil") ||
        key.contains("sauce") ||
        key.contains("vinegar") ||
        key.contains("milk") ||
        key.contains("water")
    return if (liquid) {
        GroceryBaseQuantity(volumeMl, GroceryBaseUnit.Milliliter)
    } else {
        GroceryBaseQuantity(volumeMl, GroceryBaseUnit.Gram)
    }
}

private fun defaultPieceWeightGrams(category: String, unit: String): Double = when (unit) {
    "bunch" -> 150.0
    "stalk" -> 40.0
    "can" -> 180.0
    "pack" -> 100.0
    "head" -> 250.0
    else -> when (category) {
        "Meat/Seafood" -> 180.0
        "Eggs & Dairy" -> 55.0
        "Produce" -> 100.0
        "Dry Goods" -> 100.0
        "Spices & Condiments" -> 20.0
        "Canned/Packaged" -> 180.0
        "Beverages" -> 250.0
        else -> 100.0
    }
}

private fun formatBaseQuantity(values: List<GroceryBaseQuantity>): String {
    val unit = values.firstOrNull()?.unit ?: return ""
    val total = values.sumOf { it.value }
    return when (unit) {
        GroceryBaseUnit.Gram -> if (total >= 1000.0) {
            "${formatScaledValue(total / 1000.0)} kg"
        } else {
            "${formatScaledValue(total)} g"
        }
        GroceryBaseUnit.Milliliter -> if (total >= 1000.0) {
            "${formatScaledValue(total / 1000.0)} L"
        } else {
            "${formatScaledValue(total)} ml"
        }
        GroceryBaseUnit.Count -> "${formatScaledValue(total)} pcs"
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
