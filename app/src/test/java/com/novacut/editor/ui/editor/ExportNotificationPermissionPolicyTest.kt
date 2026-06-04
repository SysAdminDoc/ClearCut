package com.novacut.editor.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportNotificationPermissionPolicyTest {

    @Test
    fun `pre Android 13 exports do not need notification prompt`() {
        val decision = decideExportNotificationPermission(
            sdkInt = 32,
            notificationPermissionGranted = false,
            promptAlreadyHandled = false
        )

        assertFalse(decision.shouldPrompt)
        assertTrue(decision.notificationsEnabled)
    }

    @Test
    fun `Android 13 denied permission prompts once`() {
        val decision = decideExportNotificationPermission(
            sdkInt = 33,
            notificationPermissionGranted = false,
            promptAlreadyHandled = false
        )

        assertTrue(decision.shouldPrompt)
        assertFalse(decision.notificationsEnabled)
    }

    @Test
    fun `handled Android 13 denial keeps export in app without reprompting`() {
        val decision = decideExportNotificationPermission(
            sdkInt = 33,
            notificationPermissionGranted = false,
            promptAlreadyHandled = true
        )

        assertFalse(decision.shouldPrompt)
        assertFalse(decision.notificationsEnabled)
    }

    @Test
    fun `granted Android 13 permission enables notifications without prompt`() {
        val decision = decideExportNotificationPermission(
            sdkInt = 33,
            notificationPermissionGranted = true,
            promptAlreadyHandled = false
        )

        assertFalse(decision.shouldPrompt)
        assertTrue(decision.notificationsEnabled)
    }
}
