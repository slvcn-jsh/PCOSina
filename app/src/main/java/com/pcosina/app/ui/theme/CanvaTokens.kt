package com.pcosina.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object CanvaTokens {
    val CanvasBackground = Color(0xFFFFFCFD)
    val PanelPink = Color(0xFFF77895)
    val PanelPinkSoft = Color(0xFFFFD3DD)
    val PanelPinkLight = Color(0xFFFFEEF3)
    val AccentPink = Color(0xFFFF6480)
    val AccentPinkStrong = Color(0xFFF55B79)
    val AccentPinkMuted = Color(0xFFF8A9B7)
    val HeadlineMaroon = Color(0xFF6C2432)
    val Ink = Color(0xFF1E1719)
    val Outline = Color(0xFF352528)
    val SoftOutline = Color(0xFFBCB0B4)
    val TipsBrown = Color(0xFFC89369)
    val BreakfastOrange = Color(0xFFFFB37D)
    val LunchPink = Color(0xFFFA88A0)
    val DinnerViolet = Color(0xFF8B90FF)
    val MintSuccess = Color(0xFFBDF5D4)
    val GreenStrong = Color(0xFF18B55B)
    val GreenDeep = Color(0xFF0D8B42)
    val RedSoft = Color(0xFFFAC0C7)
    val RedStrong = Color(0xFFE43F43)
    val ProgressTrack = Color(0xFFF5E7EA)
    val SupportGray = Color(0xFF80767A)
    val Shadow = Color(0x3329151A)

    val MainCtaGradient: Brush = Brush.horizontalGradient(
        listOf(Color(0xFFFF859C), Color(0xFFF55B79))
    )
    val HeroGradient: Brush = Brush.verticalGradient(
        listOf(Color(0xFFFFA3B6), Color(0xFFF77895))
    )
    val SurfaceGlowGradient: Brush = Brush.verticalGradient(
        listOf(Color(0xFFFFEEF3), Color(0xFFFFD7E1))
    )
}
