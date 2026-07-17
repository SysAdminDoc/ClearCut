package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * `.3dl` parsing regressed to rejecting essentially every well-formed file:
 * the old header heuristic misread a real mesh header (>3 tokens) as data and
 * skipped a headerless first data row ("0 0 0") as a header — both off-by-one
 * against the size^3 cube check.
 */
class Lut3dlParseTest {

    private fun write(contents: String): File {
        val f = Files.createTempFile("lut", ".3dl").toFile()
        f.writeText(contents)
        return f
    }

    private fun cubeRows(size: Int, max: Int): String {
        // size^3 rows of integer RGB in a 0..max encoding.
        val sb = StringBuilder()
        for (i in 0 until size * size * size) {
            val v = (i % (max + 1))
            sb.append("$v $v $v\n")
        }
        return sb.toString()
    }

    @Test
    fun parsesFileWithMeshHeader() {
        val header = (0..16).joinToString(" ") { (it * 1023 / 16).toString() }
        val lut = LutEngine.parse3dl(write(header + "\n" + cubeRows(17, 1023)))
        assertNotNull("17-point .3dl with mesh header should parse", lut)
        assertEquals(17, lut!!.size)
    }

    @Test
    fun parsesHeaderlessFile() {
        val lut = LutEngine.parse3dl(write(cubeRows(2, 1023)))
        assertNotNull("headerless 2-point .3dl should parse", lut)
        assertEquals(2, lut!!.size)
        // First entry "0 0 0" must be present, not skipped as a header.
        assertEquals(0f, lut.data[0], 0.0001f)
    }

    @Test
    fun rejectsMalformedCube() {
        // 7 rows: not a perfect cube, no valid header.
        assertNull(LutEngine.parse3dl(write("1 2 3\n4 5 6\n7 8 9\n1 2 3\n4 5 6\n7 8 9\n1 2 3\n")))
    }
}
