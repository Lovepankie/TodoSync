package com.todoapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.todoapp.domain.model.TodoType
import com.todoapp.ui.screens.AddEditItemScreen
import com.todoapp.ui.screens.MainScreen

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object AddItem : Screen("add/{type}") {
        fun createRoute(type: TodoType) = "add/${type.name}"
    }
    object EditItem : Screen("edit/{itemId}") {
        fun createRoute(id: Long) = "edit/$id"
    }
}

@Composable
fun TodoNavGraph(startItemId: Long = -1L) {
    val navController = rememberNavController()

    // If launched from a notification tap, navigate directly to the item
    LaunchedEffect(startItemId) {
        if (startItemId > 0L) {
            navController.navigate(Screen.EditItem.createRoute(startItemId))
        }
    }

    NavHost(navController = navController, startDestination = Screen.Main.route) {

        composable(Screen.Main.route) {
            MainScreen(
                onAddItem = { type ->
                    navController.navigate(Screen.AddItem.createRoute(type))
                },
                onEditItem = { id ->
                    navController.navigate(Screen.EditItem.createRoute(id))
                }
            )
        }

        composable(
            route = Screen.AddItem.route,
            arguments = listOf(navArgument("type") { type = NavType.StringType })
        ) { backStackEntry ->
            val typeStr = backStackEntry.arguments?.getString("type") ?: TodoType.TASK.name
            AddEditItemScreen(
                initialType = TodoType.valueOf(typeStr),
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.EditItem.route,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType })
        ) {
            AddEditItemScreen(
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
