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
        assertNotNull(OpenSourceLicenses.noticeForArtifact("third_party/ffmpeg-kit-next/ffmpeg-kit-next-8.1.0.aar"))
        assertNotNull(OpenSourceLicenses.noticeForArtifact("io.github.kaleyravideo:android-deepfilternet"))
    }

    @Test
    fun ffmpegKitNoticePreservesSourceOfferAndGplWarning() {
        val notice = OpenSourceLicenses.ffmpegKitNotice()

        assertTrue(notice.version.contains("8.1.0"))
        assertTrue(notice.version.contains("FFmpeg 8.1.2"))
        assertTrue(notice.licenseName.contains("General Public License"))
        assertTrue(notice.licenseText.contains("GPL"))
        assertTrue(notice.sourceOfferText.orEmpty().contains("source", ignoreCase = true))
        assertTrue(notice.sourceOfferText.orEmpty().contains("GPL v3.0"))
        assertTrue(notice.sourceOfferText.orEmpty().contains("3e223118e6e8fb6208693ecf3952e77cd096f587"))
        assertTrue(notice.sourceOfferText.orEmpty().contains("nix-android.sh"))
        assertTrue(notice.complianceNote.orEmpty().contains("res/raw/source.txt"))
        assertTrue(notice.complianceNote.orEmpty().contains("ffmpeg_kit_next_source_offer.txt"))
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

        assertEquals(listOf("third_party/ffmpeg-kit-next/ffmpeg-kit-next-8.1.0.aar"), sourceOfferArtifacts)
    }
}
