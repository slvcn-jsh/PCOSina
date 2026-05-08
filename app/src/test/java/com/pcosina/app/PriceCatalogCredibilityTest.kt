package com.pcosina.app

import com.pcosina.app.domain.PriceCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceCatalogCredibilityTest {

    @Test
    fun estimatePriceExplanation_appliesSeasonalityForRainyMonthProduce() {
        val summer = PriceCatalog.estimatePriceExplanation("tomato", "1 kg", monthIndex = 3)
        val rainy = PriceCatalog.estimatePriceExplanation("tomato", "1 kg", monthIndex = 8)

        assertTrue(rainy.marketMultiplier > summer.marketMultiplier)
        assertTrue(rainy.pricePhp > summer.pricePhp)
        assertEquals("medium", rainy.confidence)
    }

    @Test
    fun estimatePriceExplanation_appliesTingiFactorForPieceProduce() {
        val estimate = PriceCatalog.estimatePriceExplanation("kamatis", "2 pcs", monthIndex = 3)

        assertTrue(estimate.tingiMultiplier > 1.0)
        assertEquals("Produce", estimate.category)
    }

    @Test
    fun estimatePriceExplanation_labelsFallbacksAsLowConfidence() {
        val estimate = PriceCatalog.estimatePriceExplanation("unknown ingredient", "1 pack", monthIndex = 3)

        assertEquals("low", estimate.confidence)
        assertEquals("Offline category average fallback", estimate.sourceLabel)
    }
}
