package com.clearcut.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

internal const val CLEARCUT_PACKAGE = "com.novacut.editor"
private const val WAIT_TIMEOUT_MS = 10_000L
private const val PROJECTS_CREATE_PROJECT = "projects.createProject"
private const val PROJECTS_SCREEN = "projects.screen"
private const val TEMPLATE_BLANK = "templates.blank"
private const val EDITOR_SCREEN = "editor.screen"
private const val EDITOR_EXPORT = "editor.export"
private const val EXPORT_SHEET = "export.sheet"
private const val SETTINGS_SCREEN = "settings.screen"
private const val PROJECTS_SETTINGS = "projects.settings"
private const val SETTINGS_PRIVACY_OPEN = "settings.privacy.open"
private const val SETTINGS_PRIVACY_DASHBOARD = "settings.privacy.dashboard"
private const val TUTORIAL_SKIP = "tutorial.skip"

internal fun MacrobenchmarkScope.openProjectGallery() {
    pressHome()
    startActivityAndWait()
    device.waitForProjectGallery()
}

internal fun MacrobenchmarkScope.openBlankEditor() {
    device.waitForProjectCreateButton().tapAndWait(device)
    device.waitForAnyObject(
        listOf(
            testTag(TEMPLATE_BLANK),
            By.descContains("Blank Project")
        )
    ).tapAndWait(device)
    device.waitForEditor()
    dismissTutorialIfPresent()
    device.waitForExportButton()
}

internal fun MacrobenchmarkScope.openExportSheet() {
    device.waitForExportButton().tapAndWait(device)
    device.waitForAnyObject(
        listOf(
            testTag(EXPORT_SHEET),
            By.text("Ready to Export")
        )
    )
}

internal fun MacrobenchmarkScope.scrubTimelineViewport() {
    val width = device.displayWidth
    val height = device.displayHeight
    val y = (height * 0.78f).toInt()
    device.swipe((width * 0.78f).toInt(), y, (width * 0.22f).toInt(), y, 16)
    device.waitForIdle()
    device.swipe((width * 0.22f).toInt(), y, (width * 0.78f).toInt(), y, 16)
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.returnToProjectGallery() {
    repeat(3) {
        if (device.waitUntilHasAnyObject(projectGallerySelectors)) return
        device.pressBack()
        device.waitForIdle()
    }
    device.waitForProjectGallery()
}

internal fun MacrobenchmarkScope.openSettingsAndPrivacyDashboard() {
    device.waitForObject(testTag(PROJECTS_SETTINGS)).tapAndWait(device)
    device.waitForObject(testTag(SETTINGS_SCREEN))
    device.waitForObject(testTag(SETTINGS_PRIVACY_OPEN)).tapAndWait(device)
    device.waitForObject(testTag(SETTINGS_PRIVACY_DASHBOARD))
}

private fun MacrobenchmarkScope.dismissTutorialIfPresent() {
    val skip = device.wait(
        Until.findObject(testTag(TUTORIAL_SKIP)),
        1_200L
    ) ?: device.wait(Until.findObject(By.text("Skip")), 300L)
    if (skip != null) {
        skip.tapAndWait(device)
    }
}

private fun UiDevice.waitForObject(selector: BySelector): UiObject2 {
    return wait(Until.findObject(selector), WAIT_TIMEOUT_MS)
        ?: error("Timed out waiting for UI object: $selector")
}

private val projectCreateButtonSelectors = listOf(
    testTag(PROJECTS_CREATE_PROJECT),
    By.text("Create First Project"),
    By.text("New Project")
)

private val projectGallerySelectors = projectCreateButtonSelectors + listOf(
    testTag(PROJECTS_SCREEN),
    By.text("ClearCut"),
    By.text("Create sharper edits with less friction.")
)

private val editorReadySelectors = listOf(
    testTag(EDITOR_SCREEN),
    testTag(EDITOR_EXPORT),
    By.text("Export")
)

private val exportButtonSelectors = listOf(
    testTag(EDITOR_EXPORT),
    By.text("Export")
)

private fun testTag(tag: String): BySelector = By.res(tag)

private fun UiDevice.waitForProjectGallery(): UiObject2 {
    return waitForAnyObject(projectGallerySelectors)
}

private fun UiDevice.waitForProjectCreateButton(): UiObject2 {
    return waitForAnyObject(projectCreateButtonSelectors)
}

private fun UiDevice.waitForEditor(): UiObject2 {
    return waitForAnyObject(editorReadySelectors)
}

private fun UiDevice.waitForExportButton(): UiObject2 {
    return waitForAnyObject(exportButtonSelectors)
}

private fun UiDevice.waitForAnyObject(selectors: List<BySelector>): UiObject2 {
    val deadline = SystemClock.elapsedRealtime() + WAIT_TIMEOUT_MS
    while (SystemClock.elapsedRealtime() < deadline) {
        selectors.forEach { selector ->
            wait(Until.findObject(selector), 250L)?.let { return it }
        }
    }
    error("Timed out waiting for any UI object: $selectors")
}

private fun UiDevice.waitUntilHasObject(selector: BySelector): Boolean {
    return wait(Until.hasObject(selector), 800L)
}

private fun UiDevice.waitUntilHasAnyObject(selectors: List<BySelector>): Boolean {
    return selectors.any { waitUntilHasObject(it) }
}

private fun UiObject2.tapAndWait(device: UiDevice) {
    val center = visibleCenter
    device.click(center.x, center.y)
    device.waitForIdle()
}
