package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OpenFxDescriptorTest {

    private val sample = OpenFxDescriptor(
        schemaVersion = 1,
        novaCutEffectId = "gaussian_blur",
        openfxId = "uk.co.thefoundry.OfxImageEffectGaussianBlur",
        displayName = "Gaussian Blur",
        parameters = listOf(
            OpenFxDescriptor.ParameterMapping(
                novaCutName = "radius",
                openfxName = "size",
                novaCutRange = 0.0..50.0,
                openfxRange = 0.0..100.0,
                scale = 2.0,
                offset = 0.0,
                type = "double",
            )
        ),
    )

    private fun near(actual: Double, expected: Double, eps: Double = 1e-9) {
        assertTrue("Expected ~$expected got $actual", abs(actual - expected) < eps)
    }

    // --- parameter conversion math ---

    @Test
    fun toOpenFx_scalesByMappingFactor() {
        val p = sample.parameters.first()
        near(p.toOpenFx(0.0), 0.0)
        near(p.toOpenFx(25.0), 50.0)
        near(p.toOpenFx(50.0), 100.0)
    }

    @Test
    fun fromOpenFx_roundTrips() {
        val p = sample.parameters.first()
        listOf(0.0, 10.0, 25.5, 50.0).forEach { v ->
            near(p.fromOpenFx(p.toOpenFx(v)), v)
        }
    }

    @Test
    fun fromOpenFx_zeroScaleFallsBackToNovaCutStart() {
        val p = OpenFxDescriptor.ParameterMapping(
            novaCutName = "x",
            openfxName = "x",
            novaCutRange = 1.0..5.0,
            openfxRange = 0.0..1.0,
            scale = 0.0,
            offset = 0.0,
        )
        near(p.fromOpenFx(99.0), 1.0)
    }

    // --- serialization round trip ---

    @Test
    fun toJsonAndFromJson_roundTripStructure() {
        val json = sample.toJson()
        val parsed = OpenFxDescriptor.fromJson(json)
        assertNotNull(parsed)
        assertEquals(sample.schemaVersion, parsed!!.schemaVersion)
        assertEquals(sample.novaCutEffectId, parsed.novaCutEffectId)
        assertEquals(sample.openfxId, parsed.openfxId)
        assertEquals(sample.displayName, parsed.displayName)
        assertEquals(sample.parameters.size, parsed.parameters.size)
        val pa = sample.parameters.first()
        val pb = parsed.parameters.first()
        assertEquals(pa.novaCutName, pb.novaCutName)
        assertEquals(pa.openfxName, pb.openfxName)
        assertEquals(pa.scale, pb.scale, 1e-9)
        assertEquals(pa.offset, pb.offset, 1e-9)
        assertEquals(pa.type, pb.type)
        assertEquals(pa.novaCutRange.start, pb.novaCutRange.start, 1e-9)
        assertEquals(pa.novaCutRange.endInclusive, pb.novaCutRange.endInclusive, 1e-9)
    }

    @Test
    fun fromJson_rejectsMalformed() {
        assertNull(OpenFxDescriptor.fromJson("not json"))
        assertNull(OpenFxDescriptor.fromJson("{}"))
        assertNull(OpenFxDescriptor.fromJson("""{"schemaVersion":1}"""))
        assertNull(
            OpenFxDescriptor.fromJson("""{"schemaVersion":1,"novaCutEffectId":"x"}""")
        )
    }

    @Test
    fun fromJson_rejectsUnsupportedSchemaVersion() {
        val futureSchema = """
            {
              "schemaVersion": 99,
              "novaCutEffectId": "x",
              "openfxId": "y",
              "displayName": "z",
              "parameters": []
            }
        """.trimIndent()
        assertNull(OpenFxDescriptor.fromJson(futureSchema))
    }

    @Test
    fun fromJson_skipsInvalidParameterEntries() {
        // Mix of one good + one bad parameter — the good one survives.
        val mixed = """
            {
              "schemaVersion": 1,
              "novaCutEffectId": "x",
              "openfxId": "y",
              "displayName": "z",
              "parameters": [
                { "novaCutName": "a", "openfxName": "b",
                  "novaCutRange": [0, 1], "openfxRange": [0, 100],
                  "scale": 100, "offset": 0, "type": "double" },
                { "novaCutName": "", "openfxName": "missing",
                  "novaCutRange": [0, 1], "openfxRange": [0, 1] }
              ]
            }
        """.trimIndent()
        val parsed = OpenFxDescriptor.fromJson(mixed)
        assertNotNull(parsed)
        assertEquals(1, parsed!!.parameters.size)
        assertEquals("a", parsed.parameters.first().novaCutName)
    }

    @Test
    fun fromJson_rejectsInvertedRange() {
        val inverted = """
            {
              "schemaVersion": 1,
              "novaCutEffectId": "x",
              "openfxId": "y",
              "displayName": "z",
              "parameters": [
                { "novaCutName": "a", "openfxName": "b",
                  "novaCutRange": [5, 0], "openfxRange": [0, 1] }
              ]
            }
        """.trimIndent()
        val parsed = OpenFxDescriptor.fromJson(inverted)
        assertNotNull(parsed)
        // Inverted range entry is skipped; parsed list is empty.
        assertEquals(0, parsed!!.parameters.size)
    }

    @Test
    fun schemaConstantIsCurrent() {
        assertEquals(1, OpenFxDescriptor.CURRENT_SCHEMA_VERSION)
    }
}
