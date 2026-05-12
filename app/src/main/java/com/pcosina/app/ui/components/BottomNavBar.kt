package com.pcosina.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import com.pcosina.app.R
import com.pcosina.app.ui.navigation.Routes
import com.pcosina.app.ui.theme.PcosinaDeepRose
import com.pcosina.app.ui.theme.PcosinaPink
import com.pcosina.app.ui.theme.PcosinaSurface
import com.pcosina.app.ui.theme.PcosinaSurfaceAlt

@Immutable
data class BottomNavItem(
    val route: String,
    val label: String,
    @DrawableRes val iconRes: Int,
    val isCenterItem: Boolean = false,
)

val DefaultBottomNavItems: List<BottomNavItem> = listOf(
    BottomNavItem(route = Routes.MealPlan, label = "Plan", iconRes = R.drawable.pcosina_nav_plan),
    BottomNavItem(route = Routes.GroceryList, label = "Grocery", iconRes = R.drawable.pcosina_nav_grocery),
    BottomNavItem(route = Routes.Dashboard, label = "Home", iconRes = R.drawable.pcosina_nav_home, isCenterItem = true),
    BottomNavItem(route = Routes.Progress, label = "Progress", iconRes = R.drawable.pcosina_nav_progress),
    BottomNavItem(route = Routes.Ipo, label = "Support", iconRes = R.drawable.pcosina_nav_support_clean),
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
        color = PcosinaSurface,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(
            width = 1.dp,
            color = PcosinaPink.copy(alpha = 0.18f)
        )
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            modifier = Modifier
                .heightIn(min = 88.dp)
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
                            iconRes = item.iconRes,
                            label = item.label,
                            selected = selected,
                            enabled = isEnabled,
                            isCenterItem = item.isCenterItem,
                        )
                    },
                    label = {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = when {
                                selected -> PcosinaPink
                                isEnabled -> colorScheme.onSurfaceVariant
                                else -> colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            }
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PcosinaPink,
                        selectedTextColor = PcosinaPink,
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
    @DrawableRes iconRes: Int,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    isCenterItem: Boolean,
    modifier: Modifier = Modifier
) {
    val tint = when {
        !enabled -> PcosinaDeepRose.copy(alpha = 0.35f)
        selected -> Color.White
        isCenterItem -> PcosinaPink
        label == "Progress" -> PcosinaDeepRose.copy(alpha = 0.86f)
        else -> PcosinaDeepRose.copy(alpha = 0.78f)
    }
    val iconSize = when {
        isCenterItem -> 34.dp
        label == "Progress" -> 31.dp
        label == "Support" -> 31.dp
        else -> 29.dp
    }
    val containerColor = when {
        selected && isCenterItem -> PcosinaPink
        selected -> PcosinaPink.copy(alpha = 0.86f)
        enabled -> Color.Transparent
        else -> PcosinaSurfaceAlt.copy(alpha = 0.7f)
    }
    val containerModifier = if (isCenterItem) {
        modifier.size(64.dp)
    } else {
        modifier
            .width(56.dp)
            .heightIn(min = 50.dp)
    }
    Surface(
        modifier = containerModifier,
        shape = if (isCenterItem) CircleShape else RoundedCornerShape(14.dp),
        color = containerColor,
        border = when {
            isCenterItem && !selected && enabled -> BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.55f))
            isCenterItem && !enabled -> BorderStroke(1.dp, PcosinaDeepRose.copy(alpha = 0.18f))
            else -> null
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            PcosinaDesignIcon(
                resId = iconRes,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
