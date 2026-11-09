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
}
