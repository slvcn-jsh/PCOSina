package com.pcosina.app.domain

import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

data class PriceRule(
    val keywords: List<String>,
    val pricePhp: Int,
    val category: String,
    val unit: String,
    val sourceLabel: String = "Offline SRP-style baseline",
    val confidence: String = "medium",
    val isObservedRetailPrice: Boolean = false,
)

data class PriceEstimate(
    val pricePhp: Int,
    val category: String,
    val sourceLabel: String,
    val confidence: String,
    val marketMultiplier: Double,
    val tingiMultiplier: Double,
    val quantityFactor: Double,
)

object PriceCatalog {
    const val CURRENT_CATALOG_VERSION = "pcosina-ncr-retail-2026-09-06-v1"
    const val CURRENT_REFERENCE_DATE = "2026-09-06"
    private const val currentDaSource = "DA-AMAS NCR weekly average (Aug 31-Sep 6, 2026)"

    private val rules = listOf(
        PriceRule(listOf("egg", "itlog"), 8, "Eggs & Dairy", "piece", currentDaSource, "high", true),
        PriceRule(listOf("milk", "gatas"), 90, "Eggs & Dairy", "l"),
        PriceRule(listOf("cheese", "keso"), 300, "Eggs & Dairy", "kg"),
        PriceRule(listOf("yogurt"), 60, "Eggs & Dairy", "piece"),
        PriceRule(listOf("gata", "coconut milk"), 70, "Eggs & Dairy", "l"),
        PriceRule(listOf("rice", "bigas"), 49, "Dry Goods", "kg", currentDaSource, "high", true),
        PriceRule(listOf("oat"), 140, "Dry Goods", "kg"),
        PriceRule(listOf("bread", "tinapay"), 80, "Dry Goods", "piece"),
        PriceRule(listOf("pasta", "noodles", "bihon", "miki", "pancit"), 90, "Dry Goods", "kg"),
        PriceRule(listOf("flour"), 60, "Dry Goods", "kg"),
        PriceRule(listOf("chicken", "manok"), 205, "Meat/Seafood", "kg", currentDaSource, "medium", true),
        PriceRule(listOf("beef"), 441, "Meat/Seafood", "kg", currentDaSource, "medium", true),
        PriceRule(listOf("pork belly", "liempo"), 379, "Meat/Seafood", "kg", currentDaSource, "high", true),
        PriceRule(listOf("pork", "baboy"), 325, "Meat/Seafood", "kg", currentDaSource, "medium", true),
        PriceRule(listOf("bangus", "milkfish"), 244, "Meat/Seafood", "kg", currentDaSource, "high", true),
        PriceRule(listOf("galunggong"), 323, "Meat/Seafood", "kg", currentDaSource, "high", true),
        PriceRule(listOf("tilapia"), 157, "Meat/Seafood", "kg", currentDaSource, "high", true),
        PriceRule(listOf("tuna", "tambakol"), 321, "Meat/Seafood", "kg", currentDaSource, "high", true),
        PriceRule(listOf("squid", "pusit"), 468, "Meat/Seafood", "kg", currentDaSource, "high", true),
        PriceRule(listOf("fish", "salmon"), 220, "Meat/Seafood", "kg"),
        PriceRule(listOf("canned tuna", "canned sardines"), 35, "Canned/Packaged", "piece"),
        PriceRule(listOf("shrimp", "hipon"), 300, "Meat/Seafood", "kg"),
        PriceRule(listOf("tomato", "kamatis"), 109, "Produce", "kg", currentDaSource, "high", true),
        PriceRule(listOf("white onion", "sibuyas puti"), 133, "Produce", "kg", currentDaSource, "high", true),
        PriceRule(listOf("red onion", "sibuyas pula"), 116, "Produce", "kg", currentDaSource, "high", true),
        PriceRule(listOf("onion", "sibuyas"), 116, "Produce", "kg", currentDaSource, "medium", true),
        PriceRule(listOf("garlic", "bawang"), 151, "Produce", "kg", currentDaSource, "medium", true),
        PriceRule(listOf("carrot"), 117, "Produce", "kg", currentDaSource, "high", true),
        PriceRule(listOf("cabbage", "repolyo"), 148, "Produce", "kg", currentDaSource, "high", true),
        PriceRule(listOf("pechay"), 203, "Produce", "kg", currentDaSource, "high", true),
        PriceRule(listOf("string beans", "sitaw"), 185, "Produce", "kg", currentDaSource, "high", true),
        PriceRule(listOf("ampalaya", "bitter melon"), 192, "Produce", "kg", currentDaSource, "high", true),
        PriceRule(listOf("talong", "eggplant"), 186, "Produce", "kg", currentDaSource, "high", true),
        PriceRule(listOf("sayote"), 102, "Produce", "kg", currentDaSource, "high", true),
        PriceRule(listOf("kalabasa", "squash"), 67, "Produce", "kg", currentDaSource, "high", true),
        PriceRule(listOf("spinach", "kale", "malunggay", "kangkong", "okra"), 80, "Produce", "kg"),
        PriceRule(listOf("sili", "chili"), 197, "Produce", "kg", currentDaSource, "high", true),
        PriceRule(listOf("banana", "saging"), 77, "Produce", "kg", currentDaSource, "medium", true),
        PriceRule(listOf("apple"), 120, "Produce", "kg"),
        PriceRule(listOf("orange"), 80, "Produce", "kg"),
        PriceRule(listOf("papaya"), 78, "Produce", "kg", currentDaSource, "high", true),
        PriceRule(listOf("calamansi"), 111, "Produce", "kg", currentDaSource, "high", true),
        PriceRule(listOf("ginger", "luya"), 188, "Produce", "kg", currentDaSource, "high", true),
        PriceRule(listOf("cooking oil", "canola oil"), 100, "Spices & Condiments", "l", currentDaSource, "medium", true),
        PriceRule(listOf("oil", "olive", "coconut"), 120, "Spices & Condiments", "l"),
        PriceRule(listOf("soy", "toyo", "sauce", "vinegar", "suka", "patis"), 40, "Spices & Condiments", "piece"),
        PriceRule(listOf("salt", "asin"), 42, "Spices & Condiments", "kg", currentDaSource, "high", true),
        PriceRule(listOf("white sugar", "refined sugar", "granulated white sugar"), 81, "Dry Goods", "kg", currentDaSource, "high", true),
        PriceRule(listOf("pepper", "paminta", "spice"), 20, "Spices & Condiments", "piece"),
        PriceRule(listOf("coffee", "tea"), 90, "Beverages", "piece"),
        PriceRule(listOf("juice", "soda"), 40, "Beverages", "piece"),
        PriceRule(listOf("tap water", "water"), 0, "Beverages", "l", sourceLabel = "Household tap water baseline", confidence = "high"),
        PriceRule(listOf("canned", "packaged", "instant"), 45, "Canned/Packaged", "piece")
    )

    private val categoryAverages: Map<String, Int> = run {
        val grouped = rules.groupBy { it.category }
        grouped.mapValues { (_, list) -> list.map { it.pricePhp }.average().roundToInt() }
    }

    private val unitAliases = mapOf(
        "kilo" to "kg",
        "kilogram" to "kg",
        "kilograms" to "kg",
        "grams" to "g",
        "gram" to "g",
        "lbs" to "lb",
        "pound" to "lb",
        "pounds" to "lb",
        "ounce" to "oz",
        "ounces" to "oz",
        "liter" to "l",
        "litre" to "l",
        "liters" to "l",
        "litres" to "l",
        "milliliter" to "ml",
        "milliliters" to "ml",
        "cups" to "cup",
        "glasses" to "glass",
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
        "heads" to "head",
        "cans" to "piece",
        "pack" to "piece",
        "packs" to "piece",
    )

    private val categoryDefaultUnit = mapOf(
        "Produce" to "kg",
        "Meat/Seafood" to "kg",
        "Eggs & Dairy" to "piece",
        "Dry Goods" to "kg",
        "Spices & Condiments" to "piece",
        "Canned/Packaged" to "piece",
        "Beverages" to "piece",
        "Others" to "piece"
    )

    private val categoryMultiplier = mapOf(
        "Produce" to 0.75,
        "Meat/Seafood" to 0.85,
        "Eggs & Dairy" to 0.85,
        "Dry Goods" to 0.8,
        "Spices & Condiments" to 0.7,
        "Canned/Packaged" to 0.8,
        "Beverages" to 0.8,
        "Others" to 0.75
    )

    private val defaultSeasonalMultiplier = mapOf(
        "Produce" to mapOf(6 to 1.06, 7 to 1.10, 8 to 1.12, 9 to 1.12, 10 to 1.08, 11 to 1.04),
        "Meat/Seafood" to mapOf(7 to 1.04, 8 to 1.06, 9 to 1.06, 10 to 1.04)
    )

    private val volatileIngredientTokens = setOf(
        "chili", "sili", "calamansi", "tomato", "kamatis", "onion", "sibuyas", "garlic", "bawang",
        "fish", "tilapia", "bangus", "galunggong"
    )

    private val pieceWeightKg = mapOf(
        "Produce" to 0.12,
        "Meat/Seafood" to 0.15,
        "Eggs & Dairy" to 0.06,
        "Dry Goods" to 0.10,
        "Spices & Condiments" to 0.05,
        "Canned/Packaged" to 0.18,
        "Beverages" to 0.25,
        "Others" to 0.10
    )

    private val ingredientPieceWeightKg = mapOf(
        "garlic:clove" to 0.005,
        "garlic:piece" to 0.005,
        "garlic:head" to 0.045,
        "onion:piece" to 0.11,
        "tomato:piece" to 0.09,
        "ginger:piece" to 0.02,
        "egg:piece" to 0.055,
        "pechay:bunch" to 0.18,
        "kangkong:bunch" to 0.18,
        "malunggay:bunch" to 0.08,
    )

    private val quantityPattern = Regex(
        """(?i)(\d+\s+\d+/\d+|\d+/\d+|\d+(?:\.\d+)?)\s*(kg|kilo|kilogram|kilograms|g|gram|grams|lb|lbs|pound|pounds|oz|ounce|ounces|ml|milliliter|milliliters|l|liter|litre|liters|litres|cup|cups|glass|glasses|tbsp|tablespoon|tablespoons|tsp|teaspoon|teaspoons|piece|pieces|pc|pcs|clove|cloves|bunch|bunches|stalk|stalks|can|cans|pack|packs|head|heads)\.?(?![a-z])"""
    )

    fun estimatePrice(name: String): Int = estimatePriceDetail(name).first

    fun reviewRules(): List<PriceRule> = rules

    fun estimatePriceExplanation(
        name: String,
        quantityText: String = "",
        monthIndex: Int = LocalDate.now().monthValue,
        includeSafetyBuffer: Boolean = false,
        clampQuantity: Boolean = true,
    ): PriceEstimate {
        val rule = ruleForName(name)
        val category = rule?.category ?: inferCategory(name)
        val basePrice = rule?.pricePhp ?: (categoryAverages[category] ?: 60)
        val targetUnit = rule?.unit ?: (categoryDefaultUnit[category] ?: "piece")
        val (qtyValue, qtyUnit) = extractQuantity("$quantityText $name".trim())
        val rawFactor = quantityFactor(qtyValue, qtyUnit, targetUnit, category, name)
        val factor = if (clampQuantity) clampFactor(rawFactor, category) else rawFactor.coerceAtLeast(0.0)
        val marketMultiplier = if (rule?.isObservedRetailPrice == true) {
            1.0
        } else {
            defaultSeasonalMultiplier[category]?.get(monthIndex.coerceIn(1, 12)) ?: 1.0
        }
        val tingiMultiplier = if (rule?.isObservedRetailPrice == true) 1.0 else tingiMultiplier(qtyValue, qtyUnit, targetUnit)
        val safetyBuffer = if (includeSafetyBuffer) 1.10 else 1.0
        val scaledPrice = if (basePrice <= 0) {
            0.0
        } else {
            (
                basePrice *
                    factor *
                    (if (rule?.isObservedRetailPrice == true) 1.0 else categoryMultiplier[category] ?: 0.75) *
                    marketMultiplier *
                    tingiMultiplier *
                    safetyBuffer
                ).coerceAtLeast(5.0)
        }
        val confidence = when {
            rule == null -> "low"
            volatileIngredientTokens.any { name.lowercase(Locale.getDefault()).contains(it) } -> "medium"
            else -> rule.confidence
        }
        return PriceEstimate(
            pricePhp = scaledPrice.roundToInt(),
            category = category,
            sourceLabel = rule?.sourceLabel ?: "Offline category average fallback",
            confidence = confidence,
            marketMultiplier = marketMultiplier,
            tingiMultiplier = tingiMultiplier,
            quantityFactor = factor,
        )
    }

    fun estimatePriceDetail(
        name: String,
        quantityText: String = "",
        clampQuantity: Boolean = true,
    ): Pair<Int, String> {
        val estimate = estimatePriceExplanation(name, quantityText, clampQuantity = clampQuantity)
        return estimate.pricePhp to estimate.category
    }

    fun inferCategory(name: String): String {
        val raw = name.lowercase(Locale.getDefault()).trim()
        if (raw.isBlank()) return "Others"
        return when {
            listOf(
                "lettuce","spinach","cabbage","carrot","broccoli","kale","tomato","onion","garlic","pepper",
                "pechay","ampalaya","okra","eggplant","talong","sayote","squash","kalabasa","ginger","luya",
                "gabi","kamote","cucumber","pipino","malunggay","kangkong","sili","chili",
                "banana","apple","orange","mango","grape","papaya","pineapple","strawberry","melon","calamansi"
            ).any { raw.contains(it) } -> "Produce"
            listOf(
                "chicken","manok","beef","pork","baboy","liempo","fish","salmon","tuna","shrimp","tilapia",
                "meat","bangus","sardine","galunggong","tocino","longganisa"
            ).any { raw.contains(it) } -> "Meat/Seafood"
            listOf("egg","itlog","milk","gatas","cheese","keso","yogurt","butter","cream").any { raw.contains(it) } -> "Eggs & Dairy"
            listOf(
                "rice","bigas","oat","bread","pasta","noodles","bihon","miki","pancit","flour",
                "grains","cereal","quinoa","barley","corn","frozen","dried","beans","lentils"
            ).any { raw.contains(it) } -> "Dry Goods"
            listOf("salt","pepper","soy","toyo","sauce","vinegar","suka","patis","spice","condiment","oil","sugar","honey","bagoong").any { raw.contains(it) } ->
                "Spices & Condiments"
            listOf("canned","packaged","instant","biscuit","cracker","chips","snack","noodles").any { raw.contains(it) } ->
                "Canned/Packaged"
            listOf("juice","soda","coffee","tea","water","milk tea").any { raw.contains(it) } ->
                "Beverages"
            else -> "Others"
        }
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

    private fun extractQuantity(text: String): Pair<Double?, String?> {
        val match = quantityPattern.find(text)
        val qty = parseNumber(match?.groupValues?.getOrNull(1).orEmpty())
        val unit = match?.groupValues?.getOrNull(2)
            ?.trim()
            ?.lowercase(Locale.ENGLISH)
            ?.let { unitAliases[it] ?: it }
        return qty to unit
    }

    private fun unitToKg(value: Double, unit: String): Double? = when (unit) {
        "kg" -> value
        "g" -> value / 1000.0
        "lb" -> value / 2.2046
        "oz" -> value / 35.274
        "cup" -> value * 0.25
        "tbsp" -> value * 0.015
        "tsp" -> value * 0.005
        else -> null
    }

    private fun unitToLiters(value: Double, unit: String): Double? = when (unit) {
        "l" -> value
        "ml" -> value / 1000.0
        "cup" -> value * 0.24
        "glass" -> value * 0.24
        "tbsp" -> value * 0.015
        "tsp" -> value * 0.005
        else -> null
    }

    private fun quantityFactor(value: Double?, unit: String?, targetUnit: String, category: String, name: String): Double {
        if (value == null || unit == null) return 1.0
        return when (targetUnit) {
            "kg" -> {
                val kg = unitToKg(value, unit)
                    ?: if (unit in setOf("piece", "clove", "bunch", "stalk", "head")) {
                        value * pieceWeightFor(name, category, unit)
                    } else {
                        null
                    }
                kg ?: 1.0
            }
            "l" -> unitToLiters(value, unit) ?: 1.0
            "piece" -> {
                when (unit) {
                    "piece", "clove", "bunch", "stalk", "can", "pack", "head" -> value
                    else -> {
                        val kg = unitToKg(value, unit) ?: return 1.0
                        val pieceWeight = pieceWeightFor(name, category, "piece")
                        (kg / pieceWeight).coerceAtLeast(0.1)
                    }
                }
            }
            else -> 1.0
        }
    }

    private fun pieceWeightFor(name: String, category: String, unit: String): Double {
        val lower = name.lowercase(Locale.getDefault())
        val ingredient = when {
            lower.contains("garlic") || lower.contains("bawang") -> "garlic"
            lower.contains("onion") || lower.contains("sibuyas") -> "onion"
            lower.contains("tomato") || lower.contains("kamatis") -> "tomato"
            lower.contains("ginger") || lower.contains("luya") -> "ginger"
            lower.contains("egg") || lower.contains("itlog") -> "egg"
            lower.contains("pechay") -> "pechay"
            lower.contains("kangkong") -> "kangkong"
            lower.contains("malunggay") -> "malunggay"
            else -> ""
        }
        return ingredientPieceWeightKg["$ingredient:$unit"]
            ?: pieceWeightKg[category]
            ?: 0.1
    }

    private fun tingiMultiplier(value: Double?, unit: String?, targetUnit: String): Double {
        if (unit in setOf("piece", "clove", "bunch", "stalk", "can", "pack", "head") && targetUnit in setOf("kg", "l")) {
            return 1.12
        }
        if (value != null && value > 0.0 && value < 0.25 && targetUnit in setOf("kg", "l")) {
            return 1.08
        }
        return 1.0
    }

    private fun clampFactor(value: Double, category: String): Double {
        val maxFactor = if (category in setOf("Meat/Seafood", "Dry Goods")) 2.5 else 2.0
        val minFactor = if (category in setOf("Produce", "Spices & Condiments")) 0.02 else 0.1
        return value.coerceIn(minFactor, maxFactor)
    }

    private fun ruleForName(name: String): PriceRule? {
        val normalizedName = normalizePriceTokens(name)
        return rules
            .mapNotNull { rule ->
            if (rule.pricePhp == 0 && normalizedName !in setOf("water", "tap water")) {
                    return@mapNotNull null
            }
                val matchedKeywords = rule.keywords.filter { keyword ->
                    pricePhraseMatches(normalizedName, normalizePriceTokens(keyword))
                }
                if (matchedKeywords.isEmpty()) {
                    null
                } else {
                    rule to matchedKeywords.maxWithOrNull(
                        compareBy<String> { normalizePriceTokens(it).split(" ").size }
                            .thenBy { normalizePriceTokens(it).length }
                    )!!
                }
            }
            .maxWithOrNull(
                compareBy<Pair<PriceRule, String>> {
                    normalizePriceTokens(it.second).split(" ").size
                }.thenBy { normalizePriceTokens(it.second).length }
            )
            ?.first
    }

    private fun pricePhraseMatches(name: String, phrase: String): Boolean {
        val nameTokens = name.split(" ").filter { it.isNotBlank() }
        val phraseTokens = phrase.split(" ").filter { it.isNotBlank() }
        if (phraseTokens.isEmpty() || phraseTokens.size > nameTokens.size) return false
        return nameTokens.windowed(phraseTokens.size).any { candidate ->
            candidate.zip(phraseTokens).all { (left, right) -> priceTokenMatches(left, right) }
        }
    }

    private fun priceTokenMatches(left: String, right: String): Boolean {
        if (left == right) return true
        return left in pluralForms(right) || right in pluralForms(left)
    }

    private fun pluralForms(value: String): Set<String> = buildSet {
        add(value + "s")
        if (value.length > 1 && value.endsWith("y") && value[value.lastIndex - 1] !in "aeiou") {
            add(value.dropLast(1) + "ies")
        }
        if (listOf("s", "x", "z", "ch", "sh", "o").any(value::endsWith)) {
            add(value + "es")
        }
    }

    private fun normalizePriceTokens(value: String): String = value
        .lowercase(Locale.ENGLISH)
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
