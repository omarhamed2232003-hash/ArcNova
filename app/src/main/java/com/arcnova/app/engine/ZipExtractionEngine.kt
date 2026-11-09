package com.arcnova.app.engine

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Bounded ZIP extraction backend.
 *
 * Extracts into a caller-provided private directory. It rejects unsafe paths,
 * duplicate output paths, excessive entry counts, oversized files and archives
 * whose observed expansion ratio exceeds policy. It never overwrites files.
 */
object ZipExtractionEngine {
    private const val BUFFER_SIZE = 64 * 1024

    fun extract(
        input: InputStream,
        destination: File,
        policy: ExtractionPolicy = ExtractionPolicy(),
        cancellationRequested: () -> Boolean = { false },
        onProgress: (ZipExtractionProgress) -> Unit = {}
    ): ZipExtractionResult {
        require(policy.maxEntries > 0) { "maxEntries must be positive" }
        require(policy.maxSingleFileBytes >= 0L) { "maxSingleFileBytes must not be negative" }
        require(policy.maxTotalOutputBytes >= 0L) { "maxTotalOutputBytes must not be negative" }
        require(policy.maxPathLength > 0) { "maxPathLength must be positive" }
        require(policy.maxPathDepth > 0) { "maxPathDepth must be positive" }
        require(policy.maxExpansionRatio.isFinite() && policy.maxExpansionRatio > 0.0) {
            "maxExpansionRatio must be finite and greater than zero"
        }
        require(destination.exists() || destination.mkdirs()) { "Cannot create destination directory" }
        require(destination.isDirectory) { "Destination must be a directory" }

        val root = destination.canonicalFile
        var entries = 0
        var totalBytes = 0L
        var files = 0
        var directories = 0
        val buffer = ByteArray(BUFFER_SIZE)
        val seenOutputPaths = mutableSetOf<String>()

        // Do not close the caller-owned input stream; the caller controls its lifecycle.
        val zip = ZipInputStream(BufferedInputStream(input))
        while (true) {
                checkCancelled(cancellationRequested, totalBytes)
                val entry = zip.nextEntry ?: break
                entries++
                if (entries > policy.maxEntries) {
                    throw ZipExtractionLimitException("Entry count exceeds ${policy.maxEntries}", totalBytes)
                }

                val relative = sanitizeEntryName(entry.name, policy)
                val output = File(root, relative).canonicalFile
                if (!seenOutputPaths.add(output.path)) {
                    throw ZipExtractionException("Duplicate normalized output path: $relative", totalBytes)
                }
                if (output != root && !output.path.startsWith(root.path + File.separator)) {
                    throw UnsafeArchivePathException(entry.name)
                }

                if (entry.isDirectory) {
                    if (!output.exists() && !output.mkdirs()) {
                        throw ZipExtractionException("Cannot create directory: $relative", totalBytes)
                    }
                    if (!output.isDirectory) {
                        throw ZipExtractionException("Directory conflicts with an existing file: $relative", totalBytes)
                    }
                    directories++
                    zip.closeEntry()
                    onProgress(ZipExtractionProgress(entries, files, directories, totalBytes, relative))
                    continue
                }

                val parent = output.parentFile
                    ?: throw UnsafeArchivePathException(entry.name)
                if (!parent.exists() && !parent.mkdirs()) {
                    throw ZipExtractionException("Cannot create parent directory for: $relative", totalBytes)
                }
                if (!parent.isDirectory) {
                    throw ZipExtractionException("Parent path is not a directory: $relative", totalBytes)
                }
                if (output.exists()) {
                    throw ZipExtractionException("Refusing to overwrite: $relative", totalBytes)
                }

                var entryBytes = 0L
                var created = false
                try {
                    FileOutputStream(output, false).use { out ->
                        created = true
                        while (true) {
                            checkCancelled(cancellationRequested, totalBytes + entryBytes)
                            val count = zip.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            entryBytes = safeAdd(entryBytes, count.toLong())
                            totalBytes = safeAdd(totalBytes, count.toLong())

                            if (entryBytes > policy.maxSingleFileBytes) {
                                throw ZipExtractionLimitException("File exceeds per-file limit: $relative", totalBytes)
                            }
                            if (totalBytes > policy.maxTotalOutputBytes) {
                                throw ZipExtractionLimitException("Total extracted data exceeds limit", totalBytes)
                            }
                            out.write(buffer, 0, count)
                        }
                    }
                    val compressed = entry.compressedSize
                    if (compressed > 0L &&
                        entryBytes.toDouble() / compressed.toDouble() > policy.maxExpansionRatio
                    ) {
                        throw ZipExtractionLimitException("Expansion ratio exceeds limit: $relative", totalBytes)
                    }
                    files++
                    onProgress(ZipExtractionProgress(entries, files, directories, totalBytes, relative))
                } catch (error: Exception) {
                    if (created) output.delete()
                    throw error
                } finally {
                    zip.closeEntry()
                }
            }

        return ZipExtractionResult(entries, files, directories, totalBytes, root)
    }

    private fun sanitizeEntryName(name: String, policy: ExtractionPolicy): String {
        if (name.isBlank() || name.indexOf('\u0000') >= 0) throw UnsafeArchivePathException(name)
        val normalized = name.replace('\\', '/')
        if (normalized.startsWith('/') || normalized.startsWith("//") ||
            Regex("^[a-zA-Z]:").containsMatchIn(normalized)
        ) throw UnsafeArchivePathException(name)

        val parts = normalized.split('/')
        val meaningfulParts = parts.dropLastWhile { it.isEmpty() }
        if (meaningfulParts.isEmpty() || meaningfulParts.any { it == ".." || it == "." || it.isEmpty() }) {
            throw UnsafeArchivePathException(name)
        }
        val safe = parts.filter { it.isNotEmpty() }
        if (safe.isEmpty()) throw UnsafeArchivePathException(name)
        if (normalized.length > policy.maxPathLength || safe.size > policy.maxPathDepth) {
            throw ZipExtractionLimitException("Entry path exceeds configured length or depth: $name", 0L)
        }
        return safe.joinToString(File.separator)
    }

    private fun checkCancelled(cancel: () -> Boolean, bytes: Long) {
        if (cancel()) throw ZipExtractionCancelledException(bytes)
    }

    private fun safeAdd(a: Long, b: Long): Long {
        if (b > Long.MAX_VALUE - a) throw ZipExtractionLimitException("Byte counter overflow", a)
        return a + b
    }
}

data class ZipExtractionProgress(
    val entriesProcessed: Int,
    val filesExtracted: Int,
    val directoriesCreated: Int,
    val bytesWritten: Long,
    val currentEntry: String
)

data class ZipExtractionResult(
    val entriesProcessed: Int,
    val filesExtracted: Int,
    val directoriesCreated: Int,
    val bytesWritten: Long,
    val destination: File
)

open class ZipExtractionException(message: String, val bytesWritten: Long = 0L) : Exception(message)
class UnsafeArchivePathException(val entryName: String) :
    ZipExtractionException("Unsafe ZIP entry path: $entryName")
class ZipExtractionLimitException(message: String, bytesWritten: Long) :
    ZipExtractionException(message, bytesWritten)
class ZipExtractionCancelledException(bytesWritten: Long) :
    ZipExtractionException("ZIP extraction cancelled", bytesWritten)
