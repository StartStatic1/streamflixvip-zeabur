package com.streamflixvip.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    data object Home : BottomNavItem("home", "Início", Icons.Filled.Home)
    data object Explore : BottomNavItem("explore", "Explorar", Icons.Filled.Explore)
    data object Reels : BottomNavItem("reels", "Reels", Icons.Filled.VideoLibrary)
    data object LiveTv : BottomNavItem("livetv", "TV", Icons.Filled.LiveTv)
    data object Profile : BottomNavItem("profile", "Perfil", Icons.Filled.Person)
}

val bottomNavItems = listOf(
    BottomNavItem.Home,
    BottomNavItem.Explore,
    BottomNavItem.Reels,
    BottomNavItem.LiveTv,
    BottomNavItem.Profile,
)

private val OceanCyan = Color(0xFF00E5FF)

@Composable
fun StreamFlixBottomBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        containerColor = Color(0xFF0A0A12),
        contentColor = Color.White,
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (selected) return@NavigationBarItem
                    val restoredExistingDestination = navController.popBackStack(
                        route = item.route,
                        inclusive = false,
                    )
                    if (!restoredExistingDestination) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = {
                    Text(
                        item.label,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = OceanCyan,
                    selectedTextColor = OceanCyan,
                    unselectedIconColor = Color.White.copy(alpha = 0.55f),
                    unselectedTextColor = Color.White.copy(alpha = 0.55f),
                    indicatorColor = OceanCyan.copy(alpha = 0.14f),
                ),
            )
        }
    }
}
