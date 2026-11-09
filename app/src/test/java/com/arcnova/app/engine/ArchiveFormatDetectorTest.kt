package com.arcnova.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveFormatDetectorTest {
    @Test fun detectsSpecialistCompressionExtensionsAsHints() {
        assertEquals(ArchiveFormat.WINRK, ArchiveFormatDetector.detect("backup.rk").format)
        assertEquals(ArchiveFormat.KGB, ArchiveFormatDetector.detect("archive.kgb").format)
        assertEquals(ArchiveFormat.UHARC, ArchiveFormatDetector.detect("data.uha").format)
        assertEquals(ArchiveFormat.ZPAQ, ArchiveFormatDetector.detect("snapshot.zpaq").format)
        assertEquals(ArchiveFormat.DGCA, ArchiveFormatDetector.detect("data.dgc").format)
        assertEquals(ArchiveFormat.STUFFIT, ArchiveFormatDetector.detect("legacy.sitx").format)
    }

    @Test fun detectsGamePackageExtensionsWithoutClaimingContentValidation() {
        assertEquals(ArchiveFormat.UNREAL_PAK, ArchiveFormatDetector.detect("pakchunk0-Windows.pak").format)
        assertEquals(ArchiveFormat.UNREAL_IOSTORE, ArchiveFormatDetector.detect("global.utoc").format)
        assertEquals(ArchiveFormat.UNREAL_IOSTORE, ArchiveFormatDetector.detect("global.ucas").format)
        assertEquals(ArchiveFormat.UNITY_ASSET_BUNDLE, ArchiveFormatDetector.detect("sharedassets.bundle").format)
        assertEquals(ArchiveFormat.SOURCE_VPK, ArchiveFormatDetector.detect("pak01_dir.vpk").format)
        assertEquals(ArchiveFormat.XP3, ArchiveFormatDetector.detect("data.xp3").format)
        assertEquals(ArchiveFormat.RPA, ArchiveFormatDetector.detect("archive.rpa").format)
    }

    @Test fun reportsExtensionHintConfidenceAndKeepsUnknownFilesUnknown() {
        val result = ArchiveFormatDetector.detect("unknown.kgb")
        assertEquals(ArchiveDetection.Confidence.EXTENSION_HINT, result.confidence)
        assertEquals(ArchiveFormat.UNKNOWN, ArchiveFormatDetector.detect("unknown.file").format)
        assertTrue(ArchiveFormatDetector.detect("unknown.file").confidence == ArchiveDetection.Confidence.UNKNOWN)
    }
}
