package com.ferhat.commutetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ferhat.commutetracker.ui.TrackerViewModel
import com.ferhat.commutetracker.ui.screens.HomeScreen
import com.ferhat.commutetracker.ui.screens.PlacesScreen
import com.ferhat.commutetracker.ui.screens.RoutesScreen
import com.ferhat.commutetracker.ui.screens.SettingsScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startDestination = intent?.getStringExtra(EXTRA_DESTINATION) ?: Dest.HOME
        setContent {
            MaterialTheme {
                CommuteApp(startDestination = startDestination)
            }
        }
    }

    companion object {
        const val EXTRA_DESTINATION = "destination"
    }
}

private object Dest {
    const val HOME = "home"
    const val ROUTES = "routes"
    const val PLACES = "places"
    const val SETTINGS = "settings"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun CommuteApp(startDestination: String, vm: TrackerViewModel = viewModel()) {
    val navController = rememberNavController()
    val tabs = listOf(
        Tab(Dest.HOME, "Home", Icons.Default.Home),
        Tab(Dest.ROUTES, "Routes", Icons.Default.Route),
        Tab(Dest.PLACES, "Places", Icons.Default.Place),
        Tab(Dest.SETTINGS, "Settings", Icons.Default.Settings),
    )

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination.takeIf { it in setOf(Dest.HOME, Dest.ROUTES, Dest.PLACES, Dest.SETTINGS) } ?: Dest.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Dest.HOME) { HomeScreen(vm) }
            composable(Dest.ROUTES) { RoutesScreen(vm) }
            composable(Dest.PLACES) { PlacesScreen(vm) }
            composable(Dest.SETTINGS) { SettingsScreen(vm) }
        }
    }
}
