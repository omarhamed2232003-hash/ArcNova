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

    @Test fun zipExtractionIsTheOnlyCurrentlyImplementedArchiveOperation() {
        val extraction = FormatCapabilityRegistry.available(FormatOperation.EXTRACT)
        assertEquals(listOf("zip"), extraction.map { it.id })
        assertTrue(FormatCapabilityRegistry.findById("zip")!!.supports(FormatOperation.EXTRACT))
        assertFalse(FormatCapabilityRegistry.findById("freearc")!!.supports(FormatOperation.EXTRACT))
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
        assertEquals("unity-asset-bundle", FileSignatureDetector.detect("UnityFS\\u0000".toByteArray())!!.id)
        assertEquals("unity-asset-bundle", FileSignatureDetector.detect("UnityWebData1.0".toByteArray())!!.id)
        assertEquals("rpa", FileSignatureDetector.detect("RPA-3.0 0000000000000000 00000000\\n".toByteArray())!!.id)
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

    @Test fun extensionDetectionDoesNotImplyInspectionOrExtraction() {
        val kgb = FormatCapabilityRegistry.findById("kgb")!!
        assertEquals(SupportLevel.IMPLEMENTED_CI_TESTED, kgb.operationSupport[FormatOperation.DETECT])
        assertEquals(SupportLevel.PLANNED, kgb.operationSupport[FormatOperation.INSPECT])
        assertEquals(SupportLevel.PLANNED, kgb.operationSupport[FormatOperation.EXTRACT])
        assertTrue(kgb.supports(FormatOperation.DETECT))
        assertFalse(kgb.supports(FormatOperation.INSPECT))
        assertFalse(kgb.supports(FormatOperation.EXTRACT))
    }

    @Test fun plannedFormatsAreNotReportedAsAvailable() {
        val kgb = FormatCapabilityRegistry.findById("kgb")!!
        assertEquals(SupportLevel.PLANNED, kgb.operationSupport[FormatOperation.EXTRACT])
        assertFalse(kgb.supports(FormatOperation.EXTRACT))
    }
}
