package com.novacut.editor.engine

import android.graphics.Bitmap
import java.io.OutputStream

/**
 * Streaming GIF89a encoder for sampled editor frames.
 *
 * The encoder writes one frame at a time. It deliberately owns no frame list;
 * callers can recycle each input bitmap as soon as [addFrame] returns.
 */
internal class GifStreamEncoder(
    private val output: OutputStream,
    logicalWidth: Int,
    logicalHeight: Int,
    delayMs: Int,
) {
    private val logicalWidth = logicalWidth.coerceIn(1, 0xFFFF)
    private val logicalHeight = logicalHeight.coerceIn(1, 0xFFFF)
    private val delayCentiseconds = (delayMs / 10).coerceAtLeast(1)
    private var frameCount = 0

    init {
        writeHeader()
    }

    fun addFrame(frame: Bitmap) {
        require(frame.width in 1..logicalWidth) { "GIF frame width exceeds logical screen" }
        require(frame.height in 1..logicalHeight) { "GIF frame height exceeds logical screen" }
        writeFrame(frame)
        frameCount++
    }

    fun finish() {
        check(frameCount > 0) { "GIF encoder received no frames" }
        output.write(0x3B)
        output.flush()
    }

    private fun writeHeader() {
        output.write("GIF89a".toByteArray())
        output.write(logicalWidth and 0xFF)
        output.write((logicalWidth shr 8) and 0xFF)
        output.write(logicalHeight and 0xFF)
        output.write((logicalHeight shr 8) and 0xFF)
        output.write(0x00) // no global color table
        output.write(0x00) // background color
        output.write(0x00) // pixel aspect ratio

        // Netscape extension for looping.
        output.write(0x21)
        output.write(0xFF)
        output.write(0x0B)
        output.write("NETSCAPE2.0".toByteArray())
        output.write(0x03)
        output.write(0x01)
        output.write(0x00)
        output.write(0x00)
        output.write(0x00)
    }

    private fun writeFrame(frame: Bitmap) {
        val pixels = IntArray(frame.width * frame.height)
        frame.getPixels(pixels, 0, frame.width, 0, 0, frame.width, frame.height)

        // Quantize to 12-bit RGB, then keep the first 256 colors in encounter order.
        val colorMap = HashMap<Int, Int>(256)
        val palette = ArrayList<Int>(256)
        for (pixel in pixels) {
            val quantized = quantizedPixel(pixel)
            if (!colorMap.containsKey(quantized) && palette.size < 256) {
                colorMap[quantized] = palette.size
                palette.add(pixel and 0x00FFFFFF)
            }
        }
        val paletteColors = palette.toList()
        while (palette.size < 256) palette.add(0)

        output.write(0x21)
        output.write(0xF9)
        output.write(0x04)
        // Disposal method 2 restores the background so smaller frames do not ghost.
        output.write(0x08)
        output.write(delayCentiseconds and 0xFF)
        output.write((delayCentiseconds shr 8) and 0xFF)
        output.write(0x00)
        output.write(0x00)

        output.write(0x2C)
        output.write(0x00); output.write(0x00)
        output.write(0x00); output.write(0x00)
        output.write(frame.width and 0xFF); output.write((frame.width shr 8) and 0xFF)
        output.write(frame.height and 0xFF); output.write((frame.height shr 8) and 0xFF)
        output.write(0x87) // local color table, 256 entries
        for (color in palette) {
            output.write((color shr 16) and 0xFF)
            output.write((color shr 8) and 0xFF)
            output.write(color and 0xFF)
        }

        val indexedPixels = ByteArray(pixels.size)
        val nearestColorMap = HashMap<Int, Int>()
        for (i in pixels.indices) {
            val quantized = quantizedPixel(pixels[i])
            val paletteIndex = colorMap[quantized] ?: nearestColorMap.getOrPut(quantized) {
                nearestGifPaletteIndex(quantizedGifRgb(quantized), paletteColors)
            }
            indexedPixels[i] = paletteIndex.toByte()
        }
        writeGifLzw(output, indexedPixels, minCodeSize = 8)
    }

    private fun quantizedPixel(pixel: Int): Int {
        val rgb = pixel and 0x00FFFFFF
        return ((rgb shr 16 and 0xF0) shl 8) or
            ((rgb shr 8) and 0xF0) or
            ((rgb and 0xF0) shr 4)
    }
}

/** Encode one indexed GIF image using an integer-keyed prefix/suffix table. */
internal fun writeGifLzw(output: OutputStream, pixels: ByteArray, minCodeSize: Int) {
    val normalizedMinCodeSize = minCodeSize.coerceIn(2, 8)
    output.write(normalizedMinCodeSize)
    val clearCode = 1 shl normalizedMinCodeSize
    val eoiCode = clearCode + 1
    val table = IntKeyedGifCodeTable()
    val subBlocks = GifSubBlockWriter(output)
    var codeSize = normalizedMinCodeSize + 1
    var nextCode = eoiCode + 1

    fun resetTable() {
        table.clear()
        codeSize = normalizedMinCodeSize + 1
        nextCode = eoiCode + 1
    }

    subBlocks.writeBits(clearCode, codeSize)
    if (pixels.isEmpty()) {
        subBlocks.writeBits(eoiCode, codeSize)
        subBlocks.finish()
        return
    }

    var currentCode = pixels[0].toInt() and 0xFF
    for (i in 1 until pixels.size) {
        val nextByte = pixels[i].toInt() and 0xFF
        val combinedCode = table.get(currentCode, nextByte)
        if (combinedCode >= 0) {
            currentCode = combinedCode
            continue
        }

        subBlocks.writeBits(currentCode, codeSize)
        if (nextCode < 4096) {
            table.put(currentCode, nextByte, nextCode++)
            // The encoder has one dictionary entry ahead of a decoder because
            // the decoder cannot add the first emitted pair until it sees the
            // following code. Delay the width increase until the next code.
            if (nextCode > (1 shl codeSize) && codeSize < 12) {
                codeSize++
            }
        } else {
            subBlocks.writeBits(clearCode, codeSize)
            resetTable()
        }
        currentCode = nextByte
    }

    subBlocks.writeBits(currentCode, codeSize)
    subBlocks.writeBits(eoiCode, codeSize)
    subBlocks.finish()
}

private class IntKeyedGifCodeTable {
    private companion object {
        const val CAPACITY = 8192
        const val EMPTY_KEY = -1
    }

    private val keys = IntArray(CAPACITY) { EMPTY_KEY }
    private val values = IntArray(CAPACITY)
    private val mask = CAPACITY - 1

    fun clear() {
        java.util.Arrays.fill(keys, EMPTY_KEY)
    }

    fun get(prefixCode: Int, suffixByte: Int): Int {
        val key = (prefixCode shl 8) or suffixByte
        var slot = slotFor(key)
        while (true) {
            val storedKey = keys[slot]
            if (storedKey == EMPTY_KEY) return -1
            if (storedKey == key) return values[slot]
            slot = (slot + 1) and mask
        }
    }

    fun put(prefixCode: Int, suffixByte: Int, code: Int) {
        val key = (prefixCode shl 8) or suffixByte
        var slot = slotFor(key)
        while (keys[slot] != EMPTY_KEY && keys[slot] != key) {
            slot = (slot + 1) and mask
        }
        keys[slot] = key
        values[slot] = code
    }

    private fun slotFor(key: Int): Int =
        (key * -1_640_531_527 ushr 19) and mask
}

private class GifSubBlockWriter(private val output: OutputStream) {
    private val block = ByteArray(255)
    private var blockSize = 0
    private var bitBuffer = 0
    private var bitCount = 0

    fun writeBits(code: Int, bits: Int) {
        bitBuffer = bitBuffer or (code shl bitCount)
        bitCount += bits
        while (bitCount >= 8) {
            writeByte(bitBuffer and 0xFF)
            bitBuffer = bitBuffer ushr 8
            bitCount -= 8
        }
    }

    fun finish() {
        if (bitCount > 0) {
            writeByte(bitBuffer and 0xFF)
            bitBuffer = 0
            bitCount = 0
        }
        flushBlock()
        output.write(0x00)
    }

    private fun writeByte(value: Int) {
        block[blockSize++] = value.toByte()
        if (blockSize == block.size) flushBlock()
    }

    private fun flushBlock() {
        if (blockSize == 0) return
        output.write(blockSize)
        output.write(block, 0, blockSize)
        blockSize = 0
    }
}
