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
    private val rules = listOf(
        PriceRule(listOf("egg", "itlog"), 7, "Eggs & Dairy", "piece"),
        PriceRule(listOf("milk", "gatas"), 90, "Eggs & Dairy", "l"),
        PriceRule(listOf("cheese", "keso"), 300, "Eggs & Dairy", "kg"),
        PriceRule(listOf("yogurt"), 60, "Eggs & Dairy", "piece"),
        PriceRule(listOf("gata", "coconut milk"), 70, "Eggs & Dairy", "l"),
        PriceRule(listOf("rice", "bigas"), 60, "Dry Goods", "kg"),
        PriceRule(listOf("oat"), 140, "Dry Goods", "kg"),
        PriceRule(listOf("bread", "tinapay"), 80, "Dry Goods", "piece"),
        PriceRule(listOf("pasta", "noodles", "bihon", "miki", "pancit"), 90, "Dry Goods", "kg"),
        PriceRule(listOf("flour"), 60, "Dry Goods", "kg"),
        PriceRule(listOf("chicken", "manok"), 180, "Meat/Seafood", "kg"),
        PriceRule(listOf("beef"), 320, "Meat/Seafood", "kg"),
        PriceRule(listOf("pork", "liempo", "baboy"), 260, "Meat/Seafood", "kg"),
        PriceRule(listOf("fish", "tilapia", "bangus", "salmon", "galunggong"), 220, "Meat/Seafood", "kg"),
        PriceRule(listOf("tuna", "sardines"), 35, "Canned/Packaged", "piece"),
        PriceRule(listOf("shrimp", "hipon"), 300, "Meat/Seafood", "kg"),
        PriceRule(listOf("tomato", "kamatis"), 60, "Produce", "kg"),
        PriceRule(listOf("onion", "sibuyas"), 80, "Produce", "kg"),
        PriceRule(listOf("garlic", "bawang"), 120, "Produce", "kg"),
        PriceRule(listOf("carrot"), 70, "Produce", "kg"),
        PriceRule(listOf("cabbage", "repolyo"), 55, "Produce", "kg"),
        PriceRule(listOf("pechay", "spinach", "kale", "malunggay", "kangkong"), 60, "Produce", "kg"),
        PriceRule(listOf("okra", "ampalaya", "talong", "sayote", "kalabasa"), 70, "Produce", "kg"),
        PriceRule(listOf("sili", "chili"), 140, "Produce", "kg"),
        PriceRule(listOf("banana"), 60, "Produce", "kg"),
        PriceRule(listOf("apple"), 120, "Produce", "kg"),
        PriceRule(listOf("orange"), 80, "Produce", "kg"),
        PriceRule(listOf("ginger", "luya"), 140, "Produce", "kg"),
        PriceRule(listOf("oil", "olive", "coconut"), 120, "Spices & Condiments", "l"),
        PriceRule(listOf("soy", "toyo", "sauce", "vinegar", "suka", "patis"), 40, "Spices & Condiments", "piece"),
        PriceRule(listOf("salt", "asin", "pepper", "paminta", "spice"), 20, "Spices & Condiments", "piece"),
        PriceRule(listOf("coffee", "tea"), 90, "Beverages", "piece"),
        PriceRule(listOf("juice", "soda"), 40, "Beverages", "piece"),
        PriceRule(listOf("water"), 20, "Beverages", "piece"),
        PriceRule(listOf("canned", "packaged", "instant"), 45, "Canned/Packaged", "piece")
    )

    private val categoryAverages: Map<String, Int> = run {
        val grouped = rules.groupBy { it.category }
        grouped.mapValues { (_, list) -> list.map { it.pricePhp }.average().roundToInt() }
    }

    private val unitAliases = mapOf(
        "kilo" to "kg",
        "kilogram" to "kg",
        "grams" to "g",
        "gram" to "g",
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

    private val quantityPattern = Regex(
        """(?i)(\d+\s+\d+/\d+|\d+/\d+|\d+(?:\.\d+)?)\s*(kg|kilo|kilogram|g|gram|grams|lb|lbs|pound|pounds|oz|ml|l|liter|litre|cup|cups|tbsp|tablespoon|tablespoons|tsp|teaspoon|teaspoons|piece|pieces|pc|pcs|clove|cloves|bunch|bunches|stalk|stalks|can|cans|pack|packs)"""
    )

    fun estimatePrice(name: String): Int = estimatePriceDetail(name).first

    fun estimatePriceExplanation(
        name: String,
        quantityText: String = "",
        monthIndex: Int = LocalDate.now().monthValue,
        includeSafetyBuffer: Boolean = false,
    ): PriceEstimate {
        val rule = ruleForName(name)
        val category = rule?.category ?: inferCategory(name)
        val basePrice = rule?.pricePhp ?: (categoryAverages[category] ?: 60)
        val targetUnit = rule?.unit ?: (categoryDefaultUnit[category] ?: "piece")
        val (qtyValue, qtyUnit) = extractQuantity("$quantityText $name".trim())
        val factor = clampFactor(quantityFactor(qtyValue, qtyUnit, targetUnit, category), category)
        val marketMultiplier = defaultSeasonalMultiplier[category]?.get(monthIndex.coerceIn(1, 12)) ?: 1.0
        val tingiMultiplier = tingiMultiplier(qtyValue, qtyUnit, targetUnit)
        val safetyBuffer = if (includeSafetyBuffer) 1.10 else 1.0
        val scaledPrice = (
            basePrice *
                factor *
                (categoryMultiplier[category] ?: 0.75) *
                marketMultiplier *
                tingiMultiplier *
                safetyBuffer
            ).coerceAtLeast(5.0)
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

    fun estimatePriceDetail(name: String, quantityText: String = ""): Pair<Int, String> {
        val estimate = estimatePriceExplanation(name, quantityText)
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
        "tbsp" -> value * 0.015
        "tsp" -> value * 0.005
        else -> null
    }

    private fun quantityFactor(value: Double?, unit: String?, targetUnit: String, category: String): Double {
        if (value == null || unit == null) return 1.0
        return when (targetUnit) {
            "kg" -> {
                val kg = unitToKg(value, unit)
                    ?: if (unit == "piece" || unit == "clove" || unit == "bunch" || unit == "stalk") {
                        value * (pieceWeightKg[category] ?: 0.1)
                    } else {
                        null
                    }
                kg ?: 1.0
            }
            "l" -> unitToLiters(value, unit) ?: 1.0
            "piece" -> {
                when (unit) {
                    "piece", "clove", "bunch", "stalk" -> value
                    else -> {
                        val kg = unitToKg(value, unit) ?: return 1.0
                        val pieceWeight = pieceWeightKg[category] ?: 0.1
                        (kg / pieceWeight).coerceAtLeast(0.1)
                    }
                }
            }
            else -> 1.0
        }
    }

    private fun tingiMultiplier(value: Double?, unit: String?, targetUnit: String): Double {
        if (unit in setOf("piece", "clove", "bunch", "stalk", "can", "pack") && targetUnit in setOf("kg", "l")) {
            return 1.12
        }
        if (value != null && value > 0.0 && value < 0.25 && targetUnit in setOf("kg", "l")) {
            return 1.08
        }
        return 1.0
    }

    private fun clampFactor(value: Double, category: String): Double {
        val maxFactor = if (category in setOf("Meat/Seafood", "Dry Goods")) 2.5 else 2.0
        return value.coerceIn(0.1, maxFactor)
    }

    private fun ruleForName(name: String): PriceRule? {
        val lower = name.lowercase(Locale.getDefault())
        return rules.firstOrNull { rule -> rule.keywords.any { lower.contains(it) } }
    }
}
