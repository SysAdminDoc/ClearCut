package com.novacut.editor.engine

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.novacut.editor.MainActivity
import com.novacut.editor.NovaCutApp
import com.novacut.editor.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import java.io.File
import javax.inject.Inject

private const val TAG = "ExportService"

@AndroidEntryPoint
class ExportService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_CANCEL = "com.novacut.editor.CANCEL_EXPORT"
    }

    @Inject lateinit var videoEngine: VideoEngine

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastNotifiedProgress = -1
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            Log.d(TAG, "Cancel requested")
            videoEngine.cancelExport()
            return START_NOT_STICKY
        }

        val notification = buildNotification(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val currentState = videoEngine.exportState.value
        if (currentState != ExportState.COMPLETE && currentState != ExportState.ERROR && currentState != ExportState.CANCELLED) {
            startObservingExport()
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startObservingExport() {
        observeJob?.cancel()
        observeJob = serviceScope.launch {
            combine(
                videoEngine.exportProgress,
                videoEngine.exportState
            ) { progress, state -> progress to state }
                .collect { (progress, state) ->
                    when (state) {
                        ExportState.EXPORTING -> {
                            updateProgress((progress * 100).toInt().coerceIn(0, 99))
                        }
                        ExportState.COMPLETE -> {
                            notifyComplete()
                        }
                        ExportState.ERROR -> {
                            notifyError(videoEngine.exportErrorMessage.value ?: getString(R.string.notif_export_failed_default))
                        }
                        ExportState.CANCELLED -> {
                            notifyCancel()
                        }
                        ExportState.IDLE -> { /* no-op */ }
                    }
                }
        }
    }

    private fun updateProgress(progress: Int) {
        if (progress - lastNotifiedProgress < 2 && progress < 100) return
        lastNotifiedProgress = progress
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(progress))
    }

    private fun notifyComplete() {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.cancel(NOTIFICATION_ID) // Cancel the progress notification first

        // Build tap-to-open intent for the exported file
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NovaCutApp.CHANNEL_EXPORT)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle(getString(R.string.notif_export_complete_title))
            .setContentText(getString(R.string.notif_export_complete_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .build()
        nm?.notify(NOTIFICATION_ID + 1, notification)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notifyError(message: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.cancel(NOTIFICATION_ID)

        val notification = NotificationCompat.Builder(this, NovaCutApp.CHANNEL_EXPORT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(getString(R.string.notif_export_failed_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm?.notify(NOTIFICATION_ID + 1, notification)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notifyCancel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        observeJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(progress: Int): Notification {
        val cancelIntent = Intent(this, ExportService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPi = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NovaCutApp.CHANNEL_EXPORT)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle(getString(R.string.notif_export_title))
            .setContentText(getString(R.string.notif_export_progress, progress))
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.notif_export_cancel), cancelPi)
            .build()
    }
}
