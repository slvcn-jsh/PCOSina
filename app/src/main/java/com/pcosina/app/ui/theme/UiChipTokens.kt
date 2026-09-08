package com.pcosina.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object UiChipTokens {
    val CompactBreakpoint = GeneratedBreakpoints.Compact
    val MinTouchHeight = 44.dp

    fun widthByClass(
        screenWidthDp: Int,
        compact: Dp,
        medium: Dp
    ): Dp = if (screenWidthDp < CompactBreakpoint) compact else medium
}
