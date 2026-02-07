package com.pcosina.app.domain

import java.util.Locale

data class PriceRule(
    val keywords: List<String>,
    val pricePhp: Int,
    val category: String
)

object PriceCatalog {
    private val rules = listOf(
        PriceRule(listOf("egg", "itlog"), 110, "Eggs & Dairy"),
        PriceRule(listOf("milk", "gatas"), 90, "Eggs & Dairy"),
        PriceRule(listOf("cheese", "keso"), 120, "Eggs & Dairy"),
        PriceRule(listOf("yogurt"), 80, "Eggs & Dairy"),
        PriceRule(listOf("gata", "coconut milk"), 70, "Eggs & Dairy"),
        PriceRule(listOf("rice", "bigas"), 60, "Dry Goods"),
        PriceRule(listOf("oat"), 120, "Dry Goods"),
        PriceRule(listOf("bread", "tinapay"), 80, "Dry Goods"),
        PriceRule(listOf("pasta", "noodles", "bihon", "miki", "pancit"), 50, "Dry Goods"),
        PriceRule(listOf("flour"), 60, "Dry Goods"),
        PriceRule(listOf("chicken", "manok"), 180, "Meat/Seafood"),
        PriceRule(listOf("beef"), 320, "Meat/Seafood"),
        PriceRule(listOf("pork", "liempo", "baboy"), 260, "Meat/Seafood"),
        PriceRule(listOf("fish", "tilapia", "bangus", "salmon", "galunggong"), 220, "Meat/Seafood"),
        PriceRule(listOf("tuna", "sardines"), 55, "Canned/Packaged"),
        PriceRule(listOf("shrimp", "hipon"), 300, "Meat/Seafood"),
        PriceRule(listOf("tomato", "kamatis"), 30, "Produce"),
        PriceRule(listOf("onion", "sibuyas"), 20, "Produce"),
        PriceRule(listOf("garlic", "bawang"), 15, "Produce"),
        PriceRule(listOf("carrot"), 25, "Produce"),
        PriceRule(listOf("cabbage", "repolyo"), 35, "Produce"),
        PriceRule(listOf("pechay", "spinach", "kale", "malunggay", "kangkong"), 25, "Produce"),
        PriceRule(listOf("okra", "ampalaya", "talong", "sayote", "kalabasa"), 30, "Produce"),
        PriceRule(listOf("sili", "chili"), 15, "Produce"),
        PriceRule(listOf("banana"), 25, "Produce"),
        PriceRule(listOf("apple"), 35, "Produce"),
        PriceRule(listOf("orange"), 30, "Produce"),
        PriceRule(listOf("ginger", "luya"), 15, "Produce"),
        PriceRule(listOf("oil", "olive", "coconut"), 120, "Spices & Condiments"),
        PriceRule(listOf("soy", "toyo", "sauce", "vinegar", "suka", "patis"), 30, "Spices & Condiments"),
        PriceRule(listOf("salt", "asin", "pepper", "paminta", "spice"), 20, "Spices & Condiments"),
        PriceRule(listOf("coffee", "tea"), 80, "Beverages"),
        PriceRule(listOf("juice", "soda"), 40, "Beverages"),
        PriceRule(listOf("water"), 25, "Beverages"),
        PriceRule(listOf("canned", "packaged", "instant"), 50, "Canned/Packaged")
    )

    private val categoryAverages: Map<String, Int> = run {
        val grouped = rules.groupBy { it.category }
        grouped.mapValues { (_, list) -> list.map { it.pricePhp }.average().toInt() }
    }

    fun estimatePrice(name: String): Int {
        val lower = name.lowercase(Locale.getDefault())
        val match = rules.firstOrNull { rule -> rule.keywords.any { lower.contains(it) } }
        if (match != null) return match.pricePhp
        val category = inferCategory(name)
        return categoryAverages[category] ?: 60
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
}
