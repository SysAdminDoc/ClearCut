package com.novacut.editor.ui.editor

import org.junit.Assert.assertTrue
import org.junit.Test

class AiToolsDescriptionTest {

    @Test
    fun everyAiToolUsesAUniqueDescriptionResource() {
        val duplicates = aiTools
            .groupBy { it.descriptionResId }
            .filterValues { descriptions -> descriptions.size > 1 }

        assertTrue("Duplicate AI tool descriptions: $duplicates", duplicates.isEmpty())
    }
}
