package com.example.project_mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.project_mobile.ui.screens.*
import com.example.project_mobile.ui.theme.Project_MobileTheme
import com.example.project_mobile.ui.viewmodel.AuthViewModel
import com.example.project_mobile.ui.viewmodel.BookingViewModel

// 1. Centralized Routing (Maintainability)
sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Signup : Screen("signup")
    data object Home : Screen("home")
    data object History : Screen("history")
    data object Profile : Screen("profile")
    data object Calendar : Screen("calendar")
    data object Summary : Screen("summary")
}

data class NavigationItem(
    val label: String,
    val icon: ImageVector,
    val screen: Screen
)

val BottomNavItems = listOf(
    NavigationItem("Home", Icons.Default.Home, Screen.Home),
    NavigationItem("History", Icons.Default.History, Screen.History),
    NavigationItem("Profile", Icons.Default.Person, Screen.Profile)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 2. Material 3 Edge-to-Edge Optimization
        enableEdgeToEdge()
        setContent {
            Project_MobileTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Project_MobileApp()
                }
            }
        }
    }
}

@Composable
fun Project_MobileApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val authViewModel: AuthViewModel = viewModel()
    val bookingViewModel: BookingViewModel = viewModel()

    // 3. Optimize State Collection (Performance & Battery)
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val userProfile by authViewModel.userProfile.collectAsStateWithLifecycle()

    // 4. Handle "Auth Guard" (Security/UX)
    LaunchedEffect(authState) {
        if (authState is AuthViewModel.AuthState.Authenticated) {
            val currentRoute = navController.currentDestination?.route
            if (currentRoute == Screen.Login.route || currentRoute == Screen.Signup.route || currentRoute == null) {
                navController.navigate(Screen.Home.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        } else if (authState is AuthViewModel.AuthState.Unauthenticated) {
            val currentRoute = navController.currentDestination?.route
            if (currentRoute != Screen.Login.route && currentRoute != Screen.Signup.route) {
                navController.navigate(Screen.Login.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    // Sync bookingViewModel with current user data
    LaunchedEffect(authState, userProfile) {
        if (authState is AuthViewModel.AuthState.Authenticated) {
            bookingViewModel.setUserData((authState as AuthViewModel.AuthState.Authenticated).uid, userProfile)
        } else {
            bookingViewModel.setUserData(null, null)
        }
    }

    val showBottomBar = remember(currentDestination) {
        currentDestination?.route in listOf(
            Screen.Home.route,
            Screen.History.route,
            Screen.Profile.route,
            Screen.Calendar.route,
            Screen.Summary.route
        )
    }

    // 5. Fix Navigation Reconstruction (UI Logic)
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            if (showBottomBar) {
                BottomNavItems.forEach { item ->
                    item(
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true,
                        onClick = {
                            navController.navigate(item.screen.route) {
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
    ) {
        MainNavigationHost(navController, authViewModel, bookingViewModel)
    }
}

@Composable
fun MainNavigationHost(
    navController: androidx.navigation.NavHostController,
    authViewModel: AuthViewModel,
    bookingViewModel: BookingViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onSignInSuccess = { 
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onSignUpClick = { navController.navigate(Screen.Signup.route) },
                viewModel = authViewModel
            )
        }
        composable(Screen.Signup.route) {
            SignUpScreen(
                onSignUpSuccess = { 
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onSignInClick = { navController.popBackStack() },
                viewModel = authViewModel
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onNextClick = { navController.navigate(Screen.Calendar.route) },
                viewModel = bookingViewModel
            )
        }
        composable(Screen.Calendar.route) {
            CalendarScreen(
                onNextClick = { navController.navigate(Screen.Summary.route) },
                viewModel = bookingViewModel
            )
        }
        composable(Screen.Summary.route) {
            SummaryScreen(
                onConfirmClick = { 
                    navController.navigate(Screen.History.route) {
                        popUpTo(Screen.Home.route)
                    }
                },
                viewModel = bookingViewModel
            )
        }
        composable(Screen.History.route) {
            HistoryScreen(viewModel = bookingViewModel)
        }
        composable(Screen.Profile.route) {
            ProfileScreen(
                onLogoutClick = {
                    authViewModel.signOut()
                    // Navigation will be automatically handled by the Auth Guard LaunchedEffect
                },
                viewModel = authViewModel
            )
        }
    }
}
