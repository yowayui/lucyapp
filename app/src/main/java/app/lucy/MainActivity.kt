package app.lucy

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.core.view.WindowCompat
import app.lucy.ui.components.AppUpdate
import app.lucy.ui.components.AppUpdateChecker
import app.lucy.ui.components.LucyNavBar
import app.lucy.ui.components.NavItem
import app.lucy.ui.components.UpdateCheckResult
import app.lucy.ui.components.UpdateDialog
import app.lucy.ui.screens.BrowseScreen
import app.lucy.ui.screens.HomeScreen
import app.lucy.ui.screens.LibraryScreen
import app.lucy.ui.screens.ReaderScreen
import app.lucy.ui.screens.SearchScreen
import app.lucy.ui.screens.SeriesScreen
import app.lucy.ui.screens.SettingsScreen
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
            var themeMode by remember { mutableStateOf(userDb.getSetting("theme", "system")) }
            val isDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            LucyTheme(darkTheme = isDark) {
                val context = LocalContext.current
                val view = LocalView.current

                SideEffect {
                    val window = (context as? Activity)?.window ?: return@SideEffect
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !isDark
                        isAppearanceLightNavigationBars = !isDark
                    }
                }

                var availableUpdate by remember { mutableStateOf<AppUpdate?>(null) }

                LaunchedEffect(Unit) {
                    val result = AppUpdateChecker.checkLatest(BuildConfig.VERSION_NAME)
                    if (result is UpdateCheckResult.Available) {
                        availableUpdate = result.update
                    }
                }

                availableUpdate?.let { update ->
                    UpdateDialog(
                        update = update,
                        context = context,
                        onDismiss = { availableUpdate = null }
                    )
                }

                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val navItems = remember {
                    listOf(
                        NavItem(Screen.Home.route, Screen.Home.title, Screen.Home.icon),
                        NavItem(Screen.Search.route, Screen.Search.title, Screen.Search.icon),
                        NavItem(Screen.Browse.route, Screen.Browse.title, Screen.Browse.icon),
                        NavItem(Screen.Library.route, Screen.Library.title, Screen.Library.icon)
                    )
                }

                val showBottomBar = currentRoute in navItems.map { it.route }
                val isReaderRoute = currentRoute?.startsWith("reader/") == true
                val motionScheme = MaterialTheme.motionScheme

                fun openReader(seriesSlug: String, chapterSlug: String, chapterNum: String) {
                    val encSeries = URLEncoder.encode(seriesSlug, "UTF-8")
                    val encChSlug = URLEncoder.encode(chapterSlug, "UTF-8")
                    val encChNum = URLEncoder.encode(chapterNum, "UTF-8")
                    navController.navigate("reader/$encSeries/$encChSlug/$encChNum")
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        if (showBottomBar) {
                            LucyNavBar(
                                items = navItems,
                                currentRoute = currentRoute,
                                onSelect = { item ->
                                    navController.navigate(item.route) {
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
                ) { innerPadding ->
                    Box(
                        modifier = if (isReaderRoute) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier.fillMaxSize().padding(innerPadding)
                        }
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Home.route,
                            modifier = Modifier.fillMaxSize(),
                            enterTransition = {
                                fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
                                    scaleIn(
                                        initialScale = 0.96f,
                                        animationSpec = motionScheme.defaultSpatialSpec()
                                    )
                            },
                            exitTransition = {
                                fadeOut(animationSpec = motionScheme.fastEffectsSpec())
                            },
                            popEnterTransition = {
                                fadeIn(animationSpec = motionScheme.fastEffectsSpec())
                            },
                            popExitTransition = {
                                fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                                    scaleOut(
                                        targetScale = 0.98f,
                                        animationSpec = motionScheme.fastSpatialSpec()
                                    )
                            }
                        ) {
                            composable(Screen.Home.route) {
                                HomeScreen(
                                    catalogDb = catalogDb,
                                    userDb = userDb,
                                    onMangaClick = { slug ->
                                        navController.navigate("series/$slug")
                                    },
                                    onHistoryClick = { sSlug, cSlug, cNum ->
                                        openReader(sSlug, cSlug, cNum)
                                    },
                                    onSettingsClick = {
                                        navController.navigate("settings")
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
                                    },
                                    onHistoryClick = { sSlug, cSlug, cNum ->
                                        openReader(sSlug, cSlug, cNum)
                                    }
                                )
                            }

                            composable("settings") {
                                SettingsScreen(
                                    userDb = userDb,
                                    currentTheme = themeMode,
                                    onThemeChange = { next -> themeMode = next },
                                    onBackClick = { navController.popBackStack() }
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
                                        openReader(slug, ch.slug, ch.number)
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

                        // Soft scrim so content fades under the floating nav bar
                        // instead of being cut off with a hard edge.
                        if (showBottomBar) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.background
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}
