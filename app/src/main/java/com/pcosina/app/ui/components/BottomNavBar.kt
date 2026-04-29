package com.pcosina.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import com.pcosina.app.ui.navigation.Routes
import com.pcosina.app.ui.theme.CanvaTokens

@Immutable
data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val DefaultBottomNavItems: List<BottomNavItem> = listOf(
    BottomNavItem(route = Routes.MealPlan, label = "Plan", icon = Icons.Filled.RestaurantMenu),
    BottomNavItem(route = Routes.GroceryList, label = "Grocery", icon = Icons.AutoMirrored.Filled.ListAlt),
    BottomNavItem(route = Routes.Dashboard, label = "Home", icon = Icons.Filled.Home),
    BottomNavItem(route = Routes.Progress, label = "Progress", icon = Icons.Filled.Insights),
    BottomNavItem(route = Routes.Ipo, label = "Support", icon = Icons.Filled.Help),
)

@Composable
fun BottomNavBar(
    currentDestination: NavDestination?,
    onNavigateToRoute: (String) -> Unit,
    enabledRoutes: Set<String> = DefaultBottomNavItems.map { it.route }.toSet(),
    onDisabledRouteClick: (String) -> Unit = {},
    items: List<BottomNavItem> = DefaultBottomNavItems,
) {
    Surface(
        color = Color.White.copy(alpha = 0.98f),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        tonalElevation = 0.dp,
        shadowElevation = 14.dp,
        border = BorderStroke(
            width = 1.dp,
            color = CanvaTokens.SoftOutline.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 92.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            items.forEach { item ->
                val selected = currentDestination
                    ?.hierarchy
                    ?.any { it.route == item.route } == true
                val isEnabled = enabledRoutes.contains(item.route)

                BottomNavItemButton(
                    item = item,
                    selected = selected,
                    enabled = isEnabled,
                    onClick = {
                        if (!isEnabled) {
                            onDisabledRouteClick(item.route)
                            return@BottomNavItemButton
                        }
                        onNavigateToRoute(item.route)
                    },
                )
            }
        }
    }
}

@Composable
private fun BottomNavItemButton(
    item: BottomNavItem,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = when {
        selected -> CanvaTokens.PanelPink
        enabled -> CanvaTokens.SupportGray
        else -> CanvaTokens.SupportGray.copy(alpha = 0.45f)
    }
    val isHome = item.route == Routes.Dashboard

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
            shape = if (isHome) CircleShape else RoundedCornerShape(22.dp),
            color = when {
                isHome -> CanvaTokens.PanelPink
                selected -> CanvaTokens.PanelPinkLight
                else -> Color.Transparent
            },
            border = if (isHome) null else BorderStroke(
                0.dp,
                Color.Transparent,
            ),
            shadowElevation = if (isHome) 12.dp else 0.dp,
        ) {
            Box(
                modifier = Modifier
                    .size(if (isHome) 76.dp else 58.dp)
                    .padding(if (isHome) 0.dp else 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = if (isHome) Color.White else tint,
                    modifier = Modifier.size(if (isHome) 36.dp else 28.dp),
                )
            }
        }

        Text(
            text = item.label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected || isHome) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (isHome) CanvaTokens.PanelPink else tint,
        )
    }
}
