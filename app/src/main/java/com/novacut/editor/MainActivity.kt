package com.novacut.editor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.novacut.editor.ui.editor.EditorScreen
import com.novacut.editor.ui.projects.ProjectListScreen
import com.novacut.editor.ui.settings.SettingsScreen
import com.novacut.editor.ui.theme.NovaCutTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingVideoUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            NovaCutTheme {
                val navController = rememberNavController()
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route
                val rootModifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()

                LaunchedEffect(pendingVideoUri, currentRoute) {
                    if (pendingVideoUri != null && currentRoute != null && currentRoute != "projects") {
                        navController.navigate("projects") {
                            launchSingleTop = true
                            popUpTo("projects") { inclusive = false }
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = "projects",
                    modifier = rootModifier
                ) {
                    composable("projects") {
                        ProjectListScreen(
                            onProjectSelected = { projectId ->
                                navController.navigate("editor/$projectId")
                            },
                            onSettings = { navController.navigate("settings") },
                            pendingImportUri = pendingVideoUri,
                            onPendingImportHandled = { pendingVideoUri = null }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onNavigateToAiModels = { navController.popBackStack() }
                        )
                    }
                    composable("editor/{projectId}") {
                        EditorScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            val uri = intent.data ?: return
            // Only accept content:// URIs — file:// is a security risk from other apps
            if (uri.scheme != "content") return
            // Verify the URI is actually readable before accepting it
            try {
                contentResolver.getType(uri) ?: return
                pendingVideoUri = uri
            } catch (_: Exception) {
                // Ignore unreadable or malicious URIs
            }
        }
    }
}
