package com.arcnova.app.engine

import org.junit.Assert.*
import org.junit.Test

class FormatCapabilityRegistryTest {
    @Test fun roadmapIncludesSpecialistAndGameFormats() {
        val expected = listOf(
            "freearc", "srep", "lolz", "winrk", "kgb", "uharc",
            "zpaq", "dgca", "stuffit", "unreal-pak", "unreal-iostore",
            "unity-asset-bundle", "source-vpk", "xp3", "rpa", "cab", "iso9660", "oodle-kraken"
        )
        expected.forEach { id ->
            assertNotNull("Missing roadmap entry: $id", FormatCapabilityRegistry.findById(id))
        }
    }

    @Test fun zipTarAndGzipExtractionAreTheOnlyImplementedGeneralArchiveOperations() {
        val extraction = FormatCapabilityRegistry.available(FormatOperation.EXTRACT)
        assertEquals(listOf("zip", "tar", "gzip"), extraction.map { it.id })
        listOf("zip", "tar", "gzip").forEach { id ->
            assertTrue("$id extraction should be CI-tested", FormatCapabilityRegistry.findById(id)!!.supports(FormatOperation.EXTRACT))
        }
        assertFalse(FormatCapabilityRegistry.findById("freearc")!!.supports(FormatOperation.EXTRACT))
        assertFalse(FormatCapabilityRegistry.findById("7z")!!.supports(FormatOperation.EXTRACT))
        assertFalse(FormatCapabilityRegistry.findById("rar")!!.supports(FormatOperation.EXTRACT))
    }

    @Test fun tarAndGzipInspectionRemainUnavailable() {
        listOf("tar", "gzip").forEach { id ->
            val capability = FormatCapabilityRegistry.findById(id)!!
            assertEquals(SupportLevel.IMPLEMENTED_CI_TESTED, capability.operationSupport[FormatOperation.EXTRACT])
            assertEquals(SupportLevel.PLANNED, capability.operationSupport[FormatOperation.INSPECT])
            assertFalse(capability.supports(FormatOperation.INSPECT))
        }
    }

    @Test fun zipInspectionIsReportedAsImplemented() {
        val zip = FormatCapabilityRegistry.findById("zip")!!
        assertEquals(SupportLevel.IMPLEMENTED_CI_TESTED, zip.operationSupport[FormatOperation.INSPECT])
        assertTrue(zip.supports(FormatOperation.INSPECT))
    }

    @Test fun plannedFormatsNeverClaimInspectionIsAvailable() {
        FormatCapabilityRegistry.all.filter { it.id != "zip" && it.operationSupport[FormatOperation.INSPECT] == SupportLevel.PLANNED }
            .forEach { assertFalse("${it.id} must not claim inspection support", it.supports(FormatOperation.INSPECT)) }
    }

    @Test fun byteSignatureFormatsAreRegisteredAsDetectOnly() {
        listOf("unity-asset-bundle", "source-vpk", "xp3", "rpa", "cab", "iso9660").forEach { id ->
            val capability = FormatCapabilityRegistry.findById(id)!!
            assertEquals("$id detection should be CI-tested", SupportLevel.IMPLEMENTED_CI_TESTED,
                capability.operationSupport[FormatOperation.DETECT])
            assertEquals("$id inspection must remain planned", SupportLevel.PLANNED,
                capability.operationSupport[FormatOperation.INSPECT])
            assertEquals("$id extraction must remain planned", SupportLevel.PLANNED,
                capability.operationSupport[FormatOperation.EXTRACT])
            assertFalse("$id must not claim extraction", capability.supports(FormatOperation.EXTRACT))
        }
    }

    @Test fun signatureDetectedExtensionsResolveToTheSameCapabilityIds() {
        assertEquals("unity-asset-bundle", FileSignatureDetector.detect("UnityFS\u0000".toByteArray())!!.id)
        assertEquals("unity-asset-bundle", FileSignatureDetector.detect("UnityWebData1.0".toByteArray())!!.id)
        assertEquals("rpa", FileSignatureDetector.detect("RPA-3.0 0000000000000000 00000000\n".toByteArray())!!.id)
        val tar = ByteArray(263)
        "ustar".toByteArray().copyInto(tar, 257)
        assertEquals("tar", FileSignatureDetector.detect(tar)!!.id)
        assertEquals("cab", FormatCapabilityRegistry.findByExtension("CAB").single().id)
        assertEquals("iso9660", FormatCapabilityRegistry.findByExtension(".ISO").single().id)
    }

    @Test fun extensionLookupNormalizesDotsAndCase() {
        assertEquals("unreal-pak", FormatCapabilityRegistry.findByExtension(".PAK").single().id)
        assertEquals("stuffit", FormatCapabilityRegistry.findByExtension("SITX").single().id)
        assertTrue(FormatCapabilityRegistry.findByExtension("unknown").isEmpty())
    }

    @Test fun extensionHintsDoNotClaimSignatureDetectionOrArchiveOperations() {
        val extensionOnlyFormats = mapOf(
            "freearc" to "arc",
            "winrk" to "rk",
            "kgb" to "kgb",
            "uharc" to "uha",
            "zpaq" to "zpaq",
            "dgca" to "dgc",
            "stuffit" to "sitx",
            "unreal-pak" to "pak",
            "unreal-iostore" to "utoc"
        )
        extensionOnlyFormats.forEach { (id, extension) ->
            val capability = FormatCapabilityRegistry.findById(id)!!
            assertEquals("$id extension hint must not claim signature detection",
                SupportLevel.PLANNED, capability.operationSupport[FormatOperation.DETECT])
            assertFalse("$id must not be advertised as detected", capability.supports(FormatOperation.DETECT))
            assertTrue("$id extension should remain searchable",
                FormatCapabilityRegistry.findByExtension(extension).any { it.id == id })
            assertEquals(SupportLevel.PLANNED, capability.operationSupport[FormatOperation.INSPECT])
            assertEquals(SupportLevel.PLANNED, capability.operationSupport[FormatOperation.EXTRACT])
        }
    }

    @Test fun plannedFormatsAreNotReportedAsAvailable() {
        val kgb = FormatCapabilityRegistry.findById("kgb")!!
        assertEquals(SupportLevel.PLANNED, kgb.operationSupport[FormatOperation.EXTRACT])
        assertFalse(kgb.supports(FormatOperation.EXTRACT))
    }
}
