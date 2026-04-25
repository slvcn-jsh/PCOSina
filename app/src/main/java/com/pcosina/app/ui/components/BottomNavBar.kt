package com.pcosina.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import com.pcosina.app.ui.navigation.Routes

@Immutable
data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val DefaultBottomNavItems: List<BottomNavItem> = listOf(
    BottomNavItem(route = Routes.Dashboard, label = "Home", icon = Icons.Filled.Home),
    BottomNavItem(route = Routes.MealPlan, label = "Plan", icon = Icons.Filled.RestaurantMenu),
    BottomNavItem(route = Routes.GroceryList, label = "Grocery", icon = Icons.AutoMirrored.Filled.ListAlt),
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
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        color = colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 0.dp,
        shadowElevation = 14.dp,
        border = BorderStroke(
            width = 1.dp,
            color = colorScheme.outlineVariant.copy(alpha = 0.65f)
        )
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            modifier = Modifier
                .heightIn(min = 84.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            items.forEach { item ->
                val selected = currentDestination
                    ?.hierarchy
                    ?.any { it.route == item.route } == true
                val isEnabled = enabledRoutes.contains(item.route)

                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        if (!isEnabled) {
                            onDisabledRouteClick(item.route)
                            return@NavigationBarItem
                        }
                        onNavigateToRoute(item.route)
                    },
                    alwaysShowLabel = true,
                    icon = {
                        BottomNavItemIcon(
                            icon = item.icon,
                            label = item.label,
                            selected = selected,
                            enabled = isEnabled
                        )
                    },
                    label = {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = when {
                                selected -> colorScheme.primary
                                isEnabled -> colorScheme.onSurfaceVariant
                                else -> colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            }
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colorScheme.primary,
                        selectedTextColor = colorScheme.primary,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = if (isEnabled) colorScheme.onSurfaceVariant
                        else colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        unselectedTextColor = if (isEnabled) colorScheme.onSurfaceVariant
                        else colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun BottomNavItemIcon(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val tint = when {
        selected -> colorScheme.primary
        enabled -> colorScheme.onSurfaceVariant
        else -> colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    val containerColor = when {
        selected -> colorScheme.primary.copy(alpha = 0.12f)
        enabled -> Color.Transparent
        else -> colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = containerColor
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
