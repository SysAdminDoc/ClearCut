package com.novacut.editor.engine

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

fun readUtf8WithByteLimit(input: InputStream, maxBytes: Long): String {
    val buffer = ByteArrayOutputStream()
    copyWithLimit(input, buffer, maxBytes)
    return buffer.toString(Charsets.UTF_8.name())
}

fun copyWithLimit(
    input: InputStream,
    output: OutputStream,
    maxBytes: Long
): Long {
    require(maxBytes >= 0L) { "maxBytes must be non-negative" }

    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var totalBytes = 0L

    while (true) {
        val read = input.read(buffer)
        if (read == -1) break

        totalBytes += read
        if (totalBytes > maxBytes) {
            throw IOException("Input exceeds byte limit of $maxBytes")
        }

        output.write(buffer, 0, read)
    }

    return totalBytes
}
