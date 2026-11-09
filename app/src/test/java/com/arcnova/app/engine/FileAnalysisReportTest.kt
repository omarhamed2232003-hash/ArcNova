package com.arcnova.app.engine

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class FileAnalysisReportTest {
    @Test fun reportsSignatureSizeAndSha256() {
        val bytes = "hello".toByteArray()
        val report = FileAnalysisAnalyzer.analyze("note.txt", ByteArrayInputStream(bytes))
        assertEquals(5L, report.sizeBytes)
        assertNull(report.signature)
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", report.sha256)
    }

    @Test fun flagsExtensionSignatureMismatch() {
        val png = byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a)
        val report = FileAnalysisAnalyzer.analyze("photo.zip", ByteArrayInputStream(png))
        assertEquals("png", report.signature?.id)
        assertEquals(false, report.extensionMatchesSignature)
    }

    @Test fun acceptsEquivalentExtensions() {
        val jpeg = byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte())
        val report = FileAnalysisAnalyzer.analyze("photo.jpeg", ByteArrayInputStream(jpeg))
        assertEquals(true, report.extensionMatchesSignature)
    }

    @Test fun hiddenAndTrailingDotNamesHaveNoExtension() {
        assertNull(FileAnalysisAnalyzer.analyze(".hidden", ByteArrayInputStream(byteArrayOf())).extension)
        assertNull(FileAnalysisAnalyzer.analyze("README.", ByteArrayInputStream(byteArrayOf())).extension)
        assertEquals("gz", FileAnalysisAnalyzer.analyze("backup.tar.gz", ByteArrayInputStream(byteArrayOf())).extension)
    }

    @Test fun zeroLengthBulkReadFallsBackWithoutLosingBytes() {
        val source = object : ByteArrayInputStream("abc".toByteArray()) {
            private var returnZero = true
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (returnZero) {
                    returnZero = false
                    return 0
                }
                return super.read(b, off, len)
            }
        }
        val report = FileAnalysisAnalyzer.analyze("data.bin", source)
        assertEquals(3L, report.sizeBytes)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", report.sha256)
    }
}
