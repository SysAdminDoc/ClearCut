package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PluginRegistryTest {

    @Test
    fun template_extensionDetectedCaseInsensitive() {
        assertEquals(
            PluginRegistry.Kind.TEMPLATE,
            PluginRegistry.kindForFileName("VlogIntro.clearcut-template")
        )
        assertEquals(
            PluginRegistry.Kind.TEMPLATE,
            PluginRegistry.kindForFileName("VLOGINTRO.CLEARCUT-TEMPLATE")
        )
    }

    @Test
    fun effectPack_ncfxExtension() {
        assertEquals(
            PluginRegistry.Kind.EFFECT_PACK,
            PluginRegistry.kindForFileName("ColorPop.ncfx")
        )
    }

    @Test
    fun stylePack_ncstyleExtension() {
        assertEquals(
            PluginRegistry.Kind.STYLE_PACK,
            PluginRegistry.kindForFileName("ViralCaption.ncstyle")
        )
    }

    @Test
    fun lut_cubeAndThreeDl() {
        assertEquals(
            PluginRegistry.Kind.LUT_CUBE,
            PluginRegistry.kindForFileName("teal_orange.cube")
        )
        assertEquals(
            PluginRegistry.Kind.LUT_3DL,
            PluginRegistry.kindForFileName("Filmic.3dl")
        )
    }

    @Test
    fun openFxDescriptor_ncfxdExtension() {
        assertEquals(
            PluginRegistry.Kind.OPENFX_DESCRIPTOR,
            PluginRegistry.kindForFileName("blur.ncfxd")
        )
    }

    @Test
    fun longerExtensionsWinOverShorterFalsePositives() {
        // `.ncfx` would substring-match inside `.ncfxd`. Verify the
        // longest-extension-first sort returns the right Kind.
        assertEquals(
            PluginRegistry.Kind.OPENFX_DESCRIPTOR,
            PluginRegistry.kindForFileName("X.ncfxd")
        )
        assertEquals(
            PluginRegistry.Kind.EFFECT_PACK,
            PluginRegistry.kindForFileName("X.ncfx")
        )
    }

    @Test
    fun unknownExtension_returnsNull() {
        assertNull(PluginRegistry.kindForFileName("photo.jpg"))
        assertNull(PluginRegistry.kindForFileName("random.txt"))
        assertNull(PluginRegistry.kindForFileName(""))
    }

    @Test
    fun whitespaceAndCaseHandled() {
        assertEquals(
            PluginRegistry.Kind.TEMPLATE,
            PluginRegistry.kindForFileName("  Spaces.clearcut-template  ")
        )
    }

    @Test
    fun kindForFile_acceptsFileObject() {
        assertEquals(
            PluginRegistry.Kind.LUT_CUBE,
            PluginRegistry.kindForFile(File("/tmp/lookup/teal.cube"))
        )
    }

    @Test
    fun allSupportedExtensions_matchesKindCount() {
        val exts = PluginRegistry.allSupportedExtensions()
        assertEquals(PluginRegistry.Kind.entries.size, exts.size)
        // Every extension starts with a dot.
        assertTrue(exts.all { it.startsWith(".") })
        // All extensions are unique.
        assertEquals(exts.size, exts.toSet().size)
    }

    @Test
    fun shareMimeTypeFor_returnsKindMime() {
        assertEquals(
            "application/json",
            PluginRegistry.shareMimeTypeFor(PluginRegistry.Kind.OPENFX_DESCRIPTOR)
        )
        assertEquals(
            "text/plain",
            PluginRegistry.shareMimeTypeFor(PluginRegistry.Kind.LUT_CUBE)
        )
    }
}
