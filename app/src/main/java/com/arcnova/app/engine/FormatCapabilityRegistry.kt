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
            FormatOperation.INSPECT to SupportLevel.PLANNED,
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
        FormatCapability(
            id = "7z",
            displayName = "7-Zip",
            family = FormatFamily.GENERAL_ARCHIVE,
            extensions = listOf("7z"),
            operationSupport = mapOf(
                FormatOperation.DETECT to SupportLevel.IMPLEMENTED_CI_TESTED,
                FormatOperation.EXTRACT to SupportLevel.IMPLEMENTED_CI_TESTED,
                FormatOperation.INSPECT to SupportLevel.PLANNED,
                FormatOperation.CREATE to SupportLevel.PLANNED
            ),
            notes = "Bounded 7z extraction uses Commons Compress 1.28.0 with XZ for Java 1.12. Only implemented compression/filter methods are accepted; encrypted entries and multipart volumes are not supported. LZMA/LZMA2 decoder memory is capped at 256 MiB. Physical-device validation is pending."
        ),
        FormatCapability(
            id = "rar",
            displayName = "RAR",
            family = FormatFamily.GENERAL_ARCHIVE,
            extensions = listOf("rar", "r00"),
            operationSupport = mapOf(
                FormatOperation.DETECT to SupportLevel.IMPLEMENTED_CI_TESTED,
                FormatOperation.EXTRACT to SupportLevel.IMPLEMENTED_CI_TESTED,
                FormatOperation.INSPECT to SupportLevel.PLANNED,
                FormatOperation.CREATE to SupportLevel.PLANNED
            ),
            notes = "Single-volume RAR3 and RAR5 extraction uses the Android native archive backend behind ArcNova's bounded transactional writer. Encrypted entries, links, unsupported entry types, and detected multipart filenames are rejected. Upstream RAR limitations and physical-device validation remain."
        ),
        FormatCapability(
            id = "ar",
            displayName = "AR / Debian package",
            family = FormatFamily.GENERAL_ARCHIVE,
            extensions = listOf("a", "ar", "deb"),
            operationSupport = mapOf(
                FormatOperation.DETECT to SupportLevel.IMPLEMENTED_CI_TESTED,
                FormatOperation.EXTRACT to SupportLevel.IMPLEMENTED_CI_TESTED,
                FormatOperation.INSPECT to SupportLevel.PLANNED,
                FormatOperation.CREATE to SupportLevel.PLANNED
            ),
            notes = "Bounded AR extraction uses the existing Apache Commons Compress 1.28.0 dependency. GNU/BSD long-name handling is delegated to its reader; symbol-table members are skipped, and ordinary members must be regular files. A .deb is extracted only as its outer AR container; nested control/data archives are separate files, not installed packages. Physical-device validation is pending."
        ),
        FormatCapability(
            id = "cpio",
            displayName = "CPIO",
            family = FormatFamily.GENERAL_ARCHIVE,
            extensions = listOf("cpio"),
            operationSupport = mapOf(
                FormatOperation.DETECT to SupportLevel.IMPLEMENTED_CI_TESTED,
                FormatOperation.EXTRACT to SupportLevel.IMPLEMENTED_CI_TESTED,
                FormatOperation.INSPECT to SupportLevel.PLANNED,
                FormatOperation.CREATE to SupportLevel.PLANNED
            ),
            notes = "Bounded CPIO extraction uses Apache Commons Compress 1.28.0 and supports newc and newc-with-CRC only. Legacy old ASCII and old binary CPIO headers are deliberately rejected before the Commons Compress parser receives them, because their variable-length name metadata does not yet have the same pre-allocation guard. CRC is checked by the reader. Symbolic/hard links and special inode types are rejected. Physical-device validation is pending."
        ),
        FormatCapability(
            id = "tar",
            displayName = "TAR",
            family = FormatFamily.GENERAL_ARCHIVE,
            extensions = listOf("tar"),
            operationSupport = mapOf(
                FormatOperation.DETECT to SupportLevel.IMPLEMENTED_CI_TESTED,
                FormatOperation.EXTRACT to SupportLevel.IMPLEMENTED_CI_TESTED,
                FormatOperation.INSPECT to SupportLevel.PLANNED,
                FormatOperation.CREATE to SupportLevel.PLANNED
            ),
            notes = "Streaming TAR extraction is implemented with entry-type rejection, path safety, input/output limits, cancellation, rollback, and CI tests. Physical-device validation is pending."
        ),
        FormatCapability(
            id = "gzip",
            displayName = "GZIP",
            family = FormatFamily.GENERAL_ARCHIVE,
            extensions = listOf("gz", "gzip"),
            operationSupport = mapOf(
                FormatOperation.DETECT to SupportLevel.IMPLEMENTED_CI_TESTED,
                FormatOperation.EXTRACT to SupportLevel.IMPLEMENTED_CI_TESTED,
                FormatOperation.INSPECT to SupportLevel.PLANNED,
                FormatOperation.CREATE to SupportLevel.PLANNED
            ),
            notes = "Single-stream GZIP decompression is implemented with concatenated-member support, CRC validation, input/output limits, expansion-ratio checks, cancellation, rollback, and CI tests. Physical-device validation is pending."
        ),
        FormatCapability(
            id = "bzip2", displayName = "BZIP2", family = FormatFamily.GENERAL_ARCHIVE,
            extensions = listOf("bz2", "bzip2"),
            operationSupport = mapOf(
                FormatOperation.DETECT to SupportLevel.IMPLEMENTED_CI_TESTED,
                FormatOperation.EXTRACT to SupportLevel.IMPLEMENTED_CI_TESTED,
                FormatOperation.INSPECT to SupportLevel.PLANNED,
                FormatOperation.CREATE to SupportLevel.PLANNED
            ),
            notes = "Single-stream BZIP2 decompression uses Commons Compress 1.28.0 with concatenated-stream support, bounded input/output, expansion-ratio checks, cancellation, rollback, and CI tests. Physical-device validation is pending."
        ),
        FormatCapability(
            id = "xz", displayName = "XZ", family = FormatFamily.GENERAL_ARCHIVE,
            extensions = listOf("xz"),
            operationSupport = mapOf(
                FormatOperation.DETECT to SupportLevel.IMPLEMENTED_CI_TESTED,
                FormatOperation.EXTRACT to SupportLevel.IMPLEMENTED_CI_TESTED,
                FormatOperation.INSPECT to SupportLevel.PLANNED,
                FormatOperation.CREATE to SupportLevel.PLANNED
            ),
            notes = "Single-stream XZ decompression uses Commons Compress 1.28.0 and XZ for Java 1.12 with a 128 MiB decoder memory limit, concatenated-stream support, bounded input/output, expansion-ratio checks, cancellation, rollback, and CI tests. Physical-device validation is pending. LZMA-alone (.lzma) remains unsupported."
        ),
        FormatCapability(
            id = "lzma", displayName = "LZMA-alone", family = FormatFamily.GENERAL_ARCHIVE,
            extensions = listOf("lzma"),
            operationSupport = mapOf(
                FormatOperation.DETECT to SupportLevel.IMPLEMENTED_CI_TESTED,
                FormatOperation.EXTRACT to SupportLevel.IMPLEMENTED_CI_TESTED,
                FormatOperation.INSPECT to SupportLevel.PLANNED,
                FormatOperation.CREATE to SupportLevel.PLANNED
            ),
            notes = "Single-stream legacy .lzma decompression uses XZ for Java 1.12 with a 128 MiB decoder memory limit, buffered input, bounded input/output, expansion-ratio checks, cancellation, rollback, and CI tests. This is distinct from the .xz container format; physical-device validation is pending."
        ),
        FormatCapability(
            id = "zstandard", displayName = "Zstandard", family = FormatFamily.GENERAL_ARCHIVE,
            extensions = listOf("zst", "zstd"),
            operationSupport = mapOf(
                FormatOperation.DETECT to SupportLevel.IMPLEMENTED_CI_TESTED,
                FormatOperation.EXTRACT to SupportLevel.IMPLEMENTED_CI_TESTED,
                FormatOperation.INSPECT to SupportLevel.PLANNED,
                FormatOperation.CREATE to SupportLevel.PLANNED
            ),
            notes = "Bounded single-stream Zstandard decompression uses zstd-jni 1.5.7-25 Android AAR with a 128 MiB maximum decoder window, input/output and expansion-ratio limits, cancellation, rollback, and CI tests. External dictionaries are not supported. Physical-device ABI and 16 KB page-size validation is pending."
        ),
        detected("cab", "Microsoft Cabinet", FormatFamily.GENERAL_ARCHIVE, listOf("cab"), "Signature detection only; CAB inspection and extraction are not implemented."),
        detected("iso9660", "ISO 9660", FormatFamily.GENERAL_ARCHIVE, listOf("iso"), "Descriptor signature detection only; filesystem parsing and extraction are not implemented."),
        planned("freearc", "FreeArc", FormatFamily.SPECIALIST_COMPRESSION, listOf("arc"), "Filename extension hint only; no reliable signature detector, multipart parser, or codec-chain decoder is implemented."),
        planned("srep", "SREP", FormatFamily.SPECIALIST_COMPRESSION, emptyList(), "Codec/filter stage target; do not redistribute without explicit rights."),
        planned("lolz", "LOLZ", FormatFamily.SPECIALIST_COMPRESSION, emptyList(), "Codec/filter stage target; implementation and redistribution rights are unresolved."),
        planned("winrk", "WinRK", FormatFamily.SPECIALIST_COMPRESSION, listOf("rk"), "Filename extension hint only; signature detection, parser, and decoder are not implemented."),
        planned("kgb", "KGB", FormatFamily.SPECIALIST_COMPRESSION, listOf("kgb"), "Filename extension hint only; signature detection, parser, and decoder are not implemented."),
        planned("uharc", "UHARC", FormatFamily.SPECIALIST_COMPRESSION, listOf("uha"), "Filename extension hint only; signature detection, parser, and decoder are not implemented."),
        planned("zpaq", "ZPAQ", FormatFamily.SPECIALIST_COMPRESSION, listOf("zpaq"), "Filename extension hint only; signature detection, parser, and decoder are not implemented."),
        planned("dgca", "DGCA", FormatFamily.SPECIALIST_COMPRESSION, listOf("dgc"), "Filename extension hint only; signature detection, parser, and decoder are not implemented."),
        planned("stuffit", "StuffIt", FormatFamily.SPECIALIST_COMPRESSION, listOf("sit", "sitx"), "Filename extension hint only; signature detection, container parsing, decoder licensing, and feasibility require research."),
        planned("unreal-pak", "Unreal PAK", FormatFamily.GAME_CONTAINER, listOf("pak"), "Filename extension hint only; signature detection, version-aware parsing, encryption keys, and extraction remain unimplemented."),
        planned("unreal-iostore", "Unreal IoStore", FormatFamily.GAME_CONTAINER, listOf("utoc", "ucas"), "Filename extension hint only; signature detection, paired container parsing, versioning, and encryption handling remain unimplemented."),
        detected("unity-asset-bundle", "Unity AssetBundle", FormatFamily.GAME_ASSET_PACKAGE, listOf("unity3d", "bundle"), "Unity bundle-family signatures are detected; version-aware bundle and asset parsing remain unimplemented."),
        detected("source-vpk", "Source VPK", FormatFamily.GAME_CONTAINER, listOf("vpk"), "VPK magic-byte detection is implemented; directory/archive parsing and version-aware extraction remain unimplemented."),
        detected("xp3", "XP3", FormatFamily.GAME_CONTAINER, listOf("xp3"), "XP3 signature detection is implemented; archive parsing and engine-specific encryption/plugin behavior remain unimplemented."),
        detected("rpa", "Ren'Py RPA", FormatFamily.GAME_CONTAINER, listOf("rpa"), "RPA 2.0/3.0 signatures are detected; version-aware index parsing and encryption handling remain unimplemented."),
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
