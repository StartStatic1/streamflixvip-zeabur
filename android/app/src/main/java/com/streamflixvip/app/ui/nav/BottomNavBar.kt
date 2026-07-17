package com.streamflixvip.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState

/**
 * Barra de navegação inferior fixa — Início, Buscar, Gêneros, Favoritos,
 * Perfil. A aba Social (posts/comentários) foi substituída por Gêneros a
 * pedido: navegação por gênero tem uso imediato (grade de cards com
 * capas), enquanto Social ainda era só placeholder sem conteúdo real por
 * trás.
 */
sealed class BottomNavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Home : BottomNavItem("home", "Início", Icons.Filled.Home)
    data object Search : BottomNavItem("search", "Buscar", Icons.Filled.Search)
    data object Genres : BottomNavItem("genres", "Gêneros", Icons.Filled.Sell)
    data object MyList : BottomNavItem("mylist", "Favoritos", Icons.Filled.Favorite)
    data object Profile : BottomNavItem("profile", "Perfil", Icons.Filled.Person)
}

val bottomNavItems = listOf(
    BottomNavItem.Home,
    BottomNavItem.Search,
    BottomNavItem.Genres,
    BottomNavItem.MyList,
    BottomNavItem.Profile,
)

@Composable
fun StreamFlixBottomBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        bottomNavItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        // Evita empilhar múltiplas cópias da mesma aba ao
                        // alternar entre elas repetidamente.
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
            )
        }
    }
}
