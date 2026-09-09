package com.ferhat.commutetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ferhat.commutetracker.ui.CommuteViewModel
import com.ferhat.commutetracker.ui.screens.HomeScreen
import com.ferhat.commutetracker.ui.screens.LocationsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                CommuteApp()
            }
        }
    }
}

@Composable
private fun CommuteApp(vm: CommuteViewModel = viewModel()) {
    val navController = rememberNavController()
    Scaffold { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") {
                HomeScreen(vm = vm, onManageLocations = { navController.navigate("locations") })
            }
            composable("locations") {
                LocationsScreen(vm = vm, onBack = { navController.popBackStack() })
            }
        }
    }
}
