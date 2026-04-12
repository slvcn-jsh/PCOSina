package com.pcosina.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
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
    BottomNavItem(route = Routes.Ipo, label = "Community", icon = Icons.Filled.PieChart),
)

@Composable
fun BottomNavBar(
    currentDestination: NavDestination?,
    onNavigateToRoute: (String) -> Unit,
    enabledRoutes: Set<String> = DefaultBottomNavItems.map { it.route }.toSet(),
    onDisabledRouteClick: (String) -> Unit = {},
    items: List<BottomNavItem> = DefaultBottomNavItems,
) {
    NavigationBar {
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
                icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                label = { Text(text = item.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    unselectedTextColor = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                ),
            )
        }
    }
}
