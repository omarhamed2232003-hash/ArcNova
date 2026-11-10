package com.arcnova.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun detectsZipFirstVolumesAndBlocksSingleFileExtraction() {
        listOf("backup.zip.001" to 1, "backup.z01" to 1, "backup.z001" to 1).forEach { (name, partNumber) ->
            val part = ArchiveFormatDetector.detect(name)

            assertEquals(ArchiveFormat.ZIP, part.format)
            assertTrue(part.isMultipart)
            assertEquals(partNumber, part.partNumber)
            listOf<ArchiveFormat?>(ArchiveFormat.ZIP, null, ArchiveFormat.RAR).forEach { signature ->
                assertFalse(part.supportsZipExtraction(signature))
            }
        }
    }

    @Test fun ordinaryZipIsNotMultipartAndRemainsEligibleForExtraction() {
        val zip = ArchiveFormatDetector.detect("backup.zip")

        assertEquals(ArchiveFormat.ZIP, zip.format)
        assertFalse(zip.isMultipart)
        assertTrue(zip.supportsZipExtraction(null))
        assertTrue(zip.supportsZipExtraction(ArchiveFormat.ZIP))
        assertTrue(zip.supportsZipExtraction(ArchiveFormat.UNKNOWN))

        val unknownExtension = ArchiveFormatDetector.detect("unknown.bin")
        assertEquals(ArchiveFormat.UNKNOWN, unknownExtension.format)
        assertTrue(unknownExtension.supportsZipExtraction(ArchiveFormat.ZIP))
        assertFalse(unknownExtension.supportsZipExtraction(ArchiveFormat.UNKNOWN))
    }

    @Test fun knownNonZipExtensionCannotBeOverriddenByZipSignature() {
        val archive = ArchiveFormatDetector.detect("backup.rar")

        assertEquals(ArchiveFormat.RAR, archive.format)
        assertFalse(archive.supportsZipExtraction(ArchiveFormat.ZIP))
    }

    @Test fun knownZipExtensionIsRejectedWhenHeaderIdentifiesAnotherFormat() {
        val archive = ArchiveFormatDetector.detect("backup.zip")

        assertFalse(archive.supportsZipExtraction(ArchiveFormat.RAR))
    }

    @Test fun preservesRarMultipartFilenameDetection() {
        val numbered = ArchiveFormatDetector.detect("archive.part1.rar")
        assertEquals(ArchiveFormat.RAR, numbered.format)
        assertTrue(numbered.isMultipart)
        assertEquals(1, numbered.partNumber)

        val legacy = ArchiveFormatDetector.detect("archive.r00")
        assertEquals(ArchiveFormat.RAR, legacy.format)
        assertTrue(legacy.isMultipart)
        assertEquals(0, legacy.partNumber)
    }
}
