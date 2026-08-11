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
    fun activeModelAwareEditorToolIdsHaveRegistryEntries() {
        val activeModelAwareToolIds = listOf(
            "auto_captions",
            "denoise",
            "remove_bg",
            "frame_interp",
            "object_remove",
            "video_upscale",
            "ai_background",
            "ai_stabilize",
            "ai_style_transfer"
        )
        for (toolId in activeModelAwareToolIds) {
            assertNotNull("$toolId must resolve before AiModelRequirementSheet adoption", AiToolRequirements.requirementFor(toolId))
        }
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
            if (req.deliveryMode == AiToolRequirements.DeliveryMode.BUILT_IN) {
                assertEquals(0L, req.estimatedBytes)
            } else {
                assertTrue(
                    "On-device tool ${req.tool} must report a non-zero download estimate",
                    req.estimatedBytes > 0
                )
            }
        }
    }

    @Test
    fun isDownloadable_reflectsAvailability() {
        val auto = AiToolRequirements.requirementFor("auto_captions")!!
        assertEquals(AiToolRequirements.Availability.MODEL_DOWNLOAD_REQUIRED, auto.availability)
        assertTrue(auto.isDownloadable)

        val noise = AiToolRequirements.requirementFor("denoise")!!
        assertEquals(AiToolRequirements.Availability.READY, noise.availability)
        assertFalse(noise.isDownloadable)

        val stem = AiToolRequirements.requirementFor("stem_separation")!!
        assertEquals(AiToolRequirements.Availability.DEPENDENCY_MISSING, stem.availability)
        assertFalse(stem.isDownloadable)
    }

    @Test
    fun futureModelsDoNotAdvertiseDownloadsBeforePinsAndRuntimeWiring() {
        val plannedOnlyTools = listOf(
            "ai_style_transfer",
            "video_upscale",
            "tap_segment"
        )

        for (toolId in plannedOnlyTools) {
            val req = AiToolRequirements.requirementFor(toolId)!!
            assertEquals(
                "$toolId must stay unavailable until docs/models.md records exact pins and runtime wiring",
                AiToolRequirements.Availability.DEPENDENCY_MISSING,
                req.availability
            )
            assertEquals(AiToolRequirements.DeliveryMode.DEPENDENCY_NOT_BUNDLED, req.deliveryMode)
            assertEquals(
                AiToolRequirements.RuntimeChecksumBehavior.BLOCKED_UNTIL_PINNED,
                req.runtimeChecksum
            )
            assertNull(req.modelRegistryId)
        }
    }

    @Test
    fun runnableOnDeviceToolsReferenceActiveModelRegistryRows() {
        val activeRegistryIds = setOf(
            "whisper.tiny.en.onnx",
            "selfie_segmenter.tflite",
            "lama_dilated.onnx",
            "deep_filter_mobile_model"
        )

        val runnableOrDownloadable = AiToolRequirements.Tool.entries.mapNotNull {
            AiToolRequirements.requirementFor(it.toolId)
        }.filter {
            it.runtimeLocation == AiToolRequirements.Runtime.ON_DEVICE &&
                it.availability != AiToolRequirements.Availability.DEPENDENCY_MISSING &&
                it.deliveryMode != AiToolRequirements.DeliveryMode.BUILT_IN
        }

        assertEquals(
            activeRegistryIds,
            runnableOrDownloadable.map { it.modelRegistryId }.toSet()
        )
        for (req in runnableOrDownloadable) {
            assertNotNull(req.modelRegistryId)
            assertTrue(
                "Active model registry ID ${req.modelRegistryId} must be documented",
                req.modelRegistryId in activeRegistryIds
            )
            assertTrue(
                "Active ${req.tool} must have runtime/build checksum posture",
                req.runtimeChecksum == AiToolRequirements.RuntimeChecksumBehavior.REQUIRED ||
                    req.runtimeChecksum == AiToolRequirements.RuntimeChecksumBehavior.BUILD_ARTIFACT_PINNED
            )
        }
    }

    @Test
    fun deliveryAndFdroidPostureStayConsistentWithRuntime() {
        for (tool in AiToolRequirements.Tool.entries) {
            val req = AiToolRequirements.requirementFor(tool.toolId)!!
            when (req.runtimeLocation) {
                AiToolRequirements.Runtime.CLOUD -> {
                    assertEquals(AiToolRequirements.DeliveryMode.CLOUD_ONLY, req.deliveryMode)
                    assertEquals(AiToolRequirements.FdroidPosture.NON_FREE_NET, req.fdroidPosture)
                    assertEquals(
                        AiToolRequirements.RuntimeChecksumBehavior.NOT_APPLICABLE,
                        req.runtimeChecksum
                    )
                }
                AiToolRequirements.Runtime.ON_DEVICE -> {
                    assertTrue(
                        "On-device ${tool.toolId} must not be cloud-only",
                        req.deliveryMode != AiToolRequirements.DeliveryMode.CLOUD_ONLY
                    )
                    if (req.deliveryMode == AiToolRequirements.DeliveryMode.BUILT_IN) {
                        assertEquals(
                            AiToolRequirements.RuntimeChecksumBehavior.NOT_APPLICABLE,
                            req.runtimeChecksum,
                        )
                    } else {
                        assertTrue(
                            "On-device ${tool.toolId} must declare a checksum gate",
                            req.runtimeChecksum != AiToolRequirements.RuntimeChecksumBehavior.NOT_APPLICABLE
                        )
                    }
                }
            }
        }
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
            assertTrue(
                "Source URL for ${tool.toolId} must not be blank",
                !req.sourceUrl.isNullOrBlank()
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
