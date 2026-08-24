package com.safevault.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.safevault.app.ui.screens.editor.EditorScreen
import com.safevault.app.ui.screens.settings.SettingsScreen
import com.safevault.app.ui.screens.vault.VaultScreen

/**
 * Navigation graph for the unlocked portion of the app. The lock gate lives
 * above this graph (in MainActivity) so no unlocked destination can ever be
 * shown while the vault is locked.
 */
@Composable
fun SafeVaultNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.VAULT,
    ) {
        composable(Routes.VAULT) {
            VaultScreen(
                onOpenNote = { id -> navController.navigate(Routes.editor(id)) },
                onCreateNote = { navController.navigate(Routes.editor()) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            route = Routes.EDITOR_PATTERN,
            arguments = listOf(
                navArgument(Routes.EDITOR_ARG_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) {
            EditorScreen(onDone = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
