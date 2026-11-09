package com.arcnova.app.engine

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class FileComparisonEngineTest {
    @Test fun detectsIdenticalContentWithDifferentNames() {
        val result = FileComparisonEngine.compare(
            "one.bin", ByteArrayInputStream("same".toByteArray()),
            "two.dat", ByteArrayInputStream("same".toByteArray())
        )
        assertTrue(result.sameSize)
        assertTrue(result.sameContent)
    }

    @Test fun detectsDifferentSizes() {
        val result = FileComparisonEngine.compare(
            "one.bin", ByteArrayInputStream(byteArrayOf(1)),
            "two.bin", ByteArrayInputStream(byteArrayOf(1, 2))
        )
        assertFalse(result.sameSize)
        assertFalse(result.sameContent)
    }

    @Test fun detectsDifferentContentWithSameSize() {
        val result = FileComparisonEngine.compare(
            "one.bin", ByteArrayInputStream(byteArrayOf(1, 2)),
            "two.bin", ByteArrayInputStream(byteArrayOf(1, 3))
        )
        assertTrue(result.sameSize)
        assertFalse(result.sameContent)
    }

    @Test fun reportsMatchingDetectedFormats() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        val result = FileComparisonEngine.compare(
            "a.png", ByteArrayInputStream(png),
            "b.png", ByteArrayInputStream(png)
        )
        assertEquals(true, result.sameDetectedFormat)
    }

    @Test fun leavesBothStreamsOpen() {
        var leftClosed = false
        var rightClosed = false
        val left = object : ByteArrayInputStream(byteArrayOf(1)) {
            override fun close() { leftClosed = true; super.close() }
        }
        val right = object : ByteArrayInputStream(byteArrayOf(1)) {
            override fun close() { rightClosed = true; super.close() }
        }
        FileComparisonEngine.compare("a", left, "b", right)
        assertFalse(leftClosed)
        assertFalse(rightClosed)
    }
}
