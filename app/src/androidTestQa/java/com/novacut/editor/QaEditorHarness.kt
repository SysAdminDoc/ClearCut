package com.novacut.editor

import android.content.Intent
import android.view.ViewConfiguration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.core.content.FileProvider
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.novacut.editor.engine.AutoSaveState
import com.novacut.editor.engine.ProjectAutoSave
import com.novacut.editor.engine.db.ProjectDatabase
import com.novacut.editor.model.Clip
import com.novacut.editor.model.Project
import com.novacut.editor.model.TrackType
import com.novacut.editor.ui.ClearCutTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import java.io.File

/**
 * Opens a QA-only project holding the three-second fixture clip through the normal
 * share path, reads what the editor saved back from the autosave store, and reads the
 * undo stack through the editor's own Version history panel. Everything it writes is
 * scoped to the QA application id and to projects named after [fixtureName].
 */
internal class QaEditorHarness(
    private val compose: AndroidComposeTestRule<*, MainActivity>,
    private val fixtureName: String,
) {
    val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: ProjectDatabase
    private lateinit var autoSave: ProjectAutoSave
    private var projectId: String? = null
    private var fixtureFile: File? = null

    val density: Float
        get() = targetContext.resources.displayMetrics.density

    val touchSlopPx: Float
        get() = ViewConfiguration.get(targetContext).scaledTouchSlop.toFloat()

    fun setUp() {
        assertTrue("QA editor tests must run against the QA build type", BuildConfig.QA_TIMELINE_HARNESS_ENABLED)
        assertTrue(
            "QA editor tests must never target release storage",
            targetContext.packageName.endsWith(QA_APPLICATION_SUFFIX),
        )
        database = Room.databaseBuilder(targetContext, ProjectDatabase::class.java, PROJECT_DATABASE_NAME)
            .setDriver(AndroidSQLiteDriver())
            .addMigrations(*ProjectDatabase.ALL_MIGRATIONS)
            .build()
        autoSave = ProjectAutoSave(targetContext)
        cleanupNamedProjects()
    }

    fun tearDown() {
        runCatching { cleanupNamedProjects() }
        fixtureFile?.delete()
        if (::database.isInitialized) database.close()
    }

    /** Imports the fixture, waits for the editor, and returns its single video clip. */
    fun importFixture(): Clip {
        val destination = File(targetContext.filesDir, "archives/$fixtureName/$fixtureName.mp4")
        destination.parentFile?.mkdirs()
        targetContext.assets.open(QA_FIXTURE_ASSET).use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        fixtureFile = destination
        val uri = FileProvider.getUriForFile(targetContext, "${targetContext.packageName}.fileprovider", destination)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setClass(targetContext, MainActivity::class.java)
        }
        val launchIntent = compose.activity.intent
        compose.activity.runOnUiThread {
            InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(compose.activity, intent)
            // ActivityScenario follows the activity by the intent it launched with. The
            // app keeps the shared intent through setIntent, so hand the launch intent
            // back once the import has been taken in, or the scenario never sees it end.
            compose.activity.intent = launchIntent
        }
        compose.waitForIdle()
        waitForTag(ClearCutTestTags.EDITOR_SCREEN)
        dismissTutorialIfPresent()
        projectId = awaitProject().id
        val clip = videoClips(awaitState { videoClips(it).size == 1 }).single()
        waitForTag(ClearCutTestTags.TIMELINE_CLIP_PREFIX + clip.id)
        compose.waitForIdle()
        return clip
    }

    fun awaitState(predicate: (AutoSaveState) -> Boolean): AutoSaveState {
        val id = requireNotNull(projectId)
        var found: AutoSaveState? = null
        compose.waitUntil(timeoutMillis = 30_000L) {
            found = runBlocking { autoSave.loadRecoveryData(id) }?.takeIf(predicate)
            found != null
        }
        return requireNotNull(found)
    }

    fun awaitClip(clipId: String, predicate: (Clip) -> Boolean): Clip =
        videoClips(awaitState { state -> videoClips(state).any { it.id == clipId && predicate(it) } })
            .first { it.id == clipId }

    fun videoClips(state: AutoSaveState): List<Clip> = state.tracks
        .filter { it.type == TrackType.VIDEO }
        .flatMap { it.clips }

    fun clipNode(clip: Clip): SemanticsNode =
        compose.onNodeWithTag(ClearCutTestTags.TIMELINE_CLIP_PREFIX + clip.id).fetchSemanticsNode()

    /**
     * Pixels per millisecond the timeline is drawn at, read off the clip block: the
     * block is the clip's duration at that scale less its 1dp inset on each side.
     */
    fun pixelsPerMs(clip: Clip, node: SemanticsNode = clipNode(clip)): Float =
        (node.size.width + 2f * density) / clip.durationMs

    /** Runs touch input in root coordinates, so a gesture can start anywhere on the editor. */
    fun touchInRoot(block: TouchInjectionScope.(toLocal: (Offset) -> Offset) -> Unit) {
        val screen = compose.onNodeWithTag(ClearCutTestTags.EDITOR_SCREEN)
        val origin = screen.fetchSemanticsNode().positionInRoot
        screen.performTouchInput { block { point -> point - origin } }
        compose.waitForIdle()
    }

    /** One finger down at [from], a steady move to [to] in [steps] events, then up. */
    fun dragInRoot(from: Offset, to: Offset, steps: Int = 16) = touchInRoot { toLocal ->
        down(toLocal(from))
        for (step in 1..steps) {
            moveTo(toLocal(from + (to - from) * (step / steps.toFloat())))
        }
        up()
    }

    /**
     * The undo stack, newest first, as the Version history panel lists it. The menu item
     * only exists once there is something to undo, so a missing item reads as empty.
     */
    fun undoHistory(): List<String> {
        compose.onNodeWithTag(ClearCutTestTags.EDITOR_OVERFLOW).performClick()
        compose.waitForIdle()
        if (compose.onAllNodesWithTag(ClearCutTestTags.EDITOR_VERSION_HISTORY).fetchSemanticsNodes().isEmpty()) {
            Espresso.pressBack()
            compose.waitForIdle()
            return emptyList()
        }
        compose.onNodeWithTag(ClearCutTestTags.EDITOR_VERSION_HISTORY).performClick()
        waitForTag(ClearCutTestTags.UNDO_HISTORY_ENTRY_DESCRIPTION)
        val descriptions = compose
            .onAllNodesWithTag(ClearCutTestTags.UNDO_HISTORY_ENTRY_DESCRIPTION, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
            }
        compose.onNodeWithContentDescription(targetContext.getString(R.string.undo_history_close)).performClick()
        compose.waitUntil(timeoutMillis = 5_000L) {
            compose.onAllNodesWithTag(ClearCutTestTags.UNDO_HISTORY_ENTRY_DESCRIPTION, useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
        return descriptions
    }


    fun waitForTag(tag: String, timeoutMillis: Long = 10_000L) {
        compose.waitUntil(timeoutMillis) {
            compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun dismissTutorialIfPresent() {
        runCatching {
            compose.waitUntil(timeoutMillis = 2_000L) {
                compose.onAllNodesWithTag(ClearCutTestTags.TUTORIAL_SKIP).fetchSemanticsNodes().isNotEmpty()
            }
        }
        if (compose.onAllNodesWithTag(ClearCutTestTags.TUTORIAL_SKIP).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag(ClearCutTestTags.TUTORIAL_SKIP).performClick()
        }
    }

    private fun awaitProject(): Project {
        var found: Project? = null
        compose.waitUntil(timeoutMillis = 30_000L) {
            found = runBlocking {
                database.projectDao().getAllProjectsSnapshot()
                    .filter { it.name == fixtureName }
                    .maxByOrNull(Project::updatedAt)
            }
            found != null
        }
        return requireNotNull(found)
    }

    private fun cleanupNamedProjects() {
        if (!targetContext.packageName.endsWith(QA_APPLICATION_SUFFIX)) return
        val projects = runBlocking {
            database.projectDao().getAllProjectsSnapshot() + database.projectDao().getTrashedProjects().first()
        }
        projects.filter { it.name == fixtureName }.forEach { project ->
            val state = runBlocking { autoSave.loadRecoveryData(project.id) }
            val sourceUris = state?.tracks?.flatMap { it.clips }?.map { it.sourceUri }.orEmpty()
            runBlocking { autoSave.clearRecoveryData(project.id) }
            runBlocking { database.projectDao().deleteById(project.id) }
            sourceUris.forEach(::deleteManagedMediaIfOwned)
        }
    }

    private fun deleteManagedMediaIfOwned(uri: android.net.Uri) {
        if (uri.scheme != "file") return
        val file = runCatching { File(requireNotNull(uri.path)).canonicalFile }.getOrNull() ?: return
        val root = runCatching { File(targetContext.filesDir, "media/imports").canonicalFile }.getOrNull() ?: return
        if (!file.toPath().startsWith(root.toPath()) || !file.isFile) return
        file.delete()
        File(file.parentFile, "${file.name}.asset.json").delete()
    }

    private companion object {
        const val QA_APPLICATION_SUFFIX = ".qa"
        const val PROJECT_DATABASE_NAME = "clearcut.db"
        const val QA_FIXTURE_ASSET = "qa-timeline-fixture.mp4"
    }
}
