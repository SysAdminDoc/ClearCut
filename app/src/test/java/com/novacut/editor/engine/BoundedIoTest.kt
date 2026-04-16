package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class BoundedIoTest {

    @Test
    fun readUtf8WithByteLimit_readsUtf8TextWithinLimit() {
        val input = ByteArrayInputStream("NovaCut".toByteArray(Charsets.UTF_8))

        val result = readUtf8WithByteLimit(input, maxBytes = 16)

        assertEquals("NovaCut", result)
    }

    @Test
    fun readUtf8WithByteLimit_throwsWhenLimitExceeded() {
        val input = ByteArrayInputStream("0123456789".toByteArray(Charsets.UTF_8))

        val error = runCatching {
            readUtf8WithByteLimit(input, maxBytes = 5)
        }.exceptionOrNull()

        assertTrue(error is IOException)
    }

    @Test
    fun copyWithLimit_copiesBytesAndReturnsCount() {
        val input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))
        val output = ByteArrayOutputStream()

        val copied = copyWithLimit(input, output, maxBytes = 4)

        assertEquals(4L, copied)
        assertEquals(listOf<Byte>(1, 2, 3, 4), output.toByteArray().toList())
    }

    @Test
    fun copyWithLimit_stopsWhenLimitExceeded() {
        val input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5, 6))
        val output = ByteArrayOutputStream()

        val error = runCatching {
            copyWithLimit(input, output, maxBytes = 5)
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertTrue(output.size() <= 5)
    }
}
