package com.novacut.editor.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaRelinkOpenToastTest {

    @Test
    fun mediaRelinkOpenToast_isNullWhenNoProblems() {
        assertNull(mediaRelinkOpenToast(missingCount = 0, unknownCount = 0))
    }

    @Test
    fun mediaRelinkOpenToast_describesMissingAndUnverifiedSources() {
        assertEquals(
            "Media check found 1 missing source. Opened Media Manager to relink before editing or export.",
            mediaRelinkOpenToast(missingCount = 1, unknownCount = 0)
        )
        assertEquals(
            "Media check found 2 missing and 1 unverified sources. Opened Media Manager to relink before editing or export.",
            mediaRelinkOpenToast(missingCount = 2, unknownCount = 1)
        )
    }
}
