package com.arcnova.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ArchiveSignatureDetectorTest {
    @Test fun recognizesZipSignatures() {
        assertEquals(ArchiveFormat.ZIP, ArchiveSignatureDetector.detect(byteArrayOf(0x50,0x4b,0x03,0x04)))
        assertEquals(ArchiveFormat.ZIP, ArchiveSignatureDetector.detect(byteArrayOf(0x50,0x4b,0x05,0x06)))
    }
    @Test fun recognizesSevenZipAndRar() {
        assertEquals(ArchiveFormat.SEVEN_ZIP, ArchiveSignatureDetector.detect(byteArrayOf(0x37,0x7a,0xbc.toByte(),0xaf.toByte(),0x27,0x1c)))
        assertEquals(ArchiveFormat.RAR, ArchiveSignatureDetector.detect(byteArrayOf(0x52,0x61,0x72,0x21,0x1a,0x07,0x00)))
        assertEquals(ArchiveFormat.RAR, ArchiveSignatureDetector.detect(byteArrayOf(0x52,0x61,0x72,0x21,0x1a,0x07,0x01,0x00)))
    }
    @Test fun recognizesStreamCompressionSignatures() {
        assertEquals(ArchiveFormat.GZIP, ArchiveSignatureDetector.detect(byteArrayOf(0x1f,0x8b.toByte())))
        assertEquals(ArchiveFormat.BZIP2, ArchiveSignatureDetector.detect("BZh".toByteArray()))
        assertEquals(ArchiveFormat.XZ, ArchiveSignatureDetector.detect(byteArrayOf(0xfd.toByte(),0x37,0x7a,0x58,0x5a,0x00)))
        assertEquals(ArchiveFormat.ZSTANDARD, ArchiveSignatureDetector.detect(byteArrayOf(0x28,0xb5.toByte(),0x2f,0xfd.toByte())))
    }
    @Test fun recognizesTarHeaderAndRejectsUnknownOrShortInput() {
        val tar = ByteArray(512)
        "ustar".toByteArray().copyInto(tar, 257)
        assertEquals(ArchiveFormat.TAR, ArchiveSignatureDetector.detect(tar))
        assertEquals(ArchiveFormat.UNKNOWN, ArchiveSignatureDetector.detect(byteArrayOf(1,2,3)))
        assertEquals(ArchiveFormat.UNKNOWN, ArchiveSignatureDetector.detect(byteArrayOf()))
    }
}
