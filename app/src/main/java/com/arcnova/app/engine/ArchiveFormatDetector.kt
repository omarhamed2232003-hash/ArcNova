package com.arcnova.app.engine

/** Best-effort filename-based format hint; archive bytes are not inspected. */
enum class ArchiveFormat(val label: String) {
    ZIP("ZIP"), SEVEN_ZIP("7-Zip"), RAR("RAR"), TAR("TAR"),
    GZIP("GZIP"), BZIP2("BZIP2"), XZ("XZ"), ZSTANDARD("Zstandard"),
    FREEARC("FreeArc"), UNKNOWN("Unknown")
}

data class ArchiveDetection(
    val format: ArchiveFormat,
    val isMultipart: Boolean,
    val partNumber: Int? = null,
    val confidence: Confidence = Confidence.EXTENSION_HINT
) {
    enum class Confidence { EXTENSION_HINT, UNKNOWN }
}

object ArchiveFormatDetector {
    private val numberedPart = Regex("^(.*)\\.(zip|7z)\\.(\\d{3,})$", RegexOption.IGNORE_CASE)
    private val rarPart = Regex("^.*\\.part(\\d+)\\.rar$", RegexOption.IGNORE_CASE)
    private val oldRarPart = Regex("^.*\\.r(\\d{2})$", RegexOption.IGNORE_CASE)

    fun detect(fileName: String?): ArchiveDetection {
        val name = fileName?.substringAfterLast('/')?.substringAfterLast('\\')
            ?.trim()?.lowercase().orEmpty()
        if (name.isBlank()) return unknown()
        numberedPart.matchEntire(name)?.let { match ->
            val format = if (match.groupValues[2] == "zip") ArchiveFormat.ZIP else ArchiveFormat.SEVEN_ZIP
            return ArchiveDetection(format, true, match.groupValues[3].toIntOrNull())
        }
        rarPart.matchEntire(name)?.let { match ->
            return ArchiveDetection(ArchiveFormat.RAR, true, match.groupValues[1].toIntOrNull())
        }
        oldRarPart.matchEntire(name)?.let { match ->
            return ArchiveDetection(ArchiveFormat.RAR, true, match.groupValues[1].toIntOrNull())
        }
        val extension = name.substringAfterLast('.', missingDelimiterValue = "")
        val format = when (extension) {
            "zip" -> ArchiveFormat.ZIP
            "7z" -> ArchiveFormat.SEVEN_ZIP
            "rar" -> ArchiveFormat.RAR
            "tar" -> ArchiveFormat.TAR
            "gz", "gzip" -> ArchiveFormat.GZIP
            "bz2", "bzip2" -> ArchiveFormat.BZIP2
            "xz", "lzma" -> ArchiveFormat.XZ
            "zst", "zstd" -> ArchiveFormat.ZSTANDARD
            "arc" -> ArchiveFormat.FREEARC
            else -> ArchiveFormat.UNKNOWN
        }
        return if (format == ArchiveFormat.UNKNOWN) unknown() else ArchiveDetection(format, false)
    }

    private fun unknown() = ArchiveDetection(ArchiveFormat.UNKNOWN, false, confidence = ArchiveDetection.Confidence.UNKNOWN)
}
