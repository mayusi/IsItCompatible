package io.github.mayusi.isitcompatible.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.mayusi.isitcompatible.R
import io.github.mayusi.isitcompatible.ui.appupdate.AppUpdateViewModel
import io.github.mayusi.isitcompatible.ui.autodetect.AutoDetectScreen
import io.github.mayusi.isitcompatible.ui.common.AppUpdateDialog
import io.github.mayusi.isitcompatible.ui.detail.GameDetailScreen
import io.github.mayusi.isitcompatible.ui.journal.JournalScreen
import io.github.mayusi.isitcompatible.ui.library.LibraryScreen
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
    LIBRARY("library", R.string.tab_library, Icons.Outlined.FolderOpen),
    JOURNAL("journal", R.string.tab_journal, Icons.Outlined.EditNote),
    AUTODETECT("autodetect", R.string.tab_autodetect, Icons.Outlined.Smartphone),
    UPDATES("updates", R.string.tab_updates, Icons.Outlined.CloudDownload),
    SETTINGS("settings", R.string.tab_settings, Icons.Outlined.Settings);
}

private const val DETAIL_ROUTE = "game/{gameId}"
private fun detailRoute(gameId: String) = "game/$gameId"

@Composable
fun MainScaffold(
    updateVm: AppUpdateViewModel = hiltViewModel(),
) {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val currentRoute = current?.destination?.route

    val isTabRoute = currentRoute in Tab.entries.map { it.route }

    val updateState by updateVm.state.collectAsState()

    // One-time per session: show the update dialog on first launch when
    // an update is already pending.  "Later" dismisses the dialog but does
    // NOT clear the pref — the Settings banner continues to show.
    // rememberSaveable so it survives recomposition but resets on process restart.
    var launchDialogShown by rememberSaveable { mutableStateOf(false) }
    val showLaunchDialog = !launchDialogShown && updateState.pendingUpdate != null

    if (showLaunchDialog) {
        AppUpdateDialog(
            update = updateState.pendingUpdate!!,
            installState = updateState.installState,
            onInstall = updateVm::installUpdate,
            onDismiss = {
                // Dismiss the launch dialog only — keep the pref so the
                // Settings banner stays visible.
                launchDialogShown = true
            },
        )
    }
    // Once we've shown it once, mark it as shown.
    if (updateState.pendingUpdate != null && !launchDialogShown) {
        // The dialog is now open; mark shown so it doesn't re-open on tab switch.
        launchDialogShown = true
    }

    Scaffold(
        bottomBar = {
            if (isTabRoute) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = androidx.compose.ui.unit.Dp.Hairline,
                ) {
                    Tab.entries.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = {
                                Text(
                                    stringResource(tab.labelRes),
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            ),
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
            composable(Tab.LIBRARY.route) {
                LibraryScreen(
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
            composable(Tab.AUTODETECT.route) {
                AutoDetectScreen(
                    contentPadding = padding,
                    onOpenGame = { gameId -> nav.navigate(detailRoute(gameId)) },
                )
            }
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
