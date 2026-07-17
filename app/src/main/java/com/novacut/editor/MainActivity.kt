package com.novacut.editor

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacut.editor.engine.AppSettings
import com.novacut.editor.engine.IncomingDocumentIntentParser
import com.novacut.editor.engine.IncomingDocumentItem
import com.novacut.editor.engine.IncomingDocumentMetadata
import com.novacut.editor.engine.IncomingMediaIntentParser
import com.novacut.editor.engine.IncomingMediaItem
import com.novacut.editor.engine.ProjectShortcutPlanner
import com.novacut.editor.engine.SettingsRepository
import com.novacut.editor.engine.resolveMediaDisplayName
import com.novacut.editor.ui.editor.EditorScreen
import com.novacut.editor.ui.editor.LocalTabletopPosture
import com.novacut.editor.ui.projects.ProjectListScreen
import com.novacut.editor.ui.settings.SettingsScreen
import com.novacut.editor.ui.theme.ClearCutTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private var pendingIncomingMedia by mutableStateOf<List<IncomingMediaItem>>(emptyList())
    private var pendingIncomingDocuments by mutableStateOf<List<IncomingDocumentItem>>(emptyList())
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
        // Only parse the launch intent on a genuinely fresh start. Without
        // this, sharing media into ClearCut, then returning via Recents after
        // the process was killed, re-delivers the original SEND intent and
        // re-imports it into a DUPLICATE project (and duplicate managed-media
        // copies). onNewIntent still handles live re-delivery.
        if (shouldProcessLaunchIntent(savedInstanceState, intent)) {
            handleIncomingIntent(intent)
        }

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            ClearCutTheme(appearanceMode = settings.appearanceMode) {
                val navController = rememberNavController()
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route
                val rootModifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .semantics { testTagsAsResourceId = true }
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

                LaunchedEffect(pendingIncomingMedia, currentRoute) {
                    if (pendingIncomingMedia.isNotEmpty() && currentRoute != null && currentRoute != "projects") {
                        navController.navigate("projects") {
                            launchSingleTop = true
                            popUpTo("projects") { inclusive = false }
                        }
                    }
                }

                LaunchedEffect(pendingIncomingDocuments, currentRoute) {
                    if (pendingIncomingDocuments.isNotEmpty() && currentRoute != null && currentRoute != "projects") {
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
                                pendingImportItems = pendingIncomingMedia,
                                onPendingImportHandled = { pendingIncomingMedia = emptyList() },
                                pendingDocumentItems = pendingIncomingDocuments,
                                onPendingDocumentImportHandled = { pendingIncomingDocuments = emptyList() }
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

    private fun shouldProcessLaunchIntent(savedInstanceState: Bundle?, intent: Intent?): Boolean =
        shouldProcessLaunchIntent(savedInstanceState != null, intent?.flags ?: 0)

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> handleIncomingMediaIntent(intent)
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

    private fun handleIncomingMediaIntent(intent: Intent) {
        val parsed = IncomingMediaIntentParser.parse(intent) { uri ->
            runCatching { contentResolver.getType(uri) }.getOrNull()
        }
        if (parsed.isEmpty()) {
            handleIncomingDocumentIntent(intent)
            return
        }

        val readableItems = parsed.filter { item ->
            runCatching {
                contentResolver.openAssetFileDescriptor(item.uri, "r")?.use { descriptor ->
                    descriptor.length != 0L
                } ?: false
            }.getOrDefault(false)
        }
        if (readableItems.isNotEmpty()) {
            pendingIncomingMedia = readableItems
        }
    }

    private fun handleIncomingDocumentIntent(intent: Intent) {
        val parsed = IncomingDocumentIntentParser.parse(intent) { uri ->
            incomingDocumentMetadata(uri, intent.type)
        }
        if (parsed.isEmpty()) return

        val readableItems = parsed.filter { item ->
            runCatching {
                contentResolver.openAssetFileDescriptor(item.uri, "r")?.use { descriptor ->
                    descriptor.length != 0L
                } ?: false
            }.getOrDefault(false)
        }
        if (readableItems.isNotEmpty()) {
            pendingIncomingDocuments = readableItems
        }
    }

    private fun incomingDocumentMetadata(uri: Uri, intentMimeType: String?): IncomingDocumentMetadata {
        return IncomingDocumentMetadata(
            displayName = resolveMediaDisplayName(this, uri),
            mimeType = runCatching { contentResolver.getType(uri) }.getOrNull() ?: intentMimeType,
            sizeBytes = queryOpenableSize(uri)
        )
    }

    private fun queryOpenableSize(uri: Uri): Long? {
        return runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    companion object {
        const val ACTION_NEW_PROJECT = "com.novacut.editor.action.NEW_PROJECT"
        const val ACTION_OPEN_RECENT = "com.novacut.editor.action.OPEN_RECENT"
    }
}

private data class PendingEditorOpen(
    val projectId: String,
    val expectRecovery: Boolean,
)

/**
 * The launch intent should be parsed only on a genuinely fresh start:
 * not after an Activity recreation (state was already handled) and not when
 * relaunched from Recents (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`), where the
 * original SEND intent is re-delivered and would re-import a duplicate.
 */
internal fun shouldProcessLaunchIntent(isRecreation: Boolean, intentFlags: Int): Boolean {
    if (isRecreation) return false
    return (intentFlags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0
}
