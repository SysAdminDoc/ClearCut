package com.novacut.editor.model

import com.novacut.editor.model.ProjectColorPolicy.InputColor
import com.novacut.editor.model.ProjectColorPolicy.OutputColor
import com.novacut.editor.model.ProjectColorPolicy.WorkingColorSpace
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectColorPolicyTest {

    @Test
    fun newProjectsStartInSdr() {
        val policy = ProjectColorPolicy.DEFAULT

        assertEquals(InputColor.FROM_SOURCE, policy.input)
        assertEquals(WorkingColorSpace.SDR_BT709, policy.working)
        assertEquals(OutputColor.SDR, policy.output)
        assertFalse(policy.keepsHdr)
        assertTrue(ProjectColorPolicy.KEEP_HDR.keepsHdr)
    }

    @Test
    fun normalizedPairsTheWorkingSpaceWithWhatTheOutputCanHonor() {
        assertEquals(
            ProjectColorPolicy(InputColor.INTERPRET_HDR_AS_SDR, WorkingColorSpace.SDR_BT709, OutputColor.SDR),
            ProjectColorPolicy(InputColor.INTERPRET_HDR_AS_SDR, WorkingColorSpace.HDR_BT2020, OutputColor.KEEP_HDR)
                .normalized(),
        )
        assertEquals(
            ProjectColorPolicy.KEEP_HDR,
            ProjectColorPolicy(output = OutputColor.KEEP_HDR, working = WorkingColorSpace.SDR_BT709).normalized(),
        )
        assertEquals(
            ProjectColorPolicy.DEFAULT,
            ProjectColorPolicy(working = WorkingColorSpace.HDR_BT2020).normalized(),
        )
        assertFalse(
            ProjectColorPolicy(InputColor.INTERPRET_HDR_AS_SDR, output = OutputColor.KEEP_HDR).keepsHdr,
        )
    }

    @Test
    fun keepHdrSwitchMovesBetweenTheTwoDeliveries() {
        assertEquals(ProjectColorPolicy.KEEP_HDR, ProjectColorPolicy.DEFAULT.withKeepHdr(true))
        assertEquals(ProjectColorPolicy.DEFAULT, ProjectColorPolicy.KEEP_HDR.withKeepHdr(false))
        val interpret = ProjectColorPolicy(input = InputColor.INTERPRET_HDR_AS_SDR)
        assertEquals(interpret, interpret.withKeepHdr(false))
    }

    @Test
    fun jsonRoundTripsAndToleratesValuesFromNewerBuilds() {
        val interpret = ProjectColorPolicy(input = InputColor.INTERPRET_HDR_AS_SDR)
        for (policy in listOf(ProjectColorPolicy.DEFAULT, ProjectColorPolicy.KEEP_HDR, interpret)) {
            assertEquals(policy, ProjectColorPolicy.fromJson(JSONObject(policy.toJson().toString())))
        }
        val newer = JSONObject()
            .put("input", "SOMETHING_NEWER")
            .put("working", "ACES_CG")
            .put("output", "KEEP_HDR")
        assertEquals(ProjectColorPolicy.KEEP_HDR, ProjectColorPolicy.fromJson(newer))
        assertEquals(ProjectColorPolicy.DEFAULT, ProjectColorPolicy.fromJson(JSONObject()))
    }

    @Test
    fun filesWithoutAPolicyMigrateFromTheOldHdrSwitch() {
        assertNull(ProjectColorPolicy.fromJson(null))
        assertEquals(ProjectColorPolicy.DEFAULT, ProjectColorPolicy.legacy())
        assertEquals(ProjectColorPolicy.KEEP_HDR, ProjectColorPolicy.legacy(hdrRequested = true))
    }
}
