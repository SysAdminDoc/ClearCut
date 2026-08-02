package com.novacut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRelinkOpenToastTest {

    @Test
    fun mediaRelinkOpenToast_isNullWhenNoProblems() {
        assertTrue(mediaRelinkOpenToast(missingCount = 0, unknownCount = 0).isEmpty())
    }

    @Test
    fun mediaRelinkOpenToast_describesMissingAndUnverifiedSources() {
        assertEquals(
            listOf(MediaRelinkOpenToastPart(1, com.novacut.editor.R.plurals.vm_media_missing_sources)),
            mediaRelinkOpenToast(missingCount = 1, unknownCount = 0)
        )
        assertEquals(
            listOf(
                MediaRelinkOpenToastPart(2, com.novacut.editor.R.plurals.vm_media_missing_sources),
                MediaRelinkOpenToastPart(1, com.novacut.editor.R.plurals.vm_media_unverified_sources),
            ),
            mediaRelinkOpenToast(missingCount = 2, unknownCount = 1)
        )
    }

    @Test
    fun mediaRelinkOpenToast_describesManifestHealthIssues() {
        assertEquals(
            listOf(
                MediaRelinkOpenToastPart(1, com.novacut.editor.R.plurals.vm_media_repair_items),
                MediaRelinkOpenToastPart(2, com.novacut.editor.R.plurals.vm_media_warnings),
            ),
            mediaRelinkOpenToast(
                missingCount = 0,
                unknownCount = 0,
                healthBlockingCount = 1,
                healthWarningCount = 2,
            )
        )
    }
}
