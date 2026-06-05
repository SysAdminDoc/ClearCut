package com.novacut.editor.engine

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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
private const val THERMAL_FORECAST_SECONDS = 30
private const val THERMAL_POLL_INTERVAL_MS = 1_000L
private const val THERMAL_NOTIFICATION_ID = 1003

@AndroidEntryPoint
class ExportService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_CANCEL = "com.novacut.editor.CANCEL_EXPORT"
        const val EXTRA_OUTPUT_PATH = "com.novacut.editor.extra.OUTPUT_PATH"
    }

    @Inject lateinit var videoEngine: VideoEngine

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastNotifiedProgress = -1
    private var observeJob: Job? = null
    private var latestOutputPath: String? = null
    private var thermalJob: Job? = null
    private var thermalStatusListener: Any? = null
    private var latestThermalStatus = ThermalHeadroomPolicy.ThermalStatus.NONE
    private var lastThermalAction = ThermalHeadroomPolicy.ExportAction.FULL_SPEED

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int) {
        handleForegroundServiceTimeout(startId = startId, foregroundServiceType = null)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        handleForegroundServiceTimeout(startId = startId, foregroundServiceType = fgsType)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            Log.d(TAG, "Cancel requested")
            videoEngine.cancelExport()
            stopThermalMonitoring()
            return START_NOT_STICKY
        }

        latestOutputPath = intent?.getStringExtra(EXTRA_OUTPUT_PATH) ?: latestOutputPath

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
            stopThermalMonitoring()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun handleForegroundServiceTimeout(startId: Int, foregroundServiceType: Int?) {
        Log.w(TAG, "Foreground service timeout: startId=$startId type=$foregroundServiceType")
        observeJob?.cancel()
        observeJob = null
        stopThermalMonitoring()

        if (shouldFailExportForForegroundServiceTimeout(foregroundServiceType)) {
            val message = getString(R.string.export_media_processing_timeout_error)
            val failedActiveExport = videoEngine.failExportDueToForegroundServiceTimeout(message)
            if (failedActiveExport) {
                notifyError(message)
                return
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    private fun startObservingExport() {
        observeJob?.cancel()
        // Reset progress tracker so a new export doesn't inherit the previous run's value
        // (without this, a second export starts at progress 0 but the update gate
        // `progress - lastNotifiedProgress < 2` skips until progress catches up to 99+).
        lastNotifiedProgress = -1
        startThermalMonitoring()
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
        // Allow backward jumps (new export started); only throttle forward creep.
        if (progress > lastNotifiedProgress && progress - lastNotifiedProgress < 2 && progress < 100) return
        lastNotifiedProgress = progress
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(progress))
    }

    private fun notifyComplete() {
        stopThermalMonitoring()
        val nm = getSystemService(NotificationManager::class.java)
        nm?.cancel(NOTIFICATION_ID) // Cancel the progress notification first

        val openIntent = buildOpenIntent()
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

    private fun buildOpenIntent(): Intent {
        val file = latestOutputPath?.let(::File)?.takeIf { it.exists() }
        if (file != null) {
            val uri = runCatching {
                FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
            }.getOrElse { error ->
                Log.w(TAG, "Export notification FileProvider handoff failed for ${file.path}", error)
                return Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            return Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, exportMimeTypeFor(file.name))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        return Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    private fun notifyError(message: String) {
        stopThermalMonitoring()
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
        stopThermalMonitoring()
        val nm = getSystemService(NotificationManager::class.java)
        nm?.cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopThermalMonitoring()
        observeJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startThermalMonitoring() {
        stopThermalMonitoring()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }

        val powerManager = getSystemService(PowerManager::class.java) ?: return
        latestThermalStatus = ThermalHeadroomPolicy.ThermalStatus.fromOs(powerManager.currentThermalStatus)
        lastThermalAction = ThermalHeadroomPolicy.ExportAction.FULL_SPEED

        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            latestThermalStatus = ThermalHeadroomPolicy.ThermalStatus.fromOs(status)
        }
        runCatching {
            powerManager.addThermalStatusListener(mainExecutor, listener)
            thermalStatusListener = listener
        }.onFailure { error ->
            Log.w(TAG, "Unable to register thermal status listener", error)
            thermalStatusListener = null
        }

        thermalJob = serviceScope.launch {
            while (isActive && videoEngine.exportState.value == ExportState.EXPORTING) {
                evaluateThermalDecision(powerManager)
                delay(THERMAL_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopThermalMonitoring() {
        thermalJob?.cancel()
        thermalJob = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = getSystemService(PowerManager::class.java)
            val listener = thermalStatusListener as? PowerManager.OnThermalStatusChangedListener
            if (powerManager != null && listener != null) {
                runCatching {
                    powerManager.removeThermalStatusListener(listener)
                }.onFailure { error ->
                    Log.w(TAG, "Unable to unregister thermal status listener", error)
                }
            }
        }

        thermalStatusListener = null
        latestThermalStatus = ThermalHeadroomPolicy.ThermalStatus.NONE
        lastThermalAction = ThermalHeadroomPolicy.ExportAction.FULL_SPEED
        getSystemService(NotificationManager::class.java)?.cancel(THERMAL_NOTIFICATION_ID)
    }

    private fun evaluateThermalDecision(powerManager: PowerManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            latestThermalStatus = ThermalHeadroomPolicy.ThermalStatus.fromOs(powerManager.currentThermalStatus)
        }
        val headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            powerManager.getThermalHeadroom(THERMAL_FORECAST_SECONDS)
        } else {
            Float.NaN
        }

        val previousAction = lastThermalAction
        val decision = ThermalHeadroomPolicy.decide(
            status = latestThermalStatus,
            headroom = headroom,
            previousAction = previousAction
        )
        lastThermalAction = decision.action

        if (decision.shouldNotifyUser) {
            notifyThermalDecision(decision)
        } else if (
            decision.action == ThermalHeadroomPolicy.ExportAction.FULL_SPEED &&
            previousAction != ThermalHeadroomPolicy.ExportAction.FULL_SPEED
        ) {
            getSystemService(NotificationManager::class.java)?.cancel(THERMAL_NOTIFICATION_ID)
            refreshProgressNotification()
        }

        if (decision.action == ThermalHeadroomPolicy.ExportAction.CANCEL) {
            videoEngine.cancelExport()
        }
    }

    private fun notifyThermalDecision(decision: ThermalHeadroomPolicy.ThrottleDecision) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val (titleRes, textRes, priority) = when (decision.userMessageKey) {
            ThermalHeadroomPolicy.UserMessageKey.THROTTLE_LIGHT -> Triple(
                R.string.notif_export_thermal_light_title,
                R.string.notif_export_thermal_light_text,
                NotificationCompat.PRIORITY_DEFAULT
            )
            ThermalHeadroomPolicy.UserMessageKey.THROTTLE_HEAVY -> Triple(
                R.string.notif_export_thermal_heavy_title,
                R.string.notif_export_thermal_heavy_text,
                NotificationCompat.PRIORITY_HIGH
            )
            ThermalHeadroomPolicy.UserMessageKey.PAUSED_UNTIL_COOL -> Triple(
                R.string.notif_export_thermal_pause_title,
                R.string.notif_export_thermal_pause_text,
                NotificationCompat.PRIORITY_HIGH
            )
            ThermalHeadroomPolicy.UserMessageKey.EMERGENCY_STOP -> Triple(
                R.string.notif_export_thermal_stop_title,
                R.string.notif_export_thermal_stop_text,
                NotificationCompat.PRIORITY_HIGH
            )
            ThermalHeadroomPolicy.UserMessageKey.NONE -> return
        }
        val text = getString(textRes)
        val notification = NotificationCompat.Builder(this, NovaCutApp.CHANNEL_EXPORT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(titleRes))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(priority)
            .setAutoCancel(decision.action != ThermalHeadroomPolicy.ExportAction.PAUSE)
            .build()
        nm.notify(THERMAL_NOTIFICATION_ID, notification)
        refreshProgressNotification()
    }

    private fun refreshProgressNotification() {
        if (videoEngine.exportState.value != ExportState.EXPORTING) return
        val progress = lastNotifiedProgress.coerceAtLeast(0)
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(progress))
    }

    private fun buildNotification(progress: Int): Notification {
        val cancelIntent = Intent(this, ExportService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPi = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val progressText = getString(R.string.notif_export_progress, progress)
        val thermalText = thermalProgressText()
        val contentText = if (thermalText == null) {
            progressText
        } else {
            getString(R.string.notif_export_progress_with_thermal, progressText, thermalText)
        }

        return NotificationCompat.Builder(this, NovaCutApp.CHANNEL_EXPORT)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle(getString(R.string.notif_export_title))
            .setContentText(contentText)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.notif_export_cancel), cancelPi)
            .build()
    }

    private fun thermalProgressText(): String? = when (lastThermalAction) {
        ThermalHeadroomPolicy.ExportAction.FULL_SPEED -> null
        ThermalHeadroomPolicy.ExportAction.THROTTLE_LIGHT ->
            getString(R.string.notif_export_thermal_progress_light)
        ThermalHeadroomPolicy.ExportAction.THROTTLE_HEAVY ->
            getString(R.string.notif_export_thermal_progress_heavy)
        ThermalHeadroomPolicy.ExportAction.PAUSE ->
            getString(R.string.notif_export_thermal_progress_pause)
        ThermalHeadroomPolicy.ExportAction.CANCEL ->
            getString(R.string.notif_export_thermal_progress_stop)
    }
}
