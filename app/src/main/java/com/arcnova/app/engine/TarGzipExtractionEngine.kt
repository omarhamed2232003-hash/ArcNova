package com.arcnova.app.engine

import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

/**
 * Streaming TAR extraction and GZIP decompression backed by Apache Commons Compress.
 *
 * TAR is a multi-entry container. GZIP is a single-stream codec and therefore
 * requires an explicit output filename; a filename stored in the GZIP header is
 * never trusted as a destination path.
 */
object TarGzipExtractionEngine {
    private const val BUFFER_SIZE = 64 * 1024

    fun extractTar(
        input: InputStream,
        destination: File,
        maxArchiveBytes: Long,
        policy: ExtractionPolicy = ExtractionPolicy(),
        cancellationRequested: () -> Boolean = { false },
        onProgress: (ArchiveExtractionProgress) -> Unit = {}
    ): ArchiveExtractionResult {
        require(maxArchiveBytes > 0L) { "maxArchiveBytes must be positive" }
        validatePolicy(policy)

        val inputCounter = BoundedArchiveInputStream(input, maxArchiveBytes, cancellationRequested)
        val nonClosing = NonClosingInputStream(inputCounter)
        val tar = TarArchiveInputStream(nonClosing)
        val destinationExisted = destination.exists()
        require(destinationExisted || destination.mkdirs()) { "Cannot create destination directory" }
        require(destination.isDirectory) { "Destination must be a directory" }

        val root = destination.canonicalFile
        val createdFiles = mutableListOf<File>()
        val createdDirectories = mutableListOf<File>()
        val seenPaths = linkedMapOf<String, Boolean>()
        val buffer = ByteArray(BUFFER_SIZE)
        var entries = 0
        var files = 0
        var directories = 0
        var totalBytes = 0L
        var operationFailure: Exception? = null
        var closeAttempted = false

        try {
            while (true) {
                checkCancelled(cancellationRequested, totalBytes)
                val entry = tar.nextTarEntry ?: break
                entries++
                if (entries > policy.maxEntries) {
                    throw ArchiveExtractionLimitException("Entry count exceeds ${policy.maxEntries}", totalBytes)
                }

                val relative = sanitizePath(entry.name, policy)
                val normalized = relative.replace(File.separatorChar, '/')
                val isDirectory = entry.isDirectory
                if (!isDirectory && !entry.isFile) {
                    throw ArchiveExtractionUnsupportedFeatureException(
                        "TAR entry type is not a regular file or directory: ${entry.name}",
                        totalBytes
                    )
                }
                if (seenPaths.containsKey(normalized)) {
                    throw ArchiveExtractionException("Duplicate normalized TAR path: $normalized", totalBytes)
                }
                val segments = normalized.split('/')
                for (index in 1 until segments.size) {
                    val ancestor = segments.take(index).joinToString("/")
                    if (seenPaths[ancestor] == false) {
                        throw ArchiveExtractionException("TAR file path is also used as a parent: $ancestor", totalBytes)
                    }
                }
                if (!isDirectory && seenPaths.keys.any { it.startsWith("$normalized/") }) {
                    throw ArchiveExtractionException("TAR file path conflicts with an existing child: $normalized", totalBytes)
                }
                seenPaths[normalized] = isDirectory

                val output = File(root, relative).canonicalFile
                ensureContained(output, root, entry.name, totalBytes)

                if (isDirectory) {
                    if (entry.size != 0L) {
                        throw ArchiveExtractionException("TAR directory has a non-zero payload: ${entry.name}", totalBytes)
                    }
                    ensureDirectory(output, root, createdDirectories, totalBytes)
                    directories++
                    onProgress(ArchiveExtractionProgress(entries, files, directories, totalBytes, relative))
                    continue
                }

                if (entry.size < 0L || entry.size > policy.maxSingleFileBytes) {
                    throw ArchiveExtractionLimitException("TAR entry exceeds per-file limit: ${entry.name}", totalBytes)
                }
                if (entry.size > policy.maxTotalOutputBytes - totalBytes) {
                    throw ArchiveExtractionLimitException("Declared TAR output exceeds total limit", totalBytes)
                }

                val parent = output.parentFile ?: throw UnsafeArchivePathException(entry.name)
                ensureDirectory(parent, root, createdDirectories, totalBytes)
                if (output.exists() || !output.createNewFile()) {
                    throw ArchiveExtractionException("Refusing to overwrite: $relative", totalBytes)
                }
                createdFiles.add(output)

                var entryBytes = 0L
                FileOutputStream(output, false).use { out ->
                    while (true) {
                        checkCancelled(cancellationRequested, totalBytes)
                        val count = tar.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        entryBytes = safeAdd(entryBytes, count.toLong(), totalBytes)
                        totalBytes = safeAdd(totalBytes, count.toLong(), totalBytes)
                        if (entryBytes > entry.size || entryBytes > policy.maxSingleFileBytes ||
                            totalBytes > policy.maxTotalOutputBytes
                        ) {
                            throw ArchiveExtractionLimitException("Actual TAR output exceeds configured limits", totalBytes)
                        }
                        enforceExpansionRatio(totalBytes, inputCounter.bytesRead, policy.maxExpansionRatio)
                        out.write(buffer, 0, count)
                    }
                }
                if (entryBytes != entry.size) {
                    throw ArchiveExtractionIntegrityException(
                        "TAR entry size mismatch for ${entry.name}: expected ${entry.size}, got $entryBytes",
                        totalBytes
                    )
                }
                files++
                onProgress(ArchiveExtractionProgress(entries, files, directories, totalBytes, relative))
            }

            tar.close()
            closeAttempted = true
            return ArchiveExtractionResult(entries, files, directories, totalBytes, root)
        } catch (error: Exception) {
            operationFailure = error
            rollback(createdFiles, createdDirectories, if (destinationExisted) null else root, error)
            throw error
        } finally {
            if (!closeAttempted) {
                try {
                    tar.close()
                } catch (closeError: Exception) {
                    val primary = operationFailure
                    if (primary != null) primary.addSuppressed(closeError) else {
                        rollback(createdFiles, createdDirectories, if (destinationExisted) null else root, closeError)
                        throw closeError
                    }
                }
            }
        }
    }

    /**
     * Decompresses a GZIP stream into one caller-named file.
     * The GZIP header's optional original filename is deliberately ignored.
     */
    fun decompressGzip(
        input: InputStream,
        destination: File,
        outputFileName: String,
        maxInputBytes: Long,
        policy: ExtractionPolicy = ExtractionPolicy(),
        cancellationRequested: () -> Boolean = { false },
        onProgress: (ArchiveExtractionProgress) -> Unit = {}
    ): ArchiveExtractionResult {
        require(maxInputBytes > 0L) { "maxInputBytes must be positive" }
        validatePolicy(policy)

        val relative = sanitizePath(outputFileName, policy)
        val destinationExisted = destination.exists()
        require(destinationExisted || destination.mkdirs()) { "Cannot create destination directory" }
        require(destination.isDirectory) { "Destination must be a directory" }
        val root = destination.canonicalFile
        val output = File(root, relative).canonicalFile
        ensureContained(output, root, outputFileName, 0L)

        val parent = output.parentFile ?: throw UnsafeArchivePathException(outputFileName)
        val createdDirectories = mutableListOf<File>()
        val createdFiles = mutableListOf<File>()
        try {
            ensureDirectory(parent, root, createdDirectories, 0L)
            if (output.exists() || !output.createNewFile()) {
                throw ArchiveExtractionException("Refusing to overwrite: $relative", 0L)
            }
            createdFiles.add(output)
        } catch (error: Exception) {
            rollback(createdFiles, createdDirectories, if (destinationExisted) null else root, error)
            throw error
        }

        val inputCounter = BoundedArchiveInputStream(input, maxInputBytes, cancellationRequested)
        val gzip = try {
            GzipCompressorInputStream(NonClosingInputStream(inputCounter), true)
        } catch (error: Exception) {
            rollback(createdFiles, createdDirectories, if (destinationExisted) null else root, error)
            throw error
        }
        val buffer = ByteArray(BUFFER_SIZE)
        var totalBytes = 0L
        var operationFailure: Exception? = null
        var closeAttempted = false

        try {
            FileOutputStream(output, false).use { out ->
                while (true) {
                    checkCancelled(cancellationRequested, totalBytes)
                    val count = gzip.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    totalBytes = safeAdd(totalBytes, count.toLong(), totalBytes)
                    if (totalBytes > policy.maxSingleFileBytes ||
                        totalBytes > policy.maxTotalOutputBytes
                    ) {
                        throw ArchiveExtractionLimitException("GZIP output exceeds configured limits", totalBytes)
                    }
                    enforceExpansionRatio(totalBytes, inputCounter.bytesRead, policy.maxExpansionRatio)
                    out.write(buffer, 0, count)
                    onProgress(ArchiveExtractionProgress(1, 1, createdDirectories.size, totalBytes, relative))
                }
            }
            gzip.close()
            closeAttempted = true
            return ArchiveExtractionResult(1, 1, createdDirectories.size, totalBytes, root)
        } catch (error: Exception) {
            operationFailure = error
            rollback(createdFiles, createdDirectories, if (destinationExisted) null else root, error)
            throw error
        } finally {
            if (!closeAttempted) {
                try {
                    gzip.close()
                } catch (closeError: Exception) {
                    val primary = operationFailure
                    if (primary != null) primary.addSuppressed(closeError) else {
                        rollback(createdFiles, createdDirectories, if (destinationExisted) null else root, closeError)
                        throw closeError
                    }
                }
            }
        }
    }

    private fun validatePolicy(policy: ExtractionPolicy) {
        require(policy.maxEntries > 0) { "maxEntries must be positive" }
        require(policy.maxSingleFileBytes >= 0L) { "maxSingleFileBytes must not be negative" }
        require(policy.maxTotalOutputBytes >= 0L) { "maxTotalOutputBytes must not be negative" }
        require(policy.maxPathLength > 0) { "maxPathLength must be positive" }
        require(policy.maxPathDepth > 0) { "maxPathDepth must be positive" }
        require(policy.maxExpansionRatio.isFinite() && policy.maxExpansionRatio > 0.0) {
            "maxExpansionRatio must be finite and greater than zero"
        }
    }

    private fun sanitizePath(name: String, policy: ExtractionPolicy): String {
        if (name.isBlank() || name.indexOf(0.toChar()) >= 0) throw UnsafeArchivePathException(name)
        val normalized = name.replace('\\', '/')
        if (normalized.startsWith('/') || Regex("^[a-zA-Z]:").containsMatchIn(normalized)) {
            throw UnsafeArchivePathException(name)
        }
        val parts = normalized.split('/')
        val meaningful = parts.dropLastWhile { it.isEmpty() }
        if (meaningful.isEmpty() || meaningful.any { it.isEmpty() || it == "." || it == ".." }) {
            throw UnsafeArchivePathException(name)
        }
        if (normalized.length > policy.maxPathLength || meaningful.size > policy.maxPathDepth) {
            throw ArchiveExtractionLimitException("Archive path exceeds configured length or depth: $name", 0L)
        }
        return meaningful.joinToString(File.separator)
    }

    private fun ensureContained(output: File, root: File, entryName: String, bytes: Long) {
        if (output != root && !output.path.startsWith(root.path + File.separator)) {
            throw UnsafeArchivePathException(entryName)
        }
    }

    private fun ensureDirectory(
        directory: File,
        root: File,
        createdDirectories: MutableList<File>,
        bytesWritten: Long
    ) {
        if (directory == root) return
        ensureContained(directory, root, directory.path, bytesWritten)
        if (directory.exists()) {
            if (!directory.isDirectory) {
                throw ArchiveExtractionException("Path component is not a directory: $directory", bytesWritten)
            }
            return
        }
        val parent = directory.parentFile
            ?: throw ArchiveExtractionException("Cannot resolve parent directory: $directory", bytesWritten)
        ensureDirectory(parent, root, createdDirectories, bytesWritten)
        if (directory.mkdir()) createdDirectories.add(directory)
        else if (!directory.isDirectory) {
            throw ArchiveExtractionException("Cannot create directory: $directory", bytesWritten)
        }
    }

    private fun enforceExpansionRatio(outputBytes: Long, inputBytes: Long, maximum: Double) {
        if (outputBytes > 0L && inputBytes > 0L &&
            outputBytes.toDouble() / inputBytes.toDouble() > maximum
        ) {
            throw ArchiveExtractionLimitException("Expansion ratio exceeds configured limit", outputBytes)
        }
    }

    private fun safeAdd(current: Long, increment: Long, bytesWritten: Long): Long {
        if (increment > Long.MAX_VALUE - current) {
            throw ArchiveExtractionLimitException("Output byte counter overflow", bytesWritten)
        }
        return current + increment
    }

    private fun checkCancelled(cancel: () -> Boolean, bytesWritten: Long) {
        if (cancel()) throw ArchiveExtractionCancelledException(bytesWritten)
    }

    private fun rollback(
        files: List<File>,
        directories: List<File>,
        createdRoot: File?,
        originalError: Exception?
    ) {
        files.asReversed().forEach { deleteForRollback(it, originalError) }
        directories.asReversed().forEach { deleteForRollback(it, originalError) }
        if (createdRoot != null) deleteForRollback(createdRoot, originalError)
    }

    private fun deleteForRollback(file: File, originalError: Exception?) {
        try {
            if (file.exists() && !file.delete()) throw IOException("Rollback could not remove created path: $file")
        } catch (cleanupError: Exception) {
            if (originalError != null) originalError.addSuppressed(cleanupError) else throw cleanupError
        }
    }

    private class NonClosingInputStream(input: InputStream) : FilterInputStream(input) {
        override fun close() = Unit
    }

    private class BoundedArchiveInputStream(
        input: InputStream,
        private val maximumBytes: Long,
        private val cancellationRequested: () -> Boolean
    ) : FilterInputStream(input) {
        var bytesRead: Long = 0L
            private set

        override fun read(): Int {
            checkCancelled(cancellationRequested, 0L)
            if (bytesRead >= maximumBytes) {
                val extra = super.read()
                if (extra < 0) return -1
                throw ArchiveExtractionLimitException("Archive input exceeds $maximumBytes bytes", 0L)
            }
            val value = super.read()
            if (value >= 0) bytesRead++
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            checkCancelled(cancellationRequested, 0L)
            if (bytesRead >= maximumBytes) return read()
            val allowed = minOf(length.toLong(), maximumBytes - bytesRead).toInt()
            val count = super.read(buffer, offset, allowed)
            if (count > 0) bytesRead += count
            return count
        }

        override fun close() = Unit
    }
}

data class ArchiveExtractionProgress(
    val entriesProcessed: Int,
    val filesExtracted: Int,
    val directoriesCreated: Int,
    val bytesWritten: Long,
    val currentEntry: String
)

data class ArchiveExtractionResult(
    val entriesProcessed: Int,
    val filesExtracted: Int,
    val directoriesCreated: Int,
    val bytesWritten: Long,
    val destination: File
)

open class ArchiveExtractionException(message: String, val bytesWritten: Long = 0L) : IOException(message)
class ArchiveExtractionLimitException(message: String, bytesWritten: Long) :
    ArchiveExtractionException(message, bytesWritten)
class ArchiveExtractionCancelledException(bytesWritten: Long) :
    ArchiveExtractionException("Archive extraction cancelled", bytesWritten)
class ArchiveExtractionIntegrityException(message: String, bytesWritten: Long) :
    ArchiveExtractionException(message, bytesWritten)
class ArchiveExtractionUnsupportedFeatureException(message: String, bytesWritten: Long) :
    ArchiveExtractionException(message, bytesWritten)
