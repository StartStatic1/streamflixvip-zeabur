package com.streamflixvip.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
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
 * Barra de navegação inferior fixa — Início, Buscar, Social, Minha Lista,
 * Perfil. Mantém as 4 abas essenciais que já existiam e acrescenta Social
 * (posts/comentários da comunidade, inspirado na aba "O que estão
 * dizendo" do CineVerse), sem inflar a barra com abas que não teriam
 * conteúdo real por trás (tipo Shots/Explorar).
 */
sealed class BottomNavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Home : BottomNavItem("home", "Início", Icons.Filled.Home)
    data object Search : BottomNavItem("search", "Buscar", Icons.Filled.Search)
    data object Social : BottomNavItem("social", "Social", Icons.Filled.Groups)
    data object MyList : BottomNavItem("mylist", "Minha Lista", Icons.Filled.Bookmark)
    data object Profile : BottomNavItem("profile", "Perfil", Icons.Filled.Person)
}

val bottomNavItems = listOf(
    BottomNavItem.Home,
    BottomNavItem.Search,
    BottomNavItem.Social,
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
