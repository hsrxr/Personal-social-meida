package com.journal.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.journal.app.navigation.Screen

private data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
    // Detail routes that keep this tab highlighted (e.g. Full Journal under Home).
    val selectedRoutes: Set<String>,
)

private val bottomNavItems = listOf(
    BottomNavItem(
        label = "Home",
        icon = Icons.Filled.Home,
        route = Screen.Home.route,
        selectedRoutes = setOf(Screen.Home.route, Screen.FullJournal.route),
    ),
    BottomNavItem(
        label = "Discover",
        icon = Icons.Filled.Explore,
        route = Screen.Discover.route,
        selectedRoutes = setOf(Screen.Discover.route, Screen.FindSimilar.route),
    ),
    BottomNavItem(
        label = "Messages",
        icon = Icons.Outlined.Email,
        route = Screen.Messages.route,
        selectedRoutes = setOf(Screen.Messages.route),
    ),
)

/** Routes that display the bottom navigation bar; all other (pushed detail) routes hide it. */
val bottomNavRoutes: Set<String> = bottomNavItems.flatMap { it.selectedRoutes }.toSet()

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute in item.selectedRoutes
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onNavigate(item.route) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
