package com.pcosina.app.data.model

data class ArtworkAlignmentConfig(
    val offsetXDp: Float = 0f,
    val offsetYDp: Float = 0f,
    val scale: Float = 1f,
) {
    fun clamped(
        minScale: Float = MinScale,
        maxScale: Float = MaxScale,
    ): ArtworkAlignmentConfig =
        copy(
            offsetXDp = offsetXDp.coerceIn(MinOffsetDp, MaxOffsetDp),
            offsetYDp = offsetYDp.coerceIn(MinOffsetDp, MaxOffsetDp),
            scale = scale.coerceIn(minScale, maxScale),
        )

    companion object {
        const val MinOffsetDp = -600f
        const val MaxOffsetDp = 600f
        const val MinScale = 0.45f
        const val MaxScale = 2.25f
        val Default = ArtworkAlignmentConfig()
    }
}
