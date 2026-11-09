package com.arcnova.app.engine

/**
 * Honest, operation-level support inventory for formats ArcNova intends to handle.
 *
 * A roadmap entry is not an implementation. Only IMPLEMENTED_CI_TESTED or
 * DEVICE_VALIDATED may be presented as an available operation in the UI.
 */
enum class FormatOperation {
    DETECT, INSPECT, EXTRACT, CREATE, ENCODE, INSTALL, LAUNCH
}

enum class SupportLevel {
    NOT_IMPLEMENTED,
    PLANNED,
    IMPLEMENTED_CI_TESTED,
    DEVICE_VALIDATED
}

enum class FormatFamily {
    GENERAL_ARCHIVE,
    SPECIALIST_COMPRESSION,
    GAME_CONTAINER,
    GAME_ASSET_PACKAGE,
    INSTALLER_OR_RUNTIME
}

data class FormatCapability(
    val id: String,
    val displayName: String,
    val family: FormatFamily,
    val extensions: List<String>,
    val operationSupport: Map<FormatOperation, SupportLevel>,
    val notes: String
) {
    fun supports(operation: FormatOperation): Boolean =
        operationSupport[operation] == SupportLevel.IMPLEMENTED_CI_TESTED ||
            operationSupport[operation] == SupportLevel.DEVICE_VALIDATED
}

object FormatCapabilityRegistry {
    private fun planned(
        id: String,
        name: String,
        family: FormatFamily,
        extensions: List<String>,
        notes: String
    ) = FormatCapability(
        id = id,
        displayName = name,
        family = family,
        extensions = extensions,
        operationSupport = mapOf(
            FormatOperation.DETECT to SupportLevel.PLANNED,
            FormatOperation.INSPECT to SupportLevel.IMPLEMENTED_CI_TESTED,
            FormatOperation.EXTRACT to SupportLevel.PLANNED,
            FormatOperation.CREATE to SupportLevel.PLANNED
        ),
        notes = notes
    )

    private fun detected(
        id: String,
        name: String,
        family: FormatFamily,
        extensions: List<String>,
        notes: String
    ) = planned(id, name, family, extensions, notes).copy(
        operationSupport = mapOf(
            FormatOperation.DETECT to SupportLevel.IMPLEMENTED_CI_TESTED,
            FormatOperation.INSPECT to SupportLevel.PLANNED,
            FormatOperation.EXTRACT to SupportLevel.PLANNED,
            FormatOperation.CREATE to SupportLevel.PLANNED
        )
    )

    private val entries = listOf(
        FormatCapability(
            id = "zip",
            displayName = "ZIP",
            family = FormatFamily.GENERAL_ARCHIVE,
            extensions = listOf("zip"),
            operationSupport = mapOf(
                FormatOperation.DETECT to SupportLevel.IMPLEMENTED_CI_TESTED,
                FormatOperation.EXTRACT to SupportLevel.IMPLEMENTED_CI_TESTED,
                FormatOperation.INSPECT to SupportLevel.IMPLEMENTED_CI_TESTED,
                FormatOperation.CREATE to SupportLevel.PLANNED
            ),
            notes = "Bounded ZIP extraction and streaming inspection are implemented with path safety, limits, cancellation, and unit tests; physical-device validation is pending."
        ),
        detected("7z", "7-Zip", FormatFamily.GENERAL_ARCHIVE, listOf("7z"), "Detection only; no 7z decoder is registered."),
        detected("rar", "RAR", FormatFamily.GENERAL_ARCHIVE, listOf("rar", "r00"), "Detection only; no RAR decoder is registered."),
        detected("tar", "TAR", FormatFamily.GENERAL_ARCHIVE, listOf("tar"), "Detection only; no TAR reader is registered."),
        detected("gzip", "GZIP", FormatFamily.GENERAL_ARCHIVE, listOf("gz", "gzip"), "Detection only; no GZIP decoder is registered."),
        detected("bzip2", "BZIP2", FormatFamily.GENERAL_ARCHIVE, listOf("bz2", "bzip2"), "Detection only; no BZIP2 decoder is registered."),
        detected("xz", "XZ", FormatFamily.GENERAL_ARCHIVE, listOf("xz", "lzma"), "Detection only; no XZ decoder is registered."),
        detected("zstandard", "Zstandard", FormatFamily.GENERAL_ARCHIVE, listOf("zst", "zstd"), "Detection only; no Zstandard decoder is registered."),
        detected("freearc", "FreeArc", FormatFamily.SPECIALIST_COMPRESSION, listOf("arc"), "Extension hint only; multipart parsing and codec-chain decoding remain unimplemented."),
        planned("srep", "SREP", FormatFamily.SPECIALIST_COMPRESSION, emptyList(), "Codec/filter stage target; do not redistribute without explicit rights."),
        planned("lolz", "LOLZ", FormatFamily.SPECIALIST_COMPRESSION, emptyList(), "Codec/filter stage target; implementation and redistribution rights are unresolved."),
        detected("winrk", "WinRK", FormatFamily.SPECIALIST_COMPRESSION, listOf("rk"), "Filename extension hint only; parser and decoder are not implemented."),
        detected("kgb", "KGB", FormatFamily.SPECIALIST_COMPRESSION, listOf("kgb"), "Filename extension hint only; parser and decoder are not implemented."),
        detected("uharc", "UHARC", FormatFamily.SPECIALIST_COMPRESSION, listOf("uha"), "Filename extension hint only; parser and decoder are not implemented."),
        detected("zpaq", "ZPAQ", FormatFamily.SPECIALIST_COMPRESSION, listOf("zpaq"), "Filename extension hint only; parser and decoder are not implemented."),
        detected("dgca", "DGCA", FormatFamily.SPECIALIST_COMPRESSION, listOf("dgc"), "Filename extension hint only; parser and decoder are not implemented."),
        detected("stuffit", "StuffIt", FormatFamily.SPECIALIST_COMPRESSION, listOf("sit", "sitx"), "Filename extension hint only; container parsing, decoder licensing, and feasibility require research."),
        detected("unreal-pak", "Unreal PAK", FormatFamily.GAME_CONTAINER, listOf("pak"), "Filename extension hint only; version-aware parsing, encryption keys, and extraction remain unimplemented."),
        detected("unreal-iostore", "Unreal IoStore", FormatFamily.GAME_CONTAINER, listOf("utoc", "ucas"), "Filename extension hint only; paired container parsing, versioning, and encryption handling remain unimplemented."),
        detected("unity-asset-bundle", "Unity AssetBundle", FormatFamily.GAME_ASSET_PACKAGE, listOf("unity3d", "bundle"), "Filename extension hint only; version-aware bundle and asset parsing remain unimplemented."),
        detected("source-vpk", "Source VPK", FormatFamily.GAME_CONTAINER, listOf("vpk"), "Filename extension hint only; directory/archive variants and version-aware extraction remain unimplemented."),
        detected("xp3", "XP3", FormatFamily.GAME_CONTAINER, listOf("xp3"), "Filename extension hint only; archive parsing and engine-specific encryption/plugin behavior remain unimplemented."),
        detected("rpa", "Ren'Py RPA", FormatFamily.GAME_CONTAINER, listOf("rpa"), "Filename extension hint only; version-aware index parsing and encryption handling remain unimplemented."),
        planned("oodle-kraken", "Oodle Kraken / Leviathan", FormatFamily.SPECIALIST_COMPRESSION, emptyList(), "Investigate only with required SDK, patent, and redistribution permissions.")
    )

    val all: List<FormatCapability> = entries

    fun findById(id: String): FormatCapability? =
        entries.firstOrNull { it.id.equals(id, ignoreCase = true) }

    fun findByExtension(extension: String): List<FormatCapability> {
        val normalized = extension.trim().removePrefix(".").lowercase()
        if (normalized.isEmpty()) return emptyList()
        return entries.filter { capability ->
            capability.extensions.any { it.equals(normalized, ignoreCase = true) }
        }
    }

    fun available(operation: FormatOperation): List<FormatCapability> =
        entries.filter { it.supports(operation) }
}
