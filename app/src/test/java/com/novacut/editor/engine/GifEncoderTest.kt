package com.novacut.editor.engine

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GifEncoderTest {

    @Test
    fun lzwUsesBoundedSubBlocksAndRoundTripsAfterCodeTableGrowth() {
        val pixels = ByteArray(12_000) { index ->
            ((index * 37 + index / 7) and 0xFF).toByte()
        }
        val encoded = ByteArrayOutputStream()

        writeGifLzw(encoded, pixels, minCodeSize = 8)

        val bytes = encoded.toByteArray()
        assertEquals(8, bytes.first().toInt())
        val compressed = ByteArrayOutputStream()
        var offset = 1
        while (true) {
            val blockSize = bytes[offset++].toInt() and 0xFF
            if (blockSize == 0) break
            assertTrue(blockSize <= 255)
            compressed.write(bytes, offset, blockSize)
            offset += blockSize
        }
        assertArrayEquals(pixels, decodeGifLzw(compressed.toByteArray(), minCodeSize = 8))
    }

    private fun decodeGifLzw(data: ByteArray, minCodeSize: Int): ByteArray {
        val clearCode = 1 shl minCodeSize
        val eoiCode = clearCode + 1
        val dictionary = arrayOfNulls<ByteArray>(4096)
        var codeSize = minCodeSize + 1
        var nextCode = eoiCode + 1
        var bitOffset = 0
        var previous: ByteArray? = null
        val decoded = ByteArrayOutputStream()

        fun readCode(): Int {
            var value = 0
            for (bit in 0 until codeSize) {
                val absoluteBit = bitOffset++
                if ((data[absoluteBit / 8].toInt() ushr (absoluteBit % 8) and 1) != 0) {
                    value = value or (1 shl bit)
                }
            }
            return value
        }

        while (true) {
            val code = readCode()
            when {
                code == clearCode -> {
                    java.util.Arrays.fill(dictionary, null)
                    codeSize = minCodeSize + 1
                    nextCode = eoiCode + 1
                    previous = null
                }
                code == eoiCode -> return decoded.toByteArray()
                else -> {
                    val entry = when {
                        code < clearCode -> byteArrayOf(code.toByte())
                        code < nextCode -> requireNotNull(dictionary[code])
                        code == nextCode && previous != null -> previous + previous.first()
                        else -> error("Invalid GIF LZW code $code")
                    }
                    decoded.write(entry)
                    previous?.let { prior ->
                        if (nextCode < 4096) {
                            dictionary[nextCode++] = prior + entry.first()
                            if (nextCode >= (1 shl codeSize) && codeSize < 12) {
                                codeSize++
                            }
                        }
                    }
                    previous = entry
                }
            }
        }
    }
}
