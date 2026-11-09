package com.arcnova.app.engine

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class FileSignatureDetectorTest {
    @Test fun recognizesCommonImagesAndDocuments() {
        assertEquals("png", FileSignatureDetector.detect(byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a))!!.id)
        assertEquals("jpeg", FileSignatureDetector.detect(byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte()))!!.id)
        assertEquals("gif", FileSignatureDetector.detect("GIF89a".toByteArray())!!.id)
        assertEquals("pdf", FileSignatureDetector.detect("%PDF-1.7".toByteArray())!!.id)
    }

    @Test fun recognizesExecutableDatabaseAndMediaHeaders() {
        assertEquals("pe-candidate", FileSignatureDetector.detect(byteArrayOf(0x4d,0x5a))!!.id)
        assertEquals("elf", FileSignatureDetector.detect(byteArrayOf(0x7f,0x45,0x4c,0x46))!!.id)
        assertEquals("sqlite", FileSignatureDetector.detect("SQLite format 3\u0000".toByteArray())!!.id)
        assertEquals("flac", FileSignatureDetector.detect("fLaC".toByteArray())!!.id)
        assertEquals("mp4", FileSignatureDetector.detect(byteArrayOf(0,0,0,0)+ "ftyp".toByteArray())!!.id)
    }

    @Test fun unknownAndEmptyHeadersDoNotProduceFalseMatches() {
        assertNull(FileSignatureDetector.detect(byteArrayOf()))
        assertNull(FileSignatureDetector.detect("not a known signature".toByteArray()))
    }

    @Test fun streamDetectionIsBoundedAndDoesNotCloseCallerStream() {
        val bytes = byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a) + ByteArray(128)
        val stream = TrackingInputStream(bytes)
        assertEquals("png", FileSignatureDetector.detect(stream, 8)!!.id)
        assertEquals(8, stream.bytesRead)
        assertTrue(stream.read() >= 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveHeaderLimit() {
        FileSignatureDetector.detect(ByteArrayInputStream(byteArrayOf(1)), 0)
    }

    private class TrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var bytesRead = 0
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val count = super.read(b, off, len)
            if (count > 0) bytesRead += count
            return count
        }
        override fun read(): Int {
            val value = super.read()
            if (value >= 0) bytesRead++
            return value
        }
    }
}
