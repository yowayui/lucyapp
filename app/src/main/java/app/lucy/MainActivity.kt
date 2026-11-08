package app.lucy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.lucy.ui.screens.BrowseScreen
import app.lucy.ui.screens.HomeScreen
import app.lucy.ui.screens.LibraryScreen
import app.lucy.ui.screens.ReaderScreen
import app.lucy.ui.screens.SearchScreen
import app.lucy.ui.screens.SeriesScreen
import app.lucy.ui.theme.LucyTheme
import java.net.URLDecoder
import java.net.URLEncoder

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Search : Screen("search", "Search", Icons.Default.Search)
    data object Browse : Screen("browse", "Browse", Icons.Default.Explore)
    data object Library : Screen("library", "Library", Icons.Default.Bookmarks)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as LucyApp
        val catalogDb = app.catalogDb
        val userDb = app.userDb

        setContent {
            LucyTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val bottomBarRoutes = listOf(
                    Screen.Home.route,
                    Screen.Search.route,
                    Screen.Browse.route,
                    Screen.Library.route
                )

                val showBottomBar = currentRoute in bottomBarRoutes

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                val screens = listOf(
                                    Screen.Home,
                                    Screen.Search,
                                    Screen.Browse,
                                    Screen.Library
                                )
                                screens.forEach { screen ->
                                    val isSelected = currentRoute == screen.route
                                    NavigationBarItem(
                                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                                        label = { Text(screen.title) },
                                        selected = isSelected,
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        onClick = {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                catalogDb = catalogDb,
                                onMangaClick = { slug ->
                                    navController.navigate("series/$slug")
                                }
                            )
                        }

                        composable(Screen.Search.route) {
                            SearchScreen(
                                catalogDb = catalogDb,
                                onMangaClick = { slug ->
                                    navController.navigate("series/$slug")
                                }
                            )
                        }

                        composable(Screen.Browse.route) {
                            BrowseScreen(
                                catalogDb = catalogDb,
                                onMangaClick = { slug ->
                                    navController.navigate("series/$slug")
                                }
                            )
                        }

                        composable(Screen.Library.route) {
                            LibraryScreen(
                                catalogDb = catalogDb,
                                userDb = userDb,
                                onMangaClick = { slug ->
                                    navController.navigate("series/$slug")
                                }
                            )
                        }

                        composable(
                            route = "series/{slug}",
                            arguments = listOf(navArgument("slug") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val slug = backStackEntry.arguments?.getString("slug").orEmpty()
                            SeriesScreen(
                                slug = slug,
                                catalogDb = catalogDb,
                                userDb = userDb,
                                onBackClick = { navController.popBackStack() },
                                onChapterClick = { ch ->
                                    val encSeries = URLEncoder.encode(slug, "UTF-8")
                                    val encChSlug = URLEncoder.encode(ch.slug, "UTF-8")
                                    val encChNum = URLEncoder.encode(ch.number, "UTF-8")
                                    navController.navigate("reader/$encSeries/$encChSlug/$encChNum")
                                }
                            )
                        }

                        composable(
                            route = "reader/{seriesSlug}/{chapterSlug}/{chapterNum}",
                            arguments = listOf(
                                navArgument("seriesSlug") { type = NavType.StringType },
                                navArgument("chapterSlug") { type = NavType.StringType },
                                navArgument("chapterNum") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val sSlug = URLDecoder.decode(backStackEntry.arguments?.getString("seriesSlug").orEmpty(), "UTF-8")
                            val cSlug = URLDecoder.decode(backStackEntry.arguments?.getString("chapterSlug").orEmpty(), "UTF-8")
                            val cNum = URLDecoder.decode(backStackEntry.arguments?.getString("chapterNum").orEmpty(), "UTF-8")

                            ReaderScreen(
                                seriesSlug = sSlug,
                                chapterSlug = cSlug,
                                chapterNum = cNum,
                                catalogDb = catalogDb,
                                userDb = userDb,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
