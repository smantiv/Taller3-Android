package com.example.taller3

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.taller3.navigation.Screen
import com.example.taller3.ui.screens.HomeScreen
import com.example.taller3.ui.screens.LoginScreen
import com.example.taller3.ui.screens.ProfileScreen
import com.example.taller3.ui.screens.RegisterScreen
import com.example.taller3.ui.theme.Taller3Theme
import com.example.taller3.viewmodel.AuthViewModel
import com.example.taller3.viewmodel.HomeViewModel
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Taller3Theme {
                AppNavigation(authViewModel, homeViewModel)
            }
        }
    }
}

@Composable
fun AppNavigation(authViewModel: AuthViewModel, homeViewModel: HomeViewModel) {
    val navController = rememberNavController()
    val startDestination = if (authViewModel.getCurrentUser() != null) Screen.Home.route else Screen.Login.route

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            if (auth.currentUser == null) {
                navController.navigate(Screen.Login.route) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                }
            }
        }
        FirebaseAuth.getInstance().addAuthStateListener(listener)
        onDispose {
            FirebaseAuth.getInstance().removeAuthStateListener(listener)
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Login.route) {
            Log.d("Nav", "Navigating to Login")
            LoginScreen(navController, authViewModel)
        }
        composable(Screen.Register.route) {
            Log.d("Nav", "Navigating to Register")
            RegisterScreen(navController, authViewModel)
        }
        composable(Screen.Home.route) {
            Log.d("Nav", "Navigating to Home")
            HomeScreen(navController, authViewModel, homeViewModel)
        }
        composable(Screen.Profile.route) {
            Log.d("Nav", "Navigating to Profile")
            ProfileScreen(onBack = { navController.popBackStack() })
        }
    }
}