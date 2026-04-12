package com.pcosina.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun MoreToolsScreen(
    onBack: () -> Unit,
    onOpenMethodology: () -> Unit,
    onFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CommunityScreen(
        onBack = onBack,
        onFeedback = onFeedback,
        modifier = modifier
    )
}
