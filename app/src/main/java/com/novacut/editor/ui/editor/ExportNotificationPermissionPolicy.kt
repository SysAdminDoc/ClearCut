package com.novacut.editor.ui.editor

internal data class ExportNotificationPermissionDecision(
    val shouldPrompt: Boolean,
    val notificationsEnabled: Boolean
)

internal fun decideExportNotificationPermission(
    sdkInt: Int,
    notificationPermissionGranted: Boolean,
    promptAlreadyHandled: Boolean
): ExportNotificationPermissionDecision {
    val requiresRuntimePermission = sdkInt >= 33
    val notificationsEnabled = !requiresRuntimePermission || notificationPermissionGranted
    return ExportNotificationPermissionDecision(
        shouldPrompt = requiresRuntimePermission && !notificationPermissionGranted && !promptAlreadyHandled,
        notificationsEnabled = notificationsEnabled
    )
}
