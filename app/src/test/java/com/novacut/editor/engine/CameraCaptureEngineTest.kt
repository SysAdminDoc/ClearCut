package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCaptureEngineTest {

    @Test
    fun cameraXMissing_keepsOnlyExternalCameraHandoffAvailable() {
        val capability = cameraCaptureCapability(cameraXAvailable = false)

        assertTrue(capability.externalHandoff.available)
        assertEquals("Open camera app", capability.externalHandoff.label)
        assertFalse(capability.externalHandoff.requiresNovaCutCameraPermission)
        assertFalse(capability.inAppRecorder.available)
        assertTrue(capability.inAppRecorder.requiresRuntimeCameraPermission)
        assertEquals(
            "CameraX VideoCapture is not bundled; use the external camera-app handoff.",
            capability.inAppRecorder.unavailableReason
        )
    }

    @Test
    fun cameraXPresent_enablesSeparateInAppRecorderCapability() {
        val capability = cameraCaptureCapability(cameraXAvailable = true)

        assertTrue(capability.externalHandoff.available)
        assertFalse(capability.externalHandoff.requiresNovaCutCameraPermission)
        assertTrue(capability.inAppRecorder.available)
        assertTrue(capability.inAppRecorder.requiresRuntimeCameraPermission)
        assertNull(capability.inAppRecorder.unavailableReason)
    }
}
