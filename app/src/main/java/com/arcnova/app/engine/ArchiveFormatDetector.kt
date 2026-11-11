package com.arcnova.app.engine

/** Best-effort filename-based format hint; archive bytes are not inspected. */
enum class ArchiveFormat(val label: String) {
    ZIP("ZIP"), SEVEN_ZIP("7-Zip"), RAR("RAR"), AR("AR / Debian package"), CPIO("CPIO"), TAR("TAR"),
    GZIP("GZIP"), BZIP2("BZIP2"), XZ("XZ"), LZMA("LZMA-alone"), ZSTANDARD("Zstandard"),
    FREEARC("FreeArc"), WINRK("WinRK"), KGB("KGB"), UHARC("UHARC"),
    ZPAQ("ZPAQ"), DGCA("DGCA"), STUFFIT("StuffIt"),
    UNREAL_PAK("Unreal PAK"), UNREAL_IOSTORE("Unreal IoStore"),
    UNITY_ASSET_BUNDLE("Unity AssetBundle"), SOURCE_VPK("Source VPK"),
    XP3("XP3"), RPA("Ren'Py Archive"), UNKNOWN("Unknown")
}

data class ArchiveDetection(
    val format: ArchiveFormat,
    val isMultipart: Boolean,
    val partNumber: Int? = null,
    val confidence: Confidence = Confidence.EXTENSION_HINT
) {
    enum class Confidence { EXTENSION_HINT, UNKNOWN }

    fun supportedExtractionFormat(signatureFormat: ArchiveFormat?): ArchiveFormat? {
        if (isMultipart) return null
        val hasSignature = signatureFormat != null && signatureFormat != ArchiveFormat.UNKNOWN
        if (hasSignature && format != ArchiveFormat.UNKNOWN && format != signatureFormat) return null
        val resolved = if (format != ArchiveFormat.UNKNOWN) {
            format
        } else {
            signatureFormat?.takeIf { it != ArchiveFormat.UNKNOWN } ?: ArchiveFormat.UNKNOWN
        }
        return resolved.takeIf {
            it == ArchiveFormat.ZIP || it == ArchiveFormat.SEVEN_ZIP || it == ArchiveFormat.AR ||
                it == ArchiveFormat.CPIO || it == ArchiveFormat.TAR ||
                it == ArchiveFormat.GZIP || it == ArchiveFormat.BZIP2 || it == ArchiveFormat.XZ || it == ArchiveFormat.LZMA ||
                it == ArchiveFormat.ZSTANDARD || it == ArchiveFormat.RAR
        }
    }

    fun supportsZipExtraction(signatureFormat: ArchiveFormat?): Boolean =
        supportedExtractionFormat(signatureFormat) == ArchiveFormat.ZIP
}

object ArchiveFormatDetector {
    private val numberedPart = Regex("^(.*)\\.(zip|7z)\\.(\\d{3,})$", RegexOption.IGNORE_CASE)
    private val splitZipPart = Regex("^(.*)\\.z(\\d{2,})$", RegexOption.IGNORE_CASE)
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
        splitZipPart.matchEntire(name)?.let { match ->
            return ArchiveDetection(ArchiveFormat.ZIP, true, match.groupValues[2].toIntOrNull())
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
            "a", "ar", "deb" -> ArchiveFormat.AR
            "cpio" -> ArchiveFormat.CPIO
            "tar" -> ArchiveFormat.TAR
            "gz", "gzip" -> ArchiveFormat.GZIP
            "bz2", "bzip2" -> ArchiveFormat.BZIP2
            "xz" -> ArchiveFormat.XZ
            "lzma" -> ArchiveFormat.LZMA
            "zst", "zstd" -> ArchiveFormat.ZSTANDARD
            "arc" -> ArchiveFormat.FREEARC
            "rk" -> ArchiveFormat.WINRK
            "kgb" -> ArchiveFormat.KGB
            "uha" -> ArchiveFormat.UHARC
            "zpaq" -> ArchiveFormat.ZPAQ
            "dgc" -> ArchiveFormat.DGCA
            "sit", "sitx" -> ArchiveFormat.STUFFIT
            "pak" -> ArchiveFormat.UNREAL_PAK
            "utoc", "ucas" -> ArchiveFormat.UNREAL_IOSTORE
            "unity3d", "bundle" -> ArchiveFormat.UNITY_ASSET_BUNDLE
            "vpk" -> ArchiveFormat.SOURCE_VPK
            "xp3" -> ArchiveFormat.XP3
            "rpa" -> ArchiveFormat.RPA
            else -> ArchiveFormat.UNKNOWN
        }
        return if (format == ArchiveFormat.UNKNOWN) unknown() else ArchiveDetection(format, false)
    }

    private fun unknown() = ArchiveDetection(
        ArchiveFormat.UNKNOWN,
        false,
        confidence = ArchiveDetection.Confidence.UNKNOWN
    )
}
