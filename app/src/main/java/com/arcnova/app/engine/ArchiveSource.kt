package com.arcnova.app.engine

import android.net.Uri

/**
 * A user-selected archive source. URI access is mediated by Android's
 * ContentResolver; never assume a content URI is a filesystem path.
 */
data class ArchiveSource(
    val uri: Uri,
    val displayName: String?,
    val declaredSizeBytes: Long?
)

/** Safe extraction policy applied before any entry is written. */
data class ExtractionPolicy(
    val maxEntries: Int = 100_000,
    val maxSingleFileBytes: Long = 20L * 1024L * 1024L * 1024L,
    val maxTotalOutputBytes: Long = 100L * 1024L * 1024L * 1024L,
    val maxExpansionRatio: Double = 10_000.0,
    val maxPathLength: Int = 4096,
    val maxPathDepth: Int = 64
) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
        require(maxSingleFileBytes >= 0L) { "maxSingleFileBytes must not be negative" }
        require(maxTotalOutputBytes >= 0L) { "maxTotalOutputBytes must not be negative" }
        require(maxExpansionRatio.isFinite() && maxExpansionRatio > 0.0) {
            "maxExpansionRatio must be finite and greater than zero"
        }
        require(maxPathLength > 0) { "maxPathLength must be positive" }
        require(maxPathDepth > 0) { "maxPathDepth must be positive" }
    }
}
