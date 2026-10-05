package com.novacut.editor.ui.projects

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.novacut.editor.ClearCutApp
import com.novacut.editor.MainActivity
import com.novacut.editor.engine.CrashRecordStore
import com.novacut.editor.engine.DiagnosticExportEngine
import com.novacut.editor.ui.ClearCutTestTags
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A crash recorded by the last run shows on the projects screen at the next launch,
 * hands over a report or summary only when tapped, and stays gone once handled.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = ClearCutApp::class,
    sdk = [35],
    qualifiers = "w360dp-h800dp-xxhdpi",
)
class CrashReportBannerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val crashedAt = System.currentTimeMillis() - 60 * 60 * 1000L

    // Planted before the activity starts, the way the last run's handler leaves it.
    @get:Rule(order = 0)
    val plantedCrash = object : ExternalResource() {
        override fun before() {
            CrashRecordStore(context).recordUncaughtException(
                Thread.currentThread(),
                IllegalStateException("decoder released"),
                "v3.81.0",
                crashedAt,
            )
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun copySummaryPutsTheIssueBodyOnTheClipboardAndSetsTheCrashAside() {
        waitForBanner()

        compose.onNodeWithTag(ClearCutTestTags.CRASH_REPORT_COPY).performClick()

        waitUntilBannerGone()
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
        assertTrue(clip, clip.contains("ClearCut closed unexpectedly (java.lang.IllegalStateException)"))
        assertTrue(clip, clip.contains("- ClearCut: ${ClearCutApp.VERSION}"))
        assertTrue(clip, clip.contains("- Version it happened in: v3.81.0"))
        assertTrue(clip, clip.contains("- Android: "))
        assertTrue(clip, clip.contains("- ABI: "))
        assertTrue(clip, !clip.contains("decoder released"))
        assertNoNoticeOnTheNextLaunch()
    }

    @Test
    fun saveReportWritesTheDiagnosticZipWithTheIssueBodyWhereTheUserPicked() {
        waitForBanner()
        val target = File(context.cacheDir, "picked-report.zip")

        compose.onNodeWithTag(ClearCutTestTags.CRASH_REPORT_SAVE).performClick()
        compose.waitForIdle()
        val activity = shadowOf(compose.activity)
        val request = activity.nextStartedActivityForResult.intent
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, request.action)
        assertEquals("application/zip", request.type)
        assertTrue(request.getStringExtra(Intent.EXTRA_TITLE)!!.startsWith("ClearCut-report-"))
        activity.receiveResult(request, Activity.RESULT_OK, Intent().setData(Uri.fromFile(target)))

        waitUntilBannerGone()
        ZipFile(target).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertTrue(names.toString(), CrashRecordStore.CRASH_BUNDLE_ENTRY in names)
            val issueBody = zip.getInputStream(zip.getEntry(DiagnosticExportEngine.ISSUE_BODY_ENTRY)).reader().readText()
            assertTrue(issueBody, issueBody.contains("(java.lang.IllegalStateException)"))
            assertTrue(issueBody, issueBody.contains("- ABI: "))
        }
        assertNoNoticeOnTheNextLaunch()
    }

    @Test
    fun dismissingSetsTheCrashAsideWithoutSharingAnything() {
        waitForBanner()

        compose.onNodeWithTag(ClearCutTestTags.CRASH_REPORT_DISMISS).performClick()

        waitUntilBannerGone()
        assertEquals(null, context.getSystemService(ClipboardManager::class.java).primaryClip)
        assertEquals(null, shadowOf(compose.activity).nextStartedActivityForResult)
        assertNoNoticeOnTheNextLaunch()
    }

    private fun waitForBanner() {
        compose.waitUntil(10_000L) {
            compose.onAllNodesWithTag(ClearCutTestTags.CRASH_REPORT_BANNER).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitUntilBannerGone() {
        compose.waitUntil(10_000L) {
            compose.onAllNodesWithTag(ClearCutTestTags.CRASH_REPORT_BANNER).fetchSemanticsNodes().isEmpty()
        }
    }

    /** What the next launch reads from disk. */
    private fun assertNoNoticeOnTheNextLaunch() {
        val store = CrashRecordStore(context)
        compose.waitUntil(5_000L) { store.noticeAcknowledgedThroughEpochMs() >= crashedAt }
        assertEquals(
            null,
            CrashReportNoticePolicy.latest(
                crashes = store.recentCrashes(),
                exits = emptyList(),
                acknowledgedThroughEpochMs = store.noticeAcknowledgedThroughEpochMs(),
                nowEpochMs = System.currentTimeMillis(),
            )
        )
    }
}
