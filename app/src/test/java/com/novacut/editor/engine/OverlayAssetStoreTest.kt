package com.novacut.editor.engine

import com.novacut.editor.model.ImageOverlayType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayAssetStoreTest {

    @Test
    fun parseBundledStickerUri_acceptsShelfUris() {
        val ref = OverlayAssetStore.parseBundledStickerUri(
            "content://com.novacut.editor.stickers/emoji/0"
        )
        assertNotNull(ref)
        assertEquals("emoji", ref!!.category)
        assertEquals(0, ref.index)
    }

    @Test
    fun parseBundledStickerUri_rejectsUnknownAuthorityCategoryAndIndex() {
        assertNull(OverlayAssetStore.parseBundledStickerUri("content://x/emoji/0"))
        assertNull(OverlayAssetStore.parseBundledStickerUri("content://com.novacut.editor.stickers/bad/0"))
        assertNull(OverlayAssetStore.parseBundledStickerUri("content://com.novacut.editor.stickers/emoji/12"))
        assertNull(OverlayAssetStore.parseBundledStickerUri("content://com.novacut.editor.stickers/emoji/-1"))
    }

    @Test
    fun decideImport_rejectsExplicitGifOverlayRequests() {
        val decision = OverlayAssetStore.decideImport(
            mimeType = "image/png",
            fileName = "sticker.png",
            requestedType = ImageOverlayType.GIF,
        )
        assertFalse(decision.accepted)
        assertEquals(
            OverlayAssetRejectionReason.ANIMATED_GIF_UNSUPPORTED,
            decision.rejectionReason,
        )
    }

    @Test
    fun decideImport_rejectsGifMimeOrExtension() {
        val byMime = OverlayAssetStore.decideImport(
            mimeType = "image/gif",
            fileName = "sticker.png",
            requestedType = ImageOverlayType.STICKER,
        )
        val byExtension = OverlayAssetStore.decideImport(
            mimeType = null,
            fileName = "sticker.GIF",
            requestedType = ImageOverlayType.IMAGE,
        )
        assertFalse(byMime.accepted)
        assertFalse(byExtension.accepted)
        assertEquals(OverlayAssetRejectionReason.ANIMATED_GIF_UNSUPPORTED, byMime.rejectionReason)
        assertEquals(OverlayAssetRejectionReason.ANIMATED_GIF_UNSUPPORTED, byExtension.rejectionReason)
    }

    @Test
    fun decideImport_acceptsStillImageAndNormalizesExtension() {
        val decision = OverlayAssetStore.decideImport(
            mimeType = "image/jpeg",
            fileName = "picked-file",
            requestedType = ImageOverlayType.STICKER,
        )
        assertTrue(decision.accepted)
        assertEquals(OverlayAssetKind.STILL_IMAGE, decision.kind)
        assertEquals("jpg", decision.extension)
    }
}
