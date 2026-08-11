package com.novacut.editor

import android.content.Intent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.fetchSemanticsNodes
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.input.key.Key
import androidx.core.content.FileProvider
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novacut.editor.engine.AutoSaveState
import com.novacut.editor.engine.ProjectAutoSave
import com.novacut.editor.engine.db.ProjectDatabase
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Project
import com.novacut.editor.ui.ClearCutTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device timeline gate for the QA-only application id.
 *
 * This source set is intentionally separate from androidTest: a release or
 * debug APK cannot accidentally install the destructive fixture workflow.
 * Every write is made through the normal share/import path and every cleanup
 * target is scoped to this package's generated project and managed-media URIs.
 */
@RunWith(AndroidJUnit4::class)
class QaTimelineInstrumentationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: ProjectDatabase
    private lateinit var autoSave: ProjectAutoSave
    private var importedProjectId: String? = null
    private var fixtureFile: File? = null

    @Before
    fun setUp() {
        assertTrue(
            "The QA timeline test must run against the QA build type",
            BuildConfig.QA_TIMELINE_HARNESS_ENABLED
        )
        assertTrue(
            "The QA timeline test must never target release storage",
            targetContext.packageName.endsWith(QA_APPLICATION_SUFFIX)
        )
        database = Room.databaseBuilder(
            targetContext,
            ProjectDatabase::class.java,
            PROJECT_DATABASE_NAME
        )
            .setDriver(AndroidSQLiteDriver())
            .addMigrations(*ProjectDatabase.ALL_MIGRATIONS)
            .build()
        autoSave = ProjectAutoSave(targetContext)
        cleanupNamedProjects()
    }

    @After
    fun tearDown() {
        runCatching { cleanupNamedProjects() }
        fixtureFile?.delete()
        if (::database.isInitialized) database.close()
    }

    @Test
    fun importEditUndoRedoAndRelaunchStayInsideQaStorage() {
        val fixture = stageFixture()
        sendFixtureToMainActivity(fixture)
        waitForTag(ClearCutTestTags.EDITOR_SCREEN)
        dismissTutorialIfPresent()

        val project = awaitProject()
        importedProjectId = project.id
        val importedState = awaitState { videoClips(it).size == 1 }
        val importedClip = videoClips(importedState).single()
        assertTrue("QA fixture should be a three-second video", importedClip.sourceDurationMs >= 2_500L)

        // Exercise trim through the editor's real tool rail and numeric controls.
        selectClip(importedClip)
        compose.onNodeWithTag(ClearCutTestTags.EDITOR_TOOL_TAB_PREFIX + "edit")
            .performClick()
        waitForTag(ClearCutTestTags.EDITOR_TOOL_ACTION_PREFIX + "trim")
        compose.onNodeWithTag(ClearCutTestTags.EDITOR_TOOL_ACTION_PREFIX + "trim")
            .performClick()
        waitForTag(ClearCutTestTags.TIMELINE_TRIM_START)
        compose.onNodeWithTag(ClearCutTestTags.TIMELINE_TRIM_START)
            .performTextReplacement("0.25")
        compose.onNodeWithTag(ClearCutTestTags.TIMELINE_TRIM_END)
            .performTextReplacement("2.50")
        // Tapping the active tab moves focus away from the numeric field and
        // commits the gesture-style undo/save boundary.
        compose.onNodeWithTag(ClearCutTestTags.EDITOR_TOOL_TAB_PREFIX + "edit")
            .performClick()
        val trimmedState = awaitState {
            videoClips(it).singleOrNull()?.let { clip ->
                clip.trimStartMs == 250L && clip.trimEndMs == 2_500L
            } == true
        }
        assertEquals(250L, videoClips(trimmedState).single().trimStartMs)
        assertEquals(2_500L, videoClips(trimmedState).single().trimEndMs)

        // Keyboard seeking is pointer-free and deterministic: the editor's
        // right-arrow contract advances the playhead by one second.
        compose.onNodeWithTag(ClearCutTestTags.EDITOR_SCREEN)
            .performKeyInput { pressKey(Key.DirectionRight) }
        waitForTag(ClearCutTestTags.TIMELINE_SPLIT)
        compose.onNodeWithTag(ClearCutTestTags.TIMELINE_SPLIT)
            .assertIsEnabled()
            .performClick()
        awaitState { videoClips(it).size == 2 }

        waitForTag(ClearCutTestTags.TIMELINE_DELETE)
        compose.onNodeWithTag(ClearCutTestTags.TIMELINE_DELETE)
            .assertIsEnabled()
            .performClick()
        awaitState { videoClips(it).size == 1 }

        compose.onNodeWithTag(ClearCutTestTags.EDITOR_UNDO)
            .assertIsEnabled()
            .performClick()
        awaitState { videoClips(it).size == 2 }

        compose.onNodeWithTag(ClearCutTestTags.EDITOR_REDO)
            .assertIsEnabled()
            .performClick()
        val redoneState = awaitState { videoClips(it).size == 1 }
        assertEquals(250L, videoClips(redoneState).single().trimStartMs)

        // Recreate the activity, not the process: this exercises the same
        // SavedStateHandle/navigation path used by a relaunch from Recents.
        compose.activity.recreate()
        waitForTag(ClearCutTestTags.EDITOR_SCREEN)
        val restoredState = awaitState { videoClips(it).size == 1 }
        assertEquals(250L, videoClips(restoredState).single().trimStartMs)
        assertTrue(restoredState.project.id == project.id)
        compose.onNodeWithTag(ClearCutTestTags.EDITOR_SCREEN).assertIsDisplayed()
    }

    private fun stageFixture(): File {
        val destination = File(
            targetContext.filesDir,
            "archives/qa-timeline/qa-timeline-fixture.mp4"
        )
        destination.parentFile?.mkdirs()
        targetContext.assets.open(QA_FIXTURE_ASSET).use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        fixtureFile = destination
        return destination
    }

    private fun sendFixtureToMainActivity(file: File) {
        val uri = FileProvider.getUriForFile(
            targetContext,
            "${targetContext.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setClass(targetContext, MainActivity::class.java)
        }
        compose.activity.runOnUiThread {
            compose.activity.onNewIntent(intent)
        }
        compose.waitForIdle()
    }

    private fun selectClip(clip: Clip) {
        compose.onNodeWithTag(ClearCutTestTags.TIMELINE_CLIP_PREFIX + clip.id)
            .performClick()
    }

    private fun awaitProject(): Project {
        var found: Project? = null
        compose.waitUntil(timeoutMillis = 30_000L) {
            found = runBlocking {
                database.projectDao()
                    .getAllProjectsSnapshot()
                    .filter { it.name == QA_PROJECT_NAME }
                    .maxByOrNull(Project::updatedAt)
            }
            found != null
        }
        return requireNotNull(found)
    }

    private fun awaitState(predicate: (AutoSaveState) -> Boolean): AutoSaveState {
        val projectId = requireNotNull(importedProjectId)
        var found: AutoSaveState? = null
        compose.waitUntil(timeoutMillis = 30_000L) {
            found = runBlocking { autoSave.loadRecoveryData(projectId) }
                ?.takeIf(predicate)
            found != null
        }
        return requireNotNull(found)
    }

    private fun videoClips(state: AutoSaveState): List<Clip> {
        return state.tracks
            .filter { track -> track.type == com.novacut.editor.model.TrackType.VIDEO }
            .flatMap { track -> track.clips }
    }

    private fun dismissTutorialIfPresent() {
        runCatching {
            compose.waitUntil(timeoutMillis = 2_000L) {
                compose.onAllNodesWithTag(ClearCutTestTags.TUTORIAL_SKIP)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        }
        if (compose.onAllNodesWithTag(ClearCutTestTags.TUTORIAL_SKIP)
                .fetchSemanticsNodes()
                .isNotEmpty()
        ) {
            compose.onNodeWithTag(ClearCutTestTags.TUTORIAL_SKIP).performClick()
        }
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 10_000L) {
        compose.waitUntil(timeoutMillis) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun cleanupNamedProjects() {
        if (targetContext.packageName.endsWith(QA_APPLICATION_SUFFIX).not()) return
        val projects = runBlocking {
            database.projectDao().getAllProjectsSnapshot() +
                database.projectDao().getTrashedProjects().first()
        }
        projects
            .filter { project -> project.name == QA_PROJECT_NAME }
            .forEach { project -> cleanupProject(project.id) }
    }

    private fun cleanupProject(projectId: String) {
        val state = runBlocking { autoSave.loadRecoveryData(projectId) }
        val sourceUris = state?.tracks
            ?.flatMap { track -> track.clips }
            ?.map { clip -> clip.sourceUri }
            .orEmpty()
        runBlocking { autoSave.clearRecoveryData(projectId) }
        runBlocking { database.projectDao().deleteById(projectId) }
        sourceUris.forEach(::deleteManagedMediaIfOwned)
    }

    private fun deleteManagedMediaIfOwned(uri: android.net.Uri) {
        if (uri.scheme != "file") return
        val file = runCatching { File(requireNotNull(uri.path)).canonicalFile }.getOrNull() ?: return
        val root = runCatching {
            File(targetContext.filesDir, "media/imports").canonicalFile
        }.getOrNull() ?: return
        if (!file.toPath().startsWith(root.toPath()) || !file.isFile) return
        file.delete()
        File(file.parentFile, "${file.name}.asset.json").delete()
    }

    private companion object {
        const val QA_APPLICATION_SUFFIX = ".qa"
        const val PROJECT_DATABASE_NAME = "clearcut.db"
        const val QA_PROJECT_NAME = "qa-timeline-fixture"
        const val QA_FIXTURE_ASSET = "qa-timeline-fixture.mp4"
    }
}
