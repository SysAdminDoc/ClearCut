package com.novacut.editor.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** R8.2 — C2paExportEngine contract tests. */
class C2paExportEngineTest {

    private val engine = C2paExportEngine()

    private fun ledgerEntry(
        clip: String = "clip-1",
        kind: AiUsageLedger.EffectKind = AiUsageLedger.EffectKind.AUTO_EDIT_LOCAL,
        model: String = "novacut.auto-edit.v1",
        start: Long = 0L,
        end: Long = 1_000L,
        recorded: Long = 1_700_000_000_000L
    ) = AiUsageLedger.Entry(
        clipId = clip,
        effectKind = kind,
        modelName = model,
        rangeStartMs = start,
        rangeEndMs = end,
        recordedAtEpochMs = recorded
    )

    @Test
    fun buildManifest_alwaysEmitsTrainingMiningOptOutAndThumbnailAndCreated() {
        val m = engine.buildManifest(
            projectTitle = "My Project",
            novaCutVersionName = "3.74.9",
            signingMode = C2paExportEngine.SigningMode.ANDROID_KEYSTORE,
            ledger = emptyList(),
            exporterCreationTimeMs = 1_700_000_000_000L
        )
        val labels = m.assertions.map { it.label }
        assertTrue("c2pa.training-mining" in labels)
        assertTrue("c2pa.thumbnail.claim.jpeg" in labels)
        assertTrue("c2pa.actions.created" in labels)
        // No AI actions assertion when ledger empty.
        assertFalse("c2pa.actions" in labels)
    }

    @Test
    fun buildManifest_carriesClaimGeneratorVersion() {
        val m = engine.buildManifest(
            projectTitle = null,
            novaCutVersionName = "3.74.9",
            signingMode = C2paExportEngine.SigningMode.ANDROID_KEYSTORE,
            ledger = emptyList(),
            exporterCreationTimeMs = 0L
        )
        assertEquals("NovaCut/3.74.9", m.claimGenerator)
        assertEquals("3.74.9", m.claimGeneratorInfoVersion)
        assertNull(m.title)
        assertEquals(C2paExportEngine.SigningMode.ANDROID_KEYSTORE, m.signingMode)
    }

    @Test
    fun buildManifest_blankTitleNormalizesToNull() {
        val m = engine.buildManifest(
            projectTitle = "   ",
            novaCutVersionName = "3.74.9",
            signingMode = C2paExportEngine.SigningMode.STRONGBOX,
            ledger = emptyList(),
            exporterCreationTimeMs = 0L
        )
        assertNull(m.title)
    }

    @Test
    fun buildManifest_rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.buildManifest(
                projectTitle = null,
                novaCutVersionName = "",
                signingMode = C2paExportEngine.SigningMode.ANDROID_KEYSTORE,
                ledger = emptyList(),
                exporterCreationTimeMs = 0L
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            engine.buildManifest(
                projectTitle = null,
                novaCutVersionName = "3.74.9",
                signingMode = C2paExportEngine.SigningMode.ANDROID_KEYSTORE,
                ledger = emptyList(),
                exporterCreationTimeMs = -1L
            )
        }
    }

    @Test
    fun aiActionsAssertion_returnsNullForEmptyAndInternalOnlyLedger() {
        assertNull(engine.aiActionsAssertion(emptyList()))
        assertNull(
            engine.aiActionsAssertion(
                listOf(
                    ledgerEntry(kind = AiUsageLedger.EffectKind.TTS_LOCAL, model = "piper.amy"),
                    ledgerEntry(kind = AiUsageLedger.EffectKind.CAPTION_TRANSLATION_LOCAL, model = "Bergamot en-es"),
                    ledgerEntry(kind = AiUsageLedger.EffectKind.BACKGROUND_REMOVAL_LOCAL, model = "selfie-segmenter")
                )
            )
        )
    }

    @Test
    fun aiActionsAssertion_cloudGenerativeBecomesCreatedTrainedAlgorithmic() {
        val a = engine.aiActionsAssertion(
            listOf(
                ledgerEntry(
                    clip = "c1",
                    kind = AiUsageLedger.EffectKind.GENERATIVE_VIDEO_CLOUD,
                    model = "Wan 2.2"
                )
            )
        )
        assertNotNull(a)
        assertEquals("c2pa.actions", a!!.label)
        @Suppress("UNCHECKED_CAST")
        val actions = a.data["actions"] as List<Map<String, Any?>>
        assertEquals(1, actions.size)
        val action = actions[0]
        assertEquals("c2pa.created", action["action"])
        assertEquals("Wan 2.2", action["softwareAgent"])
        assertEquals("trainedAlgorithmicMedia", action["digitalSourceType"])
        @Suppress("UNCHECKED_CAST")
        val params = action["parameters"] as Map<String, Any?>
        assertEquals("c1", params["clipId"])
    }

    @Test
    fun aiActionsAssertion_autoEditBecomesEditedCompositeWithTrained() {
        val a = engine.aiActionsAssertion(
            listOf(
                ledgerEntry(kind = AiUsageLedger.EffectKind.AUTO_EDIT_LOCAL, model = "novacut.auto-edit.v1")
            )
        )
        @Suppress("UNCHECKED_CAST")
        val action = (a!!.data["actions"] as List<Map<String, Any?>>)[0]
        assertEquals("c2pa.edited", action["action"])
        assertEquals("compositeWithTrainedAlgorithmicMedia", action["digitalSourceType"])
    }

    @Test
    fun aiActionsAssertion_styleTransferBecomesEditedAlgorithmic() {
        val a = engine.aiActionsAssertion(
            listOf(
                ledgerEntry(
                    kind = AiUsageLedger.EffectKind.STYLE_TRANSFER_LOCAL,
                    model = "anime-gan-v2"
                )
            )
        )
        @Suppress("UNCHECKED_CAST")
        val action = (a!!.data["actions"] as List<Map<String, Any?>>)[0]
        assertEquals("c2pa.edited", action["action"])
        assertEquals("algorithmicMedia", action["digitalSourceType"])
    }

    @Test
    fun aiActionsAssertion_filtersInternalOnlyEvenWhenMixedWithDisclosure() {
        val a = engine.aiActionsAssertion(
            listOf(
                ledgerEntry(kind = AiUsageLedger.EffectKind.TTS_LOCAL, model = "piper.amy"),
                ledgerEntry(
                    clip = "c2",
                    kind = AiUsageLedger.EffectKind.GENERATIVE_VIDEO_CLOUD,
                    model = "Wan 2.2"
                )
            )
        )
        @Suppress("UNCHECKED_CAST")
        val actions = a!!.data["actions"] as List<Map<String, Any?>>
        assertEquals(1, actions.size)
        assertEquals("c2pa.created", actions[0]["action"])
    }

    @Test
    fun buildManifest_emitsAiActionsAssertionWhenLedgerNonEmpty() {
        val m = engine.buildManifest(
            projectTitle = "Project",
            novaCutVersionName = "3.74.9",
            signingMode = C2paExportEngine.SigningMode.ANDROID_KEYSTORE,
            ledger = listOf(
                ledgerEntry(kind = AiUsageLedger.EffectKind.LIP_SYNC_CLOUD, model = "MuseTalk")
            ),
            exporterCreationTimeMs = 1_700_000_000_000L
        )
        val labels = m.assertions.map { it.label }
        assertTrue("c2pa.actions" in labels)
    }

    @Test
    fun manifestToJson_serializesAssertionsDeterministically() {
        val manifest = engine.buildManifest(
            projectTitle = "Project",
            novaCutVersionName = "3.74.9",
            signingMode = C2paExportEngine.SigningMode.ANDROID_KEYSTORE,
            ledger = listOf(
                ledgerEntry(kind = AiUsageLedger.EffectKind.AUTO_EDIT_LOCAL, model = "NovaCut Auto Edit")
            ),
            exporterCreationTimeMs = 1_700_000_000_000L
        )

        val json = engine.manifestToJson(manifest)
        val assertions = json.getJSONArray("assertions")
        val aiActions = (0 until assertions.length())
            .map { assertions.getJSONObject(it) }
            .first { it.getString("label") == "c2pa.actions" }
            .getJSONObject("data")
            .getJSONArray("actions")

        assertEquals("com.novacut.c2pa-manifest.v1", json.getString("schema"))
        assertEquals("NovaCut/3.74.9", json.getString("claimGenerator"))
        assertEquals(C2paExportEngine.SigningMode.ANDROID_KEYSTORE.name, json.getString("signingMode"))
        assertEquals("c2pa.edited", aiActions.getJSONObject(0).getString("action"))
        assertEquals(
            "compositeWithTrainedAlgorithmicMedia",
            aiActions.getJSONObject(0).getString("digitalSourceType")
        )
    }

    @Test
    fun isAvailable_returnsFalseWhenNoC2paLibraryOnClasspath() {
        assertEquals(false, engine.isAvailable())
    }

    @Test
    fun signAndEmbed_returnsUnavailableWhenLibraryAbsent() {
        runBlocking {
            val manifest = engine.buildManifest(
                projectTitle = "Project",
                novaCutVersionName = "3.74.9",
                signingMode = C2paExportEngine.SigningMode.ANDROID_KEYSTORE,
                ledger = emptyList(),
                exporterCreationTimeMs = 0L
            )
            val result = engine.signAndEmbed(manifest, outputPath = "/tmp/foo.mp4")
            assertTrue(result is C2paExportEngine.SignResult.Unavailable)
        }
    }

    @Test
    fun signAndEmbed_rejectsBlankPath() {
        val manifest = engine.buildManifest(
            projectTitle = "Project",
            novaCutVersionName = "3.74.9",
            signingMode = C2paExportEngine.SigningMode.ANDROID_KEYSTORE,
            ledger = emptyList(),
            exporterCreationTimeMs = 0L
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { engine.signAndEmbed(manifest, outputPath = "") }
        }
    }
}
