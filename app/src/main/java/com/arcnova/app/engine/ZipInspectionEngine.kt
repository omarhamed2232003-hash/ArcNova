package com.arcnova.app.engine

import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Reads ZIP local entries without writing files to storage.
 *
 * Every entry is consumed so the ZIP stream can perform its CRC checks.
 * This is a bounded streaming inspection, not a complete central-directory
 * validator and not a malware/safety verdict.
 */
object ZipInspectionEngine {
    fun inspect(
        input: InputStream,
        limits: ZipInspectionLimits = ZipInspectionLimits(),
        cancellationRequested: () -> Boolean = { false }
    ): ZipInspectionReport {
        require(limits.maxEntries > 0) { "maxEntries must be positive" }
        require(limits.maxSingleEntryBytes >= 0) { "maxSingleEntryBytes must not be negative" }
        require(limits.maxTotalUncompressedBytes >= 0) {
            "maxTotalUncompressedBytes must not be negative"
        }

        val entries = mutableListOf<ZipEntryReport>()
        val seenNames = mutableSetOf<String>()
        var totalBytes = 0L
        var entry: ZipEntry?
        val nonClosingInput = object : FilterInputStream(input) {
            override fun close() = Unit
        }
        ZipInputStream(nonClosingInput.buffered()).use { zip ->
            while (true) {
                if (cancellationRequested()) throw ZipInspectionCancelledException()
                entry = zip.nextEntry ?: break
                if (entries.size >= limits.maxEntries) {
                    throw ZipInspectionLimitException("Entry count exceeds ${limits.maxEntries}")
                }

                val current = entry!!
                val name = current.name
                val unsafe = isUnsafePath(name)
                val normalizedName = name.replace(92.toChar(), '/').split('/').filter { it.isNotEmpty() }.joinToString("/")
                val duplicate = !seenNames.add(normalizedName)
                var entryBytes = 0L
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                if (!current.isDirectory) {
                    while (true) {
                        if (cancellationRequested()) throw ZipInspectionCancelledException()
                        val count = zip.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        entryBytes = checkedAdd(entryBytes, count.toLong())
                        totalBytes = checkedAdd(totalBytes, count.toLong())
                        if (entryBytes > limits.maxSingleEntryBytes) {
                            throw ZipInspectionLimitException(
                                "Entry output exceeds per-entry limit: $name"
                            )
                        }
                        if (totalBytes > limits.maxTotalUncompressedBytes) {
                            throw ZipInspectionLimitException("Total uncompressed output exceeds limit")
                        }
                    }
                }

                entries += ZipEntryReport(
                    name = name,
                    isDirectory = current.isDirectory,
                    declaredSizeBytes = current.size.takeIf { it >= 0 },
                    declaredCompressedSizeBytes = current.compressedSize.takeIf { it >= 0 },
                    actualSizeBytes = entryBytes,
                    crc32 = current.crc.takeIf { it >= 0 },
                    hasUnsafePath = unsafe,
                    isDuplicateName = duplicate
                )
                zip.closeEntry()
            }
        }

        return ZipInspectionReport(
            entries = entries,
            fileCount = entries.count { !it.isDirectory },
            directoryCount = entries.count { it.isDirectory },
            totalUncompressedBytes = totalBytes,
            unsafePathCount = entries.count { it.hasUnsafePath },
            duplicateNameCount = entries.count { it.isDuplicateName }
        )
    }

    private fun isUnsafePath(name: String): Boolean {
        if (name.isBlank() || name.startsWith('/') || name.startsWith('\\') ||
            name.contains('\u0000') || Regex("^[A-Za-z]:").containsMatchIn(name)
        ) return true
        val segments = name.replace('\\', '/').split('/')
        return segments.any { it == ".." || it == "." }
    }

    private fun checkedAdd(current: Long, increment: Long): Long {
        if (increment > Long.MAX_VALUE - current) {
            throw ZipInspectionLimitException("Uncompressed byte count overflow")
        }
        return current + increment
    }
}

data class ZipInspectionLimits(
    val maxEntries: Int = 100_000,
    val maxSingleEntryBytes: Long = 2L * 1024L * 1024L * 1024L,
    val maxTotalUncompressedBytes: Long = 10L * 1024L * 1024L * 1024L
)

data class ZipEntryReport(
    val name: String,
    val isDirectory: Boolean,
    val declaredSizeBytes: Long?,
    val declaredCompressedSizeBytes: Long?,
    val actualSizeBytes: Long,
    val crc32: Long?,
    val hasUnsafePath: Boolean,
    val isDuplicateName: Boolean
)

data class ZipInspectionReport(
    val entries: List<ZipEntryReport>,
    val fileCount: Int,
    val directoryCount: Int,
    val totalUncompressedBytes: Long,
    val unsafePathCount: Int,
    val duplicateNameCount: Int
)

class ZipInspectionLimitException(message: String) : Exception(message)
class ZipInspectionCancelledException : Exception("ZIP inspection cancelled")
