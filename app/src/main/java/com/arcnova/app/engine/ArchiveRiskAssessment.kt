package com.arcnova.app.engine

/**
 * Heuristic risk indicators for an inspected ZIP. Findings are advisory:
 * they do not prove that an archive is malicious or safe.
 */
object ArchiveRiskAssessment {
    fun assess(
        report: ZipInspectionReport,
        thresholds: ArchiveRiskThresholds = ArchiveRiskThresholds()
    ): ArchiveRiskReport {
        val findings = mutableListOf<ArchiveRiskFinding>()
        fun add(id: String, severity: RiskSeverity, count: Int, message: String) {
            if (count > 0) findings += ArchiveRiskFinding(id, severity, count, message)
        }

        add("unsafe-path", RiskSeverity.CRITICAL, report.unsafePathCount,
            "Entries contain absolute paths or traversal segments.")
        add("duplicate-name", RiskSeverity.HIGH, report.duplicateNameCount,
            "Multiple entries use the same path.")
        add("entry-count", RiskSeverity.MEDIUM,
            if (report.entries.size > thresholds.maxEntriesWarning) 1 else 0,
            "The archive contains an unusually large number of entries.")

        val sizeMismatches = report.entries.count {
            it.declaredSizeBytes != null && it.declaredSizeBytes != it.actualSizeBytes
        }
        add("size-mismatch", RiskSeverity.HIGH, sizeMismatches,
            "Declared and streamed uncompressed sizes differ.")

        val highRatios = report.entries.count { entry ->
            val compressed = entry.declaredCompressedSizeBytes
            compressed != null && compressed > 0 &&
                entry.actualSizeBytes.toDouble() / compressed.toDouble() > thresholds.maxCompressionRatioWarning
        }
        add("high-compression-ratio", RiskSeverity.HIGH, highRatios,
            "One or more entries expand by an unusually high ratio.")

        add("executable-content", RiskSeverity.MEDIUM,
            report.entries.count { extension(it.name) in EXECUTABLE_EXTENSIONS },
            "The archive contains executable or installer-like files.")
        add("script-content", RiskSeverity.HIGH,
            report.entries.count { extension(it.name) in SCRIPT_EXTENSIONS },
            "The archive contains scripts that may execute commands.")
        add("nested-archives", RiskSeverity.LOW,
            report.entries.count { extension(it.name) in ARCHIVE_EXTENSIONS },
            "The archive contains nested archive files.")
        add("hidden-entry", RiskSeverity.LOW,
            report.entries.count {
                it.name.replace('\\\\', '/').substringAfterLast('/').startsWith(".")
            },
            "The archive contains hidden or dot-prefixed entries.")
        add("double-extension", RiskSeverity.MEDIUM,
            report.entries.count {
                DOUBLE_EXTENSION_PATTERN.matches(it.name.substringAfterLast('/').lowercase())
            },
            "One or more filenames use potentially deceptive double extensions.")

        return ArchiveRiskReport(findings)
    }

    private fun extension(path: String): String {
        val name = path.substringAfterLast('/').substringAfterLast('\\\\')
        val dot = name.lastIndexOf('.')
        return if (dot <= 0 || dot == name.lastIndex) "" else name.substring(dot + 1).lowercase()
    }

    private val EXECUTABLE_EXTENSIONS = setOf("exe", "dll", "msi", "com", "scr", "apk")
    private val SCRIPT_EXTENSIONS = setOf("bat", "cmd", "ps1", "vbs", "js", "sh")
    private val ARCHIVE_EXTENSIONS = setOf("zip", "7z", "rar", "tar", "gz", "bz2", "xz", "zst")
    private val DOUBLE_EXTENSION_PATTERN =
        Regex(""".+\.(pdf|jpg|jpeg|png|txt|doc|docx)\.(exe|scr|com|bat|cmd|js|vbs|ps1)$""")
}

data class ArchiveRiskThresholds(
    val maxEntriesWarning: Int = 10_000,
    val maxCompressionRatioWarning: Double = 100.0
) {
    init {
        require(maxEntriesWarning >= 0)
        require(maxCompressionRatioWarning.isFinite() && maxCompressionRatioWarning > 0.0)
    }
}

enum class RiskSeverity { LOW, MEDIUM, HIGH, CRITICAL }

data class ArchiveRiskFinding(
    val id: String,
    val severity: RiskSeverity,
    val affectedEntries: Int,
    val explanation: String
)

data class ArchiveRiskReport(val findings: List<ArchiveRiskFinding>) {
    val hasCriticalFindings: Boolean get() = findings.any { it.severity == RiskSeverity.CRITICAL }
    val totalAffectedEntries: Int get() = findings.sumOf { it.affectedEntries }
}
