package io.github.mayusi.isitcompatible.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.mayusi.isitcompatible.R
import io.github.mayusi.isitcompatible.ui.autodetect.AutoDetectScreen
import io.github.mayusi.isitcompatible.ui.detail.GameDetailScreen
import io.github.mayusi.isitcompatible.ui.journal.JournalScreen
import io.github.mayusi.isitcompatible.ui.search.SearchScreen
import io.github.mayusi.isitcompatible.ui.settings.SettingsScreen
import io.github.mayusi.isitcompatible.ui.updates.UpdatesScreen

/**
 * Bottom-nav tabs. **Browse (search) is the default.** That's the primary use
 * case — look up any game and see what runs it on your device.
 *
 * v0.5: "My Games" was retired in favour of "Journal". Folder scanning still
 * exists but moved into Settings — most users never used it as a top-level
 * tab, and the Journal is the better home for "what you've actually tried."
 */
private enum class Tab(val route: String, val labelRes: Int, val icon: ImageVector) {
    BROWSE("browse", R.string.tab_browse, Icons.Outlined.Search),
    JOURNAL("journal", R.string.tab_journal, Icons.Outlined.EditNote),
    AUTODETECT("autodetect", R.string.tab_autodetect, Icons.Outlined.Smartphone),
    UPDATES("updates", R.string.tab_updates, Icons.Outlined.CloudDownload),
    SETTINGS("settings", R.string.tab_settings, Icons.Outlined.Settings);
}

private const val DETAIL_ROUTE = "game/{gameId}"
private fun detailRoute(gameId: String) = "game/$gameId"

@Composable
fun MainScaffold() {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val currentRoute = current?.destination?.route

    val isTabRoute = currentRoute in Tab.entries.map { it.route }

    Scaffold(
        bottomBar = {
            if (isTabRoute) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.BROWSE.route,
            modifier = Modifier,
        ) {
            composable(Tab.BROWSE.route) {
                SearchScreen(
                    contentPadding = padding,
                    onOpenGame = { gameId -> nav.navigate(detailRoute(gameId)) },
                )
            }
            composable(Tab.JOURNAL.route) {
                JournalScreen(
                    contentPadding = padding,
                    onOpenGame = { gameId -> nav.navigate(detailRoute(gameId)) },
                )
            }
            composable(Tab.AUTODETECT.route) { AutoDetectScreen(contentPadding = padding) }
            composable(Tab.UPDATES.route) { UpdatesScreen(contentPadding = padding) }
            composable(Tab.SETTINGS.route) { SettingsScreen(contentPadding = padding) }
            composable(
                route = DETAIL_ROUTE,
                arguments = listOf(navArgument("gameId") { type = NavType.StringType }),
            ) {
                GameDetailScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
