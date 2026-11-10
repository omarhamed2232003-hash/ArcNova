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
        assertEquals("ole-compound", FileSignatureDetector.detect(
            byteArrayOf(0xd0.toByte(), 0xcf.toByte(), 0x11, 0xe0.toByte(), 0xa1.toByte(), 0xb1.toByte(), 0x1a, 0xe1.toByte())
        )!!.id)
    }

    @Test fun recognizesExecutableDatabaseAndMediaHeaders() {
        assertEquals("pe-candidate", FileSignatureDetector.detect(byteArrayOf(0x4d,0x5a))!!.id)
        assertEquals("elf", FileSignatureDetector.detect(byteArrayOf(0x7f,0x45,0x4c,0x46))!!.id)
        assertEquals("sqlite", FileSignatureDetector.detect("SQLite format 3\u0000".toByteArray())!!.id)
        assertEquals("flac", FileSignatureDetector.detect("fLaC".toByteArray())!!.id)
        assertEquals("mp4", FileSignatureDetector.detect(byteArrayOf(0,0,0,0)+ "ftyp".toByteArray())!!.id)
    }

    @Test fun recognizesGameAndInstallerContainerHeaders() {
        assertEquals("source-vpk", FileSignatureDetector.detect(byteArrayOf(0x34, 0x12, 0xaa.toByte(), 0x55))!!.id)
        assertEquals("xp3", FileSignatureDetector.detect(
            byteArrayOf(0x58, 0x50, 0x33, 0x0d, 0x0a, 0x20, 0x0a, 0x1a, 0x8b.toByte(), 0x67, 0x01)
        )!!.id)
        assertEquals("unityfs", FileSignatureDetector.detect("UnityFS\u0000".toByteArray())!!.id)
        assertEquals("unityweb", FileSignatureDetector.detect("UnityWeb\u0000".toByteArray())!!.id)
        assertEquals("unityraw", FileSignatureDetector.detect("UnityRaw\u0000".toByteArray())!!.id)
        assertEquals("unityarchive", FileSignatureDetector.detect("UnityArchive\u0000".toByteArray())!!.id)
        assertEquals("unity-asset-bundle", FileSignatureDetector.detect("UnityWebData1.0".toByteArray())!!.id)
        assertEquals("renpy-rpa", FileSignatureDetector.detect("RPA-3.0 0000000000000000 00000000\n".toByteArray())!!.id)
        assertEquals("renpy-rpa", FileSignatureDetector.detect("RPA-2.0 0000000000000000\n".toByteArray())!!.id)
        assertEquals("cab", FileSignatureDetector.detect("MSCF".toByteArray())!!.id)
    }

    @Test fun recognizesTarAndIso9660AtTheirDefinedHeaderOffsets() {
        val tar = ByteArray(263)
        "ustar".toByteArray().copyInto(tar, 257)
        assertEquals("tar", FileSignatureDetector.detect(tar)!!.id)

        val iso = ByteArray(0x8007)
        iso[0x8000] = 1
        "CD001".toByteArray().copyInto(iso, 0x8001)
        iso[0x8006] = 1
        assertEquals("iso9660", FileSignatureDetector.detect(iso)!!.id)
    }

    @Test fun unknownAndEmptyHeadersDoNotProduceFalseMatches() {
        assertNull(FileSignatureDetector.detect(byteArrayOf()))
        assertNull(FileSignatureDetector.detect("not a known signature".toByteArray()))
        assertNull(FileSignatureDetector.detect(byteArrayOf(0x34, 0x12, 0xaa.toByte())))
        assertNull(FileSignatureDetector.detect("UnityF".toByteArray()))
        assertNull(FileSignatureDetector.detect(ByteArray(0x8006)))
    }

    @Test fun streamDetectionIsBoundedAndDoesNotCloseCallerStream() {
        val bytes = byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a) + ByteArray(128)
        val stream = TrackingInputStream(bytes)
        assertEquals("png", FileSignatureDetector.detect(stream, 8)!!.id)
        assertEquals(8, stream.bytesRead)
        assertTrue(stream.read() >= 0)
    }

    @Test fun streamDetectionCanReachOffsetSignaturesWithinItsBound() {
        val iso = ByteArray(0x8007)
        iso[0x8000] = 1
        "CD001".toByteArray().copyInto(iso, 0x8001)
        iso[0x8006] = 1
        val stream = TrackingInputStream(iso)
        assertEquals("iso9660", FileSignatureDetector.detect(stream)!!.id)
        assertEquals(iso.size, stream.bytesRead)
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
