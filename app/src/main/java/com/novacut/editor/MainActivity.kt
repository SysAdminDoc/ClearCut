package com.novacut.editor

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.novacut.editor.engine.ProjectShortcutPlanner
import com.novacut.editor.ui.editor.EditorScreen
import com.novacut.editor.ui.editor.LocalTabletopPosture
import com.novacut.editor.ui.projects.ProjectListScreen
import com.novacut.editor.ui.settings.SettingsScreen
import com.novacut.editor.ui.theme.NovaCutTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingVideoUri by mutableStateOf<Uri?>(null)
    private var pendingEditorOpen by mutableStateOf<PendingEditorOpen?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // R8.3 — remove the translucent scrim Android draws under the
        // three-button navigation bar when the app is edge-to-edge. We
        // already render full-bleed under that bar, so the scrim adds
        // visual noise without preventing legibility.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        handleIncomingIntent(intent)

        setContent {
            NovaCutTheme {
                val navController = rememberNavController()
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route
                val rootModifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                var isTabletopPosture by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    WindowInfoTracker.getOrCreate(this@MainActivity)
                        .windowLayoutInfo(this@MainActivity)
                        .collect { layoutInfo ->
                            isTabletopPosture = layoutInfo.displayFeatures
                                .filterIsInstance<FoldingFeature>()
                                .any { feature ->
                                    feature.state == FoldingFeature.State.HALF_OPENED &&
                                        feature.orientation == FoldingFeature.Orientation.HORIZONTAL
                                }
                        }
                }

                LaunchedEffect(pendingVideoUri, currentRoute) {
                    if (pendingVideoUri != null && currentRoute != null && currentRoute != "projects") {
                        navController.navigate("projects") {
                            launchSingleTop = true
                            popUpTo("projects") { inclusive = false }
                        }
                    }
                }

                LaunchedEffect(pendingEditorOpen, currentRoute) {
                    val pending = pendingEditorOpen ?: return@LaunchedEffect
                    if (currentRoute == null) return@LaunchedEffect
                    navController.navigate(
                        "editor/${Uri.encode(pending.projectId)}?expectRecovery=${pending.expectRecovery}"
                    ) {
                        launchSingleTop = true
                    }
                    pendingEditorOpen = null
                }

                CompositionLocalProvider(LocalTabletopPosture provides isTabletopPosture) {
                    NavHost(
                        navController = navController,
                        startDestination = "projects",
                        modifier = rootModifier
                    ) {
                        composable("projects") {
                            ProjectListScreen(
                                onProjectSelected = { projectId ->
                                    navController.navigate("editor/${Uri.encode(projectId)}?expectRecovery=false")
                                },
                                onSettings = { navController.navigate("settings") },
                                pendingImportUri = pendingVideoUri,
                                onPendingImportHandled = { pendingVideoUri = null }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "editor/{projectId}?expectRecovery={expectRecovery}",
                            arguments = listOf(
                                navArgument("expectRecovery") {
                                    type = NavType.BoolType
                                    defaultValue = false
                                }
                            )
                        ) {
                            EditorScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
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
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> handleViewIntent(intent)
            ACTION_NEW_PROJECT, ACTION_OPEN_RECENT -> {
                // Both App Shortcuts land on the Projects gallery. The gallery
                // surfaces "New Project" + recent list; no extra plumbing
                // required today. Future enhancement: ACTION_NEW_PROJECT could
                // pre-open the Template sheet — left as a follow-up so the
                // shortcut shape is stable for users who pin it.
            }
            ProjectShortcutPlanner.ACTION_RESUME_RECOVERED -> {
                pendingEditorOpen = pendingShortcutOpen(intent, expectRecovery = true)
            }
            ProjectShortcutPlanner.ACTION_OPEN_LAST_PROJECT -> {
                pendingEditorOpen = pendingShortcutOpen(intent, expectRecovery = false)
            }
        }
    }

    private fun pendingShortcutOpen(intent: Intent, expectRecovery: Boolean): PendingEditorOpen? {
        val projectId = intent.getStringExtra(ProjectShortcutPlanner.EXTRA_PROJECT_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return PendingEditorOpen(projectId = projectId, expectRecovery = expectRecovery)
    }

    private fun handleViewIntent(intent: Intent) {
        val uri = intent.data ?: return
        // Only accept content:// URIs — file:// is a security risk from other apps
        if (uri.scheme != "content") return
        try {
            val mimeType = contentResolver.getType(uri)?.lowercase() ?: return
            if (!ACCEPTED_VIEW_MIME_PREFIXES.any { mimeType.startsWith(it) }) return
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                if (descriptor.length == 0L) return
            } ?: return
            // For now every accepted ACTION_VIEW URI lands on the projects
            // gallery via `pendingVideoUri` — the field name is historical
            // (video was the only accepted type until image/audio share
            // intents were added). The gallery's pending-import handler is
            // mime-aware and will route the URI to the correct destination
            // (project import for video, "add to current project" for
            // image/audio) once that follow-up ships.
            pendingVideoUri = uri
        } catch (_: Exception) {
            // Ignore unreadable or malicious URIs
        }
    }

    companion object {
        const val ACTION_NEW_PROJECT = "com.novacut.editor.action.NEW_PROJECT"
        const val ACTION_OPEN_RECENT = "com.novacut.editor.action.OPEN_RECENT"

        private val ACCEPTED_VIEW_MIME_PREFIXES = listOf("video/", "image/", "audio/")
    }
}

private data class PendingEditorOpen(
    val projectId: String,
    val expectRecovery: Boolean,
)
