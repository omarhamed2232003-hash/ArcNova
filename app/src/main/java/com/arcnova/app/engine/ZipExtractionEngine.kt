package com.arcnova.app.engine

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Bounded ZIP extraction backend.
 *
 * Extracts into a caller-provided private directory. It rejects unsafe paths,
 * duplicate output paths, excessive entry counts, oversized files and archives
 * whose observed expansion ratio exceeds policy. It never overwrites files.
 *
 * The operation is transactional for filesystem objects it creates: if any
 * entry, callback, limit check, or cancellation fails, created files and empty
 * directories are removed while pre-existing destination content is preserved.
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

        val destinationExisted = destination.exists()
        require(destinationExisted || destination.mkdirs()) { "Cannot create destination directory" }
        require(destination.isDirectory) { "Destination must be a directory" }

        val root = destination.canonicalFile
        val createdFiles = mutableListOf<File>()
        val createdDirectories = mutableListOf<File>()
        var entries = 0
        var totalBytes = 0L
        var files = 0
        var directories = 0
        val buffer = ByteArray(BUFFER_SIZE)
        val seenOutputPaths = mutableSetOf<String>()

        // Do not close the caller-owned input stream; the caller controls its lifecycle.
        val zip = ZipInputStream(BufferedInputStream(input))
        try {
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
                    ensureDirectory(output, root, createdDirectories, totalBytes)
                    directories++
                    zip.closeEntry()
                    onProgress(ZipExtractionProgress(entries, files, directories, totalBytes, relative))
                    continue
                }

                val parent = output.parentFile
                    ?: throw UnsafeArchivePathException(entry.name)
                ensureDirectory(parent, root, createdDirectories, totalBytes)

                if (output.exists() || !output.createNewFile()) {
                    throw ZipExtractionException("Refusing to overwrite: $relative", totalBytes)
                }
                // Register immediately after exclusive creation so every later failure rolls it back.
                createdFiles.add(output)

                var entryBytes = 0L
                try {
                    FileOutputStream(output, false).use { out ->
                        while (true) {
                            checkCancelled(cancellationRequested, safeAdd(totalBytes, entryBytes))
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
                } finally {
                    zip.closeEntry()
                }
            }

            return ZipExtractionResult(entries, files, directories, totalBytes, root)
        } catch (error: Exception) {
            rollback(createdFiles, createdDirectories, if (destinationExisted) null else root, error)
            throw error
        }
    }


    /**
     * Central-Directory-first extraction path. Uses the same filesystem transaction
     * and rollback rules as [extract], while Commons Compress validates each entry.
     * The archive is first spooled to bounded private cache storage.
     */
    fun extractCentralDirectory(
        input: InputStream,
        destination: File,
        cacheDirectory: File,
        maxArchiveBytes: Long,
        policy: ExtractionPolicy = ExtractionPolicy(),
        cancellationRequested: () -> Boolean = { false },
        onProgress: (ZipExtractionProgress) -> Unit = {}
    ): ZipExtractionResult {
        require(maxArchiveBytes > 0L) { "maxArchiveBytes must be positive" }
        require(policy.maxEntries > 0) { "maxEntries must be positive" }
        require(policy.maxSingleFileBytes >= 0L) { "maxSingleFileBytes must not be negative" }
        require(policy.maxTotalOutputBytes >= 0L) { "maxTotalOutputBytes must not be negative" }
        require(policy.maxPathLength > 0) { "maxPathLength must be positive" }
        require(policy.maxPathDepth > 0) { "maxPathDepth must be positive" }
        require(policy.maxExpansionRatio.isFinite() && policy.maxExpansionRatio > 0.0) {
            "maxExpansionRatio must be finite and greater than zero"
        }

        val destinationExisted = destination.exists()
        require(destinationExisted || destination.mkdirs()) { "Cannot create destination directory" }
        require(destination.isDirectory) { "Destination must be a directory" }
        val root = destination.canonicalFile
        val createdFiles = mutableListOf<File>()
        val createdDirectories = mutableListOf<File>()
        var entriesProcessed = 0
        var filesExtracted = 0
        var directoriesCreated = 0
        var totalBytes = 0L

        val backendPolicy = CentralDirectoryZipPolicy(
            maxEntries = policy.maxEntries,
            maxSingleFileBytes = policy.maxSingleFileBytes,
            maxTotalOutputBytes = policy.maxTotalOutputBytes,
            maxPathLength = policy.maxPathLength,
            maxPathDepth = policy.maxPathDepth,
            maxExpansionRatio = policy.maxExpansionRatio
        )

        var archive: CentralDirectoryZipArchive? = null
        var operationFailure: Exception? = null
        try {
            checkCancelled(cancellationRequested, totalBytes)
            archive = CentralDirectoryZipBackend.open(
                input = input,
                cacheDirectory = cacheDirectory,
                maxArchiveBytes = maxArchiveBytes,
                policy = backendPolicy,
                cancellationRequested = cancellationRequested
            )

            for (entry in archive.entries) {
                checkCancelled(cancellationRequested, totalBytes)
                entriesProcessed++
                val relative = sanitizeEntryName(entry.name, policy)
                val output = File(root, relative).canonicalFile
                if (output != root && !output.path.startsWith(root.path + File.separator)) {
                    throw UnsafeArchivePathException(entry.name)
                }

                if (entry.isDirectory) {
                    ensureDirectory(output, root, createdDirectories, totalBytes)
                    directoriesCreated++
                    onProgress(
                        ZipExtractionProgress(
                            entriesProcessed, filesExtracted, directoriesCreated, totalBytes, relative
                        )
                    )
                    continue
                }

                val parent = output.parentFile ?: throw UnsafeArchivePathException(entry.name)
                ensureDirectory(parent, root, createdDirectories, totalBytes)
                if (output.exists() || !output.createNewFile()) {
                    throw ZipExtractionException("Refusing to overwrite: $relative", totalBytes)
                }
                createdFiles.add(output)

                val copied = FileOutputStream(output, false).use { out ->
                    archive.copyEntry(entry, out)
                }
                totalBytes = safeAdd(totalBytes, copied.bytesCopied)
                filesExtracted++
                onProgress(
                    ZipExtractionProgress(
                        entriesProcessed, filesExtracted, directoriesCreated, totalBytes, relative
                    )
                )
            }

            // Close the backend before committing success, so temp-file cleanup failures roll back too.
            archive.close()
            archive = null
            return ZipExtractionResult(
                entriesProcessed, filesExtracted, directoriesCreated, totalBytes, root
            )
        } catch (error: Exception) {
            val reportedError = when (error) {
                is ArchiveSpoolCancelledException -> ZipExtractionCancelledException(error.copiedBytes)
                is ArchiveSpoolLimitException -> ZipExtractionLimitException(
                    error.message ?: "Archive exceeds configured input limit",
                    error.copiedBytes
                )
                is CentralDirectoryZipCancelledException -> ZipExtractionCancelledException(totalBytes)
                is CentralDirectoryZipLimitException -> ZipExtractionLimitException(
                    error.message ?: "ZIP extraction limit exceeded",
                    totalBytes
                )
                is CentralDirectoryZipUnsupportedFeatureException -> ZipExtractionUnsupportedFeatureException(
                    error.entryName,
                    totalBytes,
                    error
                )
                else -> error
            }
            operationFailure = reportedError
            rollback(
                createdFiles,
                createdDirectories,
                if (destinationExisted) null else root,
                reportedError
            )
            throw reportedError
        } finally {
            archive?.let { opened ->
                try {
                    opened.close()
                } catch (closeError: Exception) {
                    val primary = operationFailure
                    if (primary != null) primary.addSuppressed(closeError) else throw closeError
                }
            }
        }
    }

    private fun ensureDirectory(
        directory: File,
        root: File,
        createdDirectories: MutableList<File>,
        bytesWritten: Long
    ) {
        if (directory == root) return
        if (!directory.path.startsWith(root.path + File.separator)) {
            throw ZipExtractionException("Directory escapes extraction destination: $directory", bytesWritten)
        }
        if (directory.exists()) {
            if (!directory.isDirectory) {
                throw ZipExtractionException("Path component is not a directory: $directory", bytesWritten)
            }
            return
        }

        val parent = directory.parentFile
            ?: throw ZipExtractionException("Cannot resolve parent directory: $directory", bytesWritten)
        ensureDirectory(parent, root, createdDirectories, bytesWritten)
        if (directory.mkdir()) {
            createdDirectories.add(directory)
        } else if (!directory.isDirectory) {
            throw ZipExtractionException("Cannot create directory: $directory", bytesWritten)
        }
    }

    private fun rollback(
        createdFiles: List<File>,
        createdDirectories: List<File>,
        createdRoot: File?,
        originalError: Exception
    ) {
        createdFiles.asReversed().forEach { deleteForRollback(it, originalError) }
        createdDirectories.asReversed().forEach { deleteForRollback(it, originalError) }
        if (createdRoot != null) deleteForRollback(createdRoot, originalError)
    }

    private fun deleteForRollback(file: File, originalError: Exception) {
        try {
            if (file.exists() && !file.delete()) {
                throw IOException("Rollback could not remove created path: $file")
            }
        } catch (cleanupError: Exception) {
            originalError.addSuppressed(cleanupError)
        }
    }

    private fun sanitizeEntryName(name: String, policy: ExtractionPolicy): String {
        if (name.isBlank() || name.indexOf(0.toChar()) >= 0) throw UnsafeArchivePathException(name)
        val normalized = name.replace(92.toChar(), '/')
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
class ZipExtractionUnsupportedFeatureException(
    val entryName: String,
    bytesWritten: Long,
    cause: Throwable? = null
) : ZipExtractionException(
    "ZIP entry uses encryption or an unsupported compression method: $entryName",
    bytesWritten
) {
    init {
        cause?.let(::initCause)
    }
}
