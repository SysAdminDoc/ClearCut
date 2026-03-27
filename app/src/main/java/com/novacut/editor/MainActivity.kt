package com.novacut.editor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.novacut.editor.ui.editor.EditorScreen
import com.novacut.editor.ui.projects.ProjectListScreen
import com.novacut.editor.ui.settings.SettingsScreen
import com.novacut.editor.ui.theme.NovaCutTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingVideoUri: Uri? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filter { !it.value }.keys
        if (denied.isNotEmpty()) {
            val permanentlyDenied = denied.any { perm ->
                !shouldShowRequestPermissionRationale(perm)
            }
            if (permanentlyDenied) {
                android.widget.Toast.makeText(
                    this,
                    "Permissions required. Please enable in Settings > Apps > NovaCut > Permissions.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } else {
                android.widget.Toast.makeText(
                    this,
                    "Some permissions were denied. Media access may be limited.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissions()
        handleIncomingIntent(intent)

        setContent {
            NovaCutTheme {
                val navController = rememberNavController()
                val rootModifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()

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
                            pendingImportUri = pendingVideoUri?.also { pendingVideoUri = null }
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
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            pendingVideoUri = intent.data
        }
    }

    private fun requestPermissions() {
        val needed = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkPerm(Manifest.permission.READ_MEDIA_VIDEO)) needed.add(Manifest.permission.READ_MEDIA_VIDEO)
            if (checkPerm(Manifest.permission.READ_MEDIA_AUDIO)) needed.add(Manifest.permission.READ_MEDIA_AUDIO)
            if (checkPerm(Manifest.permission.READ_MEDIA_IMAGES)) needed.add(Manifest.permission.READ_MEDIA_IMAGES)
            if (checkPerm(Manifest.permission.POST_NOTIFICATIONS)) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            if (checkPerm(Manifest.permission.READ_EXTERNAL_STORAGE)) needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (checkPerm(Manifest.permission.RECORD_AUDIO)) needed.add(Manifest.permission.RECORD_AUDIO)
        if (checkPerm(Manifest.permission.CAMERA)) needed.add(Manifest.permission.CAMERA)

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun checkPerm(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
    }
}
