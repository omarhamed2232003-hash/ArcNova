package com.arcnova.app.engine

import org.junit.Assert.*
import org.junit.Test

class ArchiveRiskAssessmentTest {
    private fun entry(
        name: String,
        size: Long = 10,
        compressed: Long? = 10,
        declared: Long? = size,
        unsafe: Boolean = false,
        duplicate: Boolean = false
    ) = ZipEntryReport(name, false, declared, compressed, size, null, unsafe, duplicate)

    private fun report(vararg entries: ZipEntryReport) = ZipInspectionReport(
        entries = entries.toList(),
        fileCount = entries.size,
        directoryCount = 0,
        totalUncompressedBytes = entries.sumOf { it.actualSizeBytes },
        unsafePathCount = entries.count { it.hasUnsafePath },
        duplicateNameCount = entries.count { it.isDuplicateName }
    )

    private fun finding(result: ArchiveRiskReport, id: String) =
        result.findings.firstOrNull { it.id == id }

    @Test fun flagsUnsafePathsAsCritical() {
        val result = ArchiveRiskAssessment.assess(report(entry("../escape", unsafe = true)))
        assertEquals(RiskSeverity.CRITICAL, finding(result, "unsafe-path")?.severity)
        assertTrue(result.hasCriticalFindings)
    }

    @Test fun flagsDuplicateNames() {
        val result = ArchiveRiskAssessment.assess(report(entry("same.txt", duplicate = true)))
        assertEquals(1, finding(result, "duplicate-name")?.affectedEntries)
    }

    @Test fun warnsOnVeryLargeEntryCounts() {
        val result = ArchiveRiskAssessment.assess(
            report(entry("a")), ArchiveRiskAssessment.ArchiveRiskThresholds(maxEntriesWarning = 0)
        )
        assertNotNull(finding(result, "entry-count"))
    }

    @Test fun detectsDeclaredSizeMismatch() {
        val result = ArchiveRiskAssessment.assess(report(entry("a.txt", size = 20, declared = 10)))
        assertNotNull(finding(result, "size-mismatch"))
    }

    @Test fun detectsHighCompressionRatio() {
        val result = ArchiveRiskAssessment.assess(report(entry("huge.bin", size = 100_000, compressed = 10)))
        assertNotNull(finding(result, "high-compression-ratio"))
    }

    @Test fun identifiesExecutableContent() {
        val result = ArchiveRiskAssessment.assess(report(entry("setup.exe")))
        assertNotNull(finding(result, "executable-content"))
    }

    @Test fun identifiesScripts() {
        val result = ArchiveRiskAssessment.assess(report(entry("run.ps1")))
        assertNotNull(finding(result, "script-content"))
    }

    @Test fun identifiesNestedArchives() {
        val result = ArchiveRiskAssessment.assess(report(entry("payload.7z")))
        assertNotNull(finding(result, "nested-archives"))
    }

    @Test fun identifiesHiddenEntries() {
        val result = ArchiveRiskAssessment.assess(report(entry("config/.secret")))
        assertNotNull(finding(result, "hidden-entry"))
    }

    @Test fun detectsDeceptiveDoubleExtensions() {
        val result = ArchiveRiskAssessment.assess(report(entry("invoice.pdf.exe")))
        assertNotNull(finding(result, "double-extension"))
    }

    @Test fun cleanArchiveHasNoFindings() {
        val result = ArchiveRiskAssessment.assess(report(entry("readme.txt")))
        assertTrue(result.findings.isEmpty())
        assertEquals(0, result.totalAffectedEntries)
    }
}
