package com.robocore.camerat.ui

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.robocore.camerat.MainViewModel

object MainNavDestination {
    const val CAMERA_VIEW = "camera_view"
    const val HOME = "home"
}

@Composable
fun MainNavGraph(
    navController: NavHostController,
    startDestination: String = MainNavDestination.HOME
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(MainNavDestination.HOME) { Home(navController = navController) }
        composable(MainNavDestination.CAMERA_VIEW) { CameraView(viewModel = hiltViewModel<MainViewModel>(), navController = navController) }
    }
}