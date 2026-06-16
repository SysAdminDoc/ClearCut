package com.novacut.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class MediaPickerCameraHandoffCopyTest {

    @Test
    fun mediaPickerCopyDescribesExternalCameraHandoff() {
        val strings = stringResources(File("src/main/res/values/strings.xml"))

        assertEquals("External Camera Handoff", strings.getValue("media_picker_capture_title"))
        assertEquals("Open Camera App", strings.getValue("media_picker_record_video"))
        assertTrue(strings.getValue("media_picker_capture_description").contains("device camera app"))
        assertTrue(strings.getValue("media_picker_capture_description").contains("does not request camera permission"))
        assertTrue(strings.getValue("media_picker_camera_empty_capture").contains("empty clip"))
        assertFalse(strings.getValue("media_picker_subtitle").contains("without leaving"))
    }

    @Test
    fun manifestKeepsCameraPermissionAbsentUntilInAppRecorderShips() {
        val manifest = parseXml(File("src/main/AndroidManifest.xml")).documentElement
        val permissions = manifest.childElements("uses-permission")
            .map { it.androidName }
            .toSet()
        val cameraFeature = manifest.childElements("uses-feature")
            .firstOrNull { it.androidName == "android.hardware.camera" }
            ?: error("Camera feature declaration missing")

        assertFalse("External camera handoff must not declare CAMERA permission", "android.permission.CAMERA" in permissions)
        assertEquals("false", cameraFeature.getAttributeNS(ANDROID_NS, "required"))
    }

    private fun stringResources(file: File): Map<String, String> {
        val resources = parseXml(file).documentElement
        val strings = mutableMapOf<String, String>()
        for (index in 0 until resources.childNodes.length) {
            val element = resources.childNodes.item(index) as? Element ?: continue
            if (element.tagName == "string") {
                strings[element.getAttribute("name")] = element.textContent
            }
        }
        return strings
    }

    private fun parseXml(file: File) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(file)

    private fun Element.childElements(tagName: String): List<Element> {
        val nodes = getElementsByTagName(tagName)
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private val Element.androidName: String
        get() = getAttributeNS(ANDROID_NS, "name")

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
