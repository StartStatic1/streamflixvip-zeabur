package com.streamflixvip.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.streamflixvip.app.ui.auth.AuthScreen
import com.streamflixvip.app.ui.auth.AuthViewModel
import com.streamflixvip.app.ui.detail.DetailScreen
import com.streamflixvip.app.ui.explore.ExploreScreen
import com.streamflixvip.app.ui.genre.GenreDetailScreen
import com.streamflixvip.app.ui.home.HomeScreen
import com.streamflixvip.app.ui.livetv.LivePlayerScreen
import com.streamflixvip.app.ui.livetv.LiveTvScreen
import com.streamflixvip.app.ui.mylist.MyListScreen
import com.streamflixvip.app.ui.nav.BottomNavBar
import com.streamflixvip.app.ui.player.PlayerScreen
import com.streamflixvip.app.ui.profile.ProfileScreen
import com.streamflixvip.app.ui.search.SearchScreen
import com.streamflixvip.app.ui.theme.StreamFlixVIPTheme
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StreamFlixVIPTheme {
                AppRoot()
            }
        }
    }
}
