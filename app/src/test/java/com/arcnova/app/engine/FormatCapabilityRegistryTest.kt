package com.arcnova.app.engine

import org.junit.Assert.*
import org.junit.Test

class FormatCapabilityRegistryTest {
    @Test fun roadmapIncludesSpecialistAndGameFormats() {
        val expected = listOf(
            "freearc", "srep", "lolz", "winrk", "kgb", "uharc",
            "zpaq", "dgca", "stuffit", "unreal-pak", "unreal-iostore",
            "unity-asset-bundle", "source-vpk", "xp3", "rpa", "oodle-kraken"
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
