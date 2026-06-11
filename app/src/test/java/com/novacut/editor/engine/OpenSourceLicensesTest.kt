package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSourceLicensesTest {
    @Test
    fun registryIsNotEmptyAndEveryNoticeHasRequiredDisplayFields() {
        assertTrue(OpenSourceLicenses.notices.isNotEmpty())

        OpenSourceLicenses.notices.forEach { notice ->
            assertFalse(notice.name.isBlank())
            assertFalse(notice.version.isBlank())
            assertFalse(notice.artifact.isBlank())
            assertFalse(notice.licenseName.isBlank())
            assertFalse(notice.licenseText.isBlank())
            assertFalse(notice.licenseUrl.isBlank())
            assertFalse(notice.projectUrl.isBlank())
        }
    }

    @Test
    fun registryContainsReleaseNoticeObligationDependencies() {
        assertNotNull(OpenSourceLicenses.noticeForArtifact("com.moizhassan.ffmpeg:ffmpeg-kit-16kb"))
        assertNotNull(OpenSourceLicenses.noticeForArtifact("io.github.kaleyravideo:android-deepfilternet"))
    }

    @Test
    fun ffmpegKitNoticePreservesSourceOfferAndGplWarning() {
        val notice = OpenSourceLicenses.ffmpegKitNotice()

        assertTrue(notice.licenseText.contains("GPLv3"))
        assertTrue(notice.sourceOfferText.orEmpty().contains("source", ignoreCase = true))
        assertTrue(notice.sourceOfferText.orEmpty().contains("GPL v3.0"))
        assertTrue(notice.sourceOfferText.orEmpty().contains("https://github.com/arthenica/ffmpeg-kit/wiki/Source"))
        assertTrue(notice.complianceNote.orEmpty().contains("res/raw/source.txt"))
    }

    @Test
    fun requiredApacheDependenciesAreMarkedApacheLicensed() {
        val apacheArtifacts = listOf(
            "com.google.mediapipe:tasks-vision",
            "com.airbnb.android:lottie-compose",
            "com.squareup.okhttp3:okhttp",
            "io.github.kaleyravideo:android-deepfilternet",
        )

        apacheArtifacts.forEach { artifact ->
            val notice = OpenSourceLicenses.noticeForArtifact(artifact)
            assertNotNull(notice)
            assertTrue(notice!!.licenseName.contains("Apache", ignoreCase = true))
        }
    }

    @Test
    fun onlyFfmpegKitCurrentlyNeedsSourceOfferCopy() {
        val sourceOfferArtifacts = OpenSourceLicenses.dependenciesWithSourceOffers().map { it.artifact }

        assertEquals(listOf("com.moizhassan.ffmpeg:ffmpeg-kit-16kb"), sourceOfferArtifacts)
    }
}
