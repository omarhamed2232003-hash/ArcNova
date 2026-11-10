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

    @Test fun resolvesSupportedTarAndGzipFormatsFromExtensionsAndSignatures() {
        val tar = ArchiveFormatDetector.detect("backup.tar")
        assertEquals(ArchiveFormat.TAR, tar.supportedExtractionFormat(null))
        assertEquals(ArchiveFormat.TAR, tar.supportedExtractionFormat(ArchiveFormat.UNKNOWN))
        assertEquals(ArchiveFormat.TAR, tar.supportedExtractionFormat(ArchiveFormat.TAR))
        assertEquals(null, tar.supportedExtractionFormat(ArchiveFormat.GZIP))

        val gzip = ArchiveFormatDetector.detect("payload.gz")
        assertEquals(ArchiveFormat.GZIP, gzip.supportedExtractionFormat(null))
        assertEquals(ArchiveFormat.GZIP, gzip.supportedExtractionFormat(ArchiveFormat.GZIP))
        assertEquals(null, gzip.supportedExtractionFormat(ArchiveFormat.TAR))

        val signatureOnlyTar = ArchiveFormatDetector.detect("unknown.bin")
        assertEquals(ArchiveFormat.TAR, signatureOnlyTar.supportedExtractionFormat(ArchiveFormat.TAR))
        assertEquals(null, signatureOnlyTar.supportedExtractionFormat(ArchiveFormat.UNKNOWN))
    }

    @Test fun resolvesSevenZipForExtractionAndRejectsMultipartVolumes() {
        val sevenZip = ArchiveFormatDetector.detect("archive.7z")
        assertEquals(ArchiveFormat.SEVEN_ZIP, sevenZip.supportedExtractionFormat(null))
        assertEquals(ArchiveFormat.SEVEN_ZIP, sevenZip.supportedExtractionFormat(ArchiveFormat.SEVEN_ZIP))
        assertEquals(null, sevenZip.supportedExtractionFormat(ArchiveFormat.RAR))

        val firstVolume = ArchiveFormatDetector.detect("archive.7z.001")
        assertEquals(null, firstVolume.supportedExtractionFormat(ArchiveFormat.SEVEN_ZIP))
    }

    @Test fun supportedExtractionResolutionRejectsMultipartAndUnsupportedFormats() {
        val multipartZip = ArchiveFormatDetector.detect("backup.zip.001")
        assertEquals(null, multipartZip.supportedExtractionFormat(ArchiveFormat.ZIP))
        assertEquals(null, ArchiveFormatDetector.detect("backup.rar").supportedExtractionFormat(null))
    }

    @Test fun resolvesBzip2AndXzExtractionWithoutMisclassifyingLzmaAlone() {
        val bzip2 = ArchiveFormatDetector.detect("payload.bz2")
        assertEquals(ArchiveFormat.BZIP2, bzip2.supportedExtractionFormat(null))
        assertEquals(ArchiveFormat.BZIP2, bzip2.supportedExtractionFormat(ArchiveFormat.BZIP2))
        assertEquals(null, bzip2.supportedExtractionFormat(ArchiveFormat.XZ))

        val xz = ArchiveFormatDetector.detect("payload.tar.xz")
        assertEquals(ArchiveFormat.XZ, xz.supportedExtractionFormat(null))
        assertEquals(ArchiveFormat.XZ, xz.supportedExtractionFormat(ArchiveFormat.XZ))
        assertEquals(null, xz.supportedExtractionFormat(ArchiveFormat.BZIP2))

        val lzma = ArchiveFormatDetector.detect("payload.lzma")
        assertEquals(ArchiveFormat.LZMA, lzma.format)
        assertEquals(ArchiveFormat.LZMA, lzma.supportedExtractionFormat(null))
        assertEquals(null, lzma.supportedExtractionFormat(ArchiveFormat.XZ))
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
