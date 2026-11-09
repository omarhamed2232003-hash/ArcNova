package com.arcnova.app.engine

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Collections
import java.util.zip.CRC32
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile

/**
 * Central-Directory-first ZIP reader built on Commons Compress.
 *
 * Opening performs a complete metadata preflight before callers can write output.
 * Payload copying is explicit so the caller can connect it to ArcNova's existing
 * transactional writer. This class does not create destination files itself.
 */
object CentralDirectoryZipBackend {
    fun open(
        input: InputStream,
        cacheDirectory: File,
        maxArchiveBytes: Long,
        policy: CentralDirectoryZipPolicy = CentralDirectoryZipPolicy(),
        cancellationRequested: () -> Boolean = { false }
    ): CentralDirectoryZipArchive {
        val spool = SeekableArchiveSpool.create(
            input = input,
            cacheDirectory = cacheDirectory,
            maxArchiveBytes = maxArchiveBytes,
            cancellationRequested = cancellationRequested
        )
        try {
            val zip = ZipFile.builder().setFile(spool.file).get()
            try {
                val entries = Collections.list(zip.entries)
                preflight(entries, policy, cancellationRequested)
                return CentralDirectoryZipArchive(
                    spool = spool,
                    zipFile = zip,
                    entries = entries,
                    policy = policy,
                    cancellationRequested = cancellationRequested
                )
            } catch (error: Exception) {
                try {
                    zip.close()
                } catch (closeError: Exception) {
                    error.addSuppressed(closeError)
                }
                throw error
            }
        } catch (error: Exception) {
            try {
                spool.close()
            } catch (closeError: Exception) {
                error.addSuppressed(closeError)
            }
            throw error
        }
    }

    private fun preflight(
        entries: List<ZipArchiveEntry>,
        policy: CentralDirectoryZipPolicy,
        cancellationRequested: () -> Boolean
    ) {
        if (entries.size > policy.maxEntries) {
            throw CentralDirectoryZipException("Entry count exceeds ${policy.maxEntries}")
        }
        val seenPaths = mutableSetOf<String>()
        var declaredTotal = 0L
        entries.forEach { entry ->
            if (cancellationRequested()) throw CentralDirectoryZipCancelledException()
            val normalized = normalizePath(entry.name, policy)
            if (!seenPaths.add(normalized)) {
                throw CentralDirectoryZipException("Duplicate normalized output path: $normalized")
            }
            if (entry.size < 0L || entry.compressedSize < 0L) {
                throw CentralDirectoryZipException("Entry has unknown or invalid size metadata: ${entry.name}")
            }
            if (entry.isDirectory && entry.size != 0L) {
                throw CentralDirectoryZipException("Directory entry has non-zero payload: ${entry.name}")
            }
            if (!entry.isDirectory && entry.size > policy.maxSingleFileBytes) {
                throw CentralDirectoryZipException("File exceeds per-file limit: ${entry.name}")
            }
            if (entry.size > Long.MAX_VALUE - declaredTotal) {
                throw CentralDirectoryZipException("Declared output size overflow")
            }
            declaredTotal += entry.size
            if (declaredTotal > policy.maxTotalOutputBytes) {
                throw CentralDirectoryZipException("Declared total output exceeds configured limit")
            }
            if (entry.size > 0L) {
                if (entry.compressedSize == 0L ||
                    entry.size.toDouble() / entry.compressedSize.toDouble() > policy.maxExpansionRatio
                ) {
                    throw CentralDirectoryZipException("Declared expansion ratio exceeds limit: ${entry.name}")
                }
            }
        }
    }

    internal fun normalizePath(name: String, policy: CentralDirectoryZipPolicy): String {
        if (name.isBlank() || name.indexOf(0.toChar()) >= 0) {
            throw CentralDirectoryZipUnsafePathException(name)
        }
        val normalized = name.replace('\\', '/')
        if (normalized.startsWith('/') || Regex("^[a-zA-Z]:").containsMatchIn(normalized)) {
            throw CentralDirectoryZipUnsafePathException(name)
        }
        val segments = normalized.split('/')
        val meaningful = segments.dropLastWhile { it.isEmpty() }
        if (meaningful.isEmpty() || meaningful.any { it.isEmpty() || it == "." || it == ".." }) {
            throw CentralDirectoryZipUnsafePathException(name)
        }
        if (normalized.length > policy.maxPathLength || meaningful.size > policy.maxPathDepth) {
            throw CentralDirectoryZipException("Entry path exceeds configured length or depth: $name")
        }
        return meaningful.joinToString("/")
    }
}

data class CentralDirectoryZipPolicy(
    val maxEntries: Int = 100_000,
    val maxSingleFileBytes: Long = 2L * 1024L * 1024L * 1024L,
    val maxTotalOutputBytes: Long = 10L * 1024L * 1024L * 1024L,
    val maxPathLength: Int = 4_096,
    val maxPathDepth: Int = 128,
    val maxExpansionRatio: Double = 200.0
) {
    init {
        require(maxEntries > 0)
        require(maxSingleFileBytes >= 0L)
        require(maxTotalOutputBytes >= 0L)
        require(maxPathLength > 0)
        require(maxPathDepth > 0)
        require(maxExpansionRatio.isFinite() && maxExpansionRatio > 0.0)
    }
}

/** Owns both the Commons Compress reader and its temporary seekable archive. */
class CentralDirectoryZipArchive internal constructor(
    private val spool: SeekableArchiveFile,
    private val zipFile: ZipFile,
    val entries: List<ZipArchiveEntry>,
    private val policy: CentralDirectoryZipPolicy,
    private val cancellationRequested: () -> Boolean
) : AutoCloseable {
    private var closed = false
    private var totalCopiedBytes = 0L

    /**
     * Copies one preflighted entry to a caller-owned output stream, validating
     * actual size, expansion ratio and CRC32 while streaming.
     * The output stream remains open; callers own transactional destination cleanup.
     */
    @Synchronized
    fun copyEntry(entry: ZipArchiveEntry, output: OutputStream): CentralDirectoryCopyResult {
        check(!closed) { "Archive is already closed" }
        require(entries.any { it === entry }) { "Entry does not belong to this archive" }
        if (entry.isDirectory) return CentralDirectoryCopyResult(0L, 0L)

        val crc = CRC32()
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        zipFile.getInputStream(entry).use { input ->
            while (true) {
                if (cancellationRequested()) throw CentralDirectoryZipCancelledException()
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                if (count.toLong() > Long.MAX_VALUE - copied ||
                    count.toLong() > Long.MAX_VALUE - totalCopiedBytes
                ) {
                    throw CentralDirectoryZipException("Actual output byte count overflow")
                }
                val nextEntry = copied + count
                val nextTotal = totalCopiedBytes + count
                if (nextEntry > policy.maxSingleFileBytes || nextTotal > policy.maxTotalOutputBytes) {
                    throw CentralDirectoryZipException("Actual output exceeds configured limits")
                }
                output.write(buffer, 0, count)
                crc.update(buffer, 0, count)
                copied = nextEntry
                totalCopiedBytes = nextTotal
            }
        }

        if (copied != entry.size) {
            throw CentralDirectoryZipException(
                "Uncompressed size mismatch for ${entry.name}: expected ${entry.size}, got $copied"
            )
        }
        if (entry.crc >= 0L && crc.value != entry.crc) {
            throw CentralDirectoryZipCrcException(entry.name, entry.crc, crc.value)
        }
        if (entry.compressedSize > 0L &&
            copied.toDouble() / entry.compressedSize.toDouble() > policy.maxExpansionRatio
        ) {
            throw CentralDirectoryZipException("Actual expansion ratio exceeds limit: ${entry.name}")
        }
        return CentralDirectoryCopyResult(copied, crc.value)
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        var failure: Exception? = null
        try {
            zipFile.close()
        } catch (error: Exception) {
            failure = error
        }
        try {
            spool.close()
        } catch (error: Exception) {
            if (failure == null) failure = error else failure.addSuppressed(error)
        }
        failure?.let { throw it }
    }
}

data class CentralDirectoryCopyResult(val bytesCopied: Long, val crc32: Long)

open class CentralDirectoryZipException(message: String) : IOException(message)
class CentralDirectoryZipUnsafePathException(val entryName: String) :
    CentralDirectoryZipException("Unsafe ZIP entry path: $entryName")
class CentralDirectoryZipCancelledException :
    CentralDirectoryZipException("Central Directory ZIP operation cancelled")
class CentralDirectoryZipCrcException(
    val entryName: String,
    val expectedCrc32: Long,
    val actualCrc32: Long
) : CentralDirectoryZipException(
    "CRC32 mismatch for $entryName: expected $expectedCrc32, got $actualCrc32"
)
