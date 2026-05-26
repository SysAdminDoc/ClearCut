package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the registry contract that the future `AiModelRequirementSheet`
 * composable depends on (RESEARCH_FEATURE_PLAN_2026-05-25 Highest-Value #10).
 *
 * The two invariants that matter for the sheet:
 *  - Every `Tool` enum has a [AiToolRequirements.ToolRequirement] entry.
 *  - Cloud tools always have `requiresOptInConsent = true` and never claim
 *    `Availability.READY` (a cloud round-trip is never "ready" — the user
 *    must consent to leave their device).
 */
class AiToolRequirementsTest {

    @Test
    fun everyToolHasARegistryEntry() {
        for (tool in AiToolRequirements.Tool.entries) {
            val req = AiToolRequirements.requirementFor(tool.toolId)
            assertNotNull("Tool ${tool.toolId} must have a requirement entry", req)
            // Internal consistency: the resolved tool matches the lookup ID.
            assertEquals(tool, req!!.tool)
        }
    }

    @Test
    fun unknownToolIdReturnsNull() {
        assertNull(AiToolRequirements.requirementFor("nonexistent_tool_id"))
        assertNull(AiToolRequirements.requirementFor(""))
    }

    @Test
    fun cloudToolsRequireExplicitConsentAndAreNeverReady() {
        val cloud = AiToolRequirements.Tool.entries.mapNotNull {
            AiToolRequirements.requirementFor(it.toolId)
        }.filter { it.runtimeLocation == AiToolRequirements.Runtime.CLOUD }
        assertFalse("Expect at least one cloud tool", cloud.isEmpty())
        for (req in cloud) {
            assertTrue(
                "Cloud tool ${req.tool} must require explicit consent",
                req.requiresOptInConsent
            )
            assertEquals(
                "Cloud tool ${req.tool} must be CLOUD_OPT_IN, never READY",
                AiToolRequirements.Availability.CLOUD_OPT_IN,
                req.availability
            )
            // Cloud tools have no local download.
            assertEquals(0L, req.estimatedBytes)
        }
    }

    @Test
    fun onDeviceToolsHaveNonZeroEstimatedBytes() {
        val onDevice = AiToolRequirements.Tool.entries.mapNotNull {
            AiToolRequirements.requirementFor(it.toolId)
        }.filter { it.runtimeLocation == AiToolRequirements.Runtime.ON_DEVICE }
        assertFalse(onDevice.isEmpty())
        for (req in onDevice) {
            assertTrue(
                "On-device tool ${req.tool} must report a non-zero download estimate",
                req.estimatedBytes > 0
            )
        }
    }

    @Test
    fun isDownloadable_reflectsAvailability() {
        val auto = AiToolRequirements.requirementFor("auto_captions")!!
        assertEquals(AiToolRequirements.Availability.MODEL_DOWNLOAD_REQUIRED, auto.availability)
        assertTrue(auto.isDownloadable)

        val noise = AiToolRequirements.requirementFor("reduce_noise")!!
        assertEquals(AiToolRequirements.Availability.READY, noise.availability)
        assertFalse(noise.isDownloadable)

        val stem = AiToolRequirements.requirementFor("stem_separation")!!
        assertEquals(AiToolRequirements.Availability.DEPENDENCY_MISSING, stem.availability)
        assertFalse(stem.isDownloadable)
    }

    @Test
    fun sizeMb_isMegabyteFloor() {
        val auto = AiToolRequirements.requirementFor("auto_captions")!!
        // 152_000_000 bytes / 1_048_576 = ~144 MB (binary). Use floor.
        assertEquals(144, auto.sizeMb)
    }

    @Test
    fun readyToolsExist_soSheetCanShowImmediateUseCases() {
        // At least one tool must be READY out of the box, otherwise the
        // sheet has no positive path to celebrate.
        val ready = AiToolRequirements.Tool.entries.mapNotNull {
            AiToolRequirements.requirementFor(it.toolId)
        }.filter { it.availability == AiToolRequirements.Availability.READY }
        assertFalse("At least one tool must be READY", ready.isEmpty())
    }

    @Test
    fun licenseFieldIsNeverBlank() {
        for (tool in AiToolRequirements.Tool.entries) {
            val req = AiToolRequirements.requirementFor(tool.toolId)!!
            assertTrue(
                "License for ${tool.toolId} must not be blank",
                req.license.isNotBlank()
            )
        }
    }

    @Test
    fun voiceCloneRequiresConsentEvenThoughOnDevice() {
        // Voice cloning is a consent-bearing tool regardless of where it
        // runs — the user must enroll a 6-second sample of someone's voice,
        // which has real-person implications.
        val clone = AiToolRequirements.requirementFor("voice_clone")!!
        assertEquals(AiToolRequirements.Runtime.ON_DEVICE, clone.runtimeLocation)
        assertTrue(clone.requiresOptInConsent)
    }
}
