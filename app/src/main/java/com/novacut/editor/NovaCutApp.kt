package com.novacut.editor

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.novacut.editor.BuildConfig
import com.novacut.editor.engine.CrashRecordStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NovaCutApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        const val CHANNEL_EXPORT = "novacut_export"
        // Source from BuildConfig so the constant can never drift from the gradle versionName.
        // Consumed by model-download User-Agent headers, crash reports, and the about dialog —
        // a stale value here would misreport the user's actual build.
        val VERSION: String = "v${BuildConfig.VERSION_NAME}"
    }

    override fun onCreate() {
        super.onCreate()
        CrashRecordStore(this).installGlobalHandler(VERSION)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val exportChannel = NotificationChannel(
            CHANNEL_EXPORT,
            "Export Progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows video export progress"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(exportChannel)
    }
}
