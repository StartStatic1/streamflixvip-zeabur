package com.streamflixvip.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
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
 * Navegação principal focada nas áreas recorrentes do app. A pesquisa geral
 * não ocupa uma aba: ela é uma ação global acessível pela lupa do cabeçalho.
 * Isso evita confundi-la com Explorar, que é a experiência de descoberta por
 * filtros, categorias e listas de catálogo.
 */
sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    data object Home : BottomNavItem("home", "Início", Icons.Filled.Home)
    data object Explore : BottomNavItem("explore", "Explorar", Icons.Filled.Explore)
    data object Genres : BottomNavItem("genres", "Gêneros", Icons.Filled.Sell)
    data object MyList : BottomNavItem("mylist", "Favoritos", Icons.Filled.Favorite)
    data object Profile : BottomNavItem("profile", "Perfil", Icons.Filled.Person)
}

val bottomNavItems = listOf(
    BottomNavItem.Home,
    BottomNavItem.Explore,
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
                    if (selected) return@NavigationBarItem

                    // Primeiro tenta voltar a uma aba que já esteja na pilha.
                    // Assim, tocar em Início vindo de outra área volta
                    // imediatamente para Home, sem deixar a pessoa presa no
                    // gesto de voltar e sem duplicar destinos na navegação.
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
                label = { Text(item.label) },
            )
        }
    }
}
