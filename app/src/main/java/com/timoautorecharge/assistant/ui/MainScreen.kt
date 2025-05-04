package com.timoautorecharge.assistant.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.timoautorecharge.assistant.R
import com.timoautorecharge.assistant.ui.connection.ConnectionStatusScreen
import com.timoautorecharge.assistant.ui.dashboard.DashboardScreen
import com.timoautorecharge.assistant.ui.settings.SettingsScreen
import com.timoautorecharge.assistant.ui.transactions.TransactionsScreen
import com.timoautorecharge.assistant.ui.uielements.UiElementsScreen
import org.koin.androidx.compose.getViewModel

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                    label = { Text(stringResource(R.string.dashboard)) },
                    selected = currentDestination?.hierarchy?.any { it.route == "dashboard" } == true,
                    onClick = {
                        navController.navigate("dashboard") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.transactions)) },
                    selected = currentDestination?.hierarchy?.any { it.route == "transactions" } == true,
                    onClick = {
                        navController.navigate("transactions") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.settings)) },
                    selected = currentDestination?.hierarchy?.any { it.route == "settings" } == true,
                    onClick = {
                        navController.navigate("settings") {
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
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("dashboard") {
                DashboardScreen(
                    navigateToUiElements = { navController.navigate("ui_elements") },
                    navigateToConnectionStatus = { navController.navigate("connection_status") },
                    viewModel = getViewModel()
                )
            }
            composable("transactions") {
                TransactionsScreen(viewModel = getViewModel())
            }
            composable("settings") {
                SettingsScreen(viewModel = getViewModel())
            }
            composable("ui_elements") {
                UiElementsScreen(
                    navigateBack = { navController.navigateUp() },
                    viewModel = getViewModel()
                )
            }
            composable("connection_status") {
                ConnectionStatusScreen(
                    navigateBack = { navController.navigateUp() },
                    viewModel = getViewModel()
                )
            }
        }
    }
}
