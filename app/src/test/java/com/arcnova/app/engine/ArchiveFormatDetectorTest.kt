package com.arcnova.app.engine

import org.junit.Assert.*
import org.junit.Test

class ArchiveFormatDetectorTest {
    @Test fun detectsCommonFormatsCaseInsensitively() {
        assertEquals(ArchiveFormat.ZIP, ArchiveFormatDetector.detect("Backup.ZIP").format)
        assertEquals(ArchiveFormat.SEVEN_ZIP, ArchiveFormatDetector.detect("game.7z").format)
        assertEquals(ArchiveFormat.RAR, ArchiveFormatDetector.detect("part.rar").format)
        assertEquals(ArchiveFormat.FREEARC, ArchiveFormatDetector.detect("setup.arc").format)
    }
    @Test fun detectsNumberedParts() {
        val zip = ArchiveFormatDetector.detect("backup.zip.002")
        assertEquals(ArchiveFormat.ZIP, zip.format); assertTrue(zip.isMultipart); assertEquals(2, zip.partNumber)
        val seven = ArchiveFormatDetector.detect("data.7z.001")
        assertEquals(ArchiveFormat.SEVEN_ZIP, seven.format); assertTrue(seven.isMultipart); assertEquals(1, seven.partNumber)
    }
    @Test fun detectsBothRarMultipartStyles() {
        val modern = ArchiveFormatDetector.detect("archive.part03.rar")
        assertEquals(ArchiveFormat.RAR, modern.format); assertTrue(modern.isMultipart); assertEquals(3, modern.partNumber)
        val legacy = ArchiveFormatDetector.detect("archive.r00")
        assertEquals(ArchiveFormat.RAR, legacy.format); assertTrue(legacy.isMultipart); assertEquals(0, legacy.partNumber)
    }
    @Test fun doesNotGuessBinFilesAreArchives() {
        val result = ArchiveFormatDetector.detect("fg-01.bin")
        assertEquals(ArchiveFormat.UNKNOWN, result.format); assertFalse(result.isMultipart)
        assertEquals(ArchiveDetection.Confidence.UNKNOWN, result.confidence)
    }
    @Test fun handlesMissingNames() {
        assertEquals(ArchiveFormat.UNKNOWN, ArchiveFormatDetector.detect(null).format)
        assertEquals(ArchiveFormat.UNKNOWN, ArchiveFormatDetector.detect("  ").format)
    }
}
