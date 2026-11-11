package com.arcnova.app.engine

import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import org.apache.commons.compress.archivers.ar.ArArchiveEntry
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream

/**
 * Bounded extraction for Unix ar and CPIO containers.
 *
 * AR is an outer archive format (including the outer container of Debian .deb packages);
 * nested control/data archives are intentionally left as separate files for a later operation.
 * CPIO regular files and directories are supported. Links and special inode types are rejected.
 */
object ArCpioExtractionEngine {
    private const val BUFFER_SIZE = 64 * 1024
    private const val AR_FILE_TYPE_MASK = 0xF000
    private const val AR_REGULAR_FILE = 0x8000
    private val arMetadataEntries = setOf("/", "/SYM64/", "__.SYMDEF", "__.SYMDEF SORTED", "__.SYMDEF_64")

    private enum class EntryType { FILE, DIRECTORY, UNSUPPORTED, METADATA }

    private data class Entry(
        val name: String,
        val size: Long,
        val type: EntryType
    )

    private interface Reader : AutoCloseable {
        fun nextEntry(): Entry?
        fun read(buffer: ByteArray): Int
    }

    fun extractAr(
        input: InputStream,
        destination: File,
        maxArchiveBytes: Long,
        policy: ExtractionPolicy = ExtractionPolicy(),
        cancellationRequested: () -> Boolean = { false },
        onProgress: (ArchiveExtractionProgress) -> Unit = {}
    ): ArchiveExtractionResult = extract(
        input, destination, maxArchiveBytes, policy, cancellationRequested, onProgress
    ) { source -> ArReader(source) }

    fun extractCpio(
        input: InputStream,
        destination: File,
        maxArchiveBytes: Long,
        policy: ExtractionPolicy = ExtractionPolicy(),
        cancellationRequested: () -> Boolean = { false },
        onProgress: (ArchiveExtractionProgress) -> Unit = {}
    ): ArchiveExtractionResult = extract(
        input, destination, maxArchiveBytes, policy, cancellationRequested, onProgress
    ) { source -> CpioReader(source) }

    private fun extract(
        input: InputStream,
        destination: File,
        maxArchiveBytes: Long,
        policy: ExtractionPolicy,
        cancellationRequested: () -> Boolean,
        onProgress: (ArchiveExtractionProgress) -> Unit,
        readerFactory: (InputStream) -> Reader
    ): ArchiveExtractionResult {
        require(maxArchiveBytes > 0L) { "maxArchiveBytes must be positive" }
        require(policy.maxEntries > 0) { "maxEntries must be positive" }
        require(policy.maxSingleFileBytes >= 0L) { "maxSingleFileBytes must not be negative" }
        require(policy.maxTotalOutputBytes >= 0L) { "maxTotalOutputBytes must not be negative" }
        require(policy.maxPathLength > 0) { "maxPathLength must be positive" }
        require(policy.maxPathDepth > 0) { "maxPathDepth must be positive" }
        require(policy.maxExpansionRatio.isFinite() && policy.maxExpansionRatio > 0.0) {
            "maxExpansionRatio must be finite and greater than zero"
        }

        val bounded = BoundedInputStream(input, maxArchiveBytes, cancellationRequested)
        val destinationExisted = destination.exists()
        var root: File? = null
        val createdFiles = mutableListOf<File>()
        val createdDirectories = mutableListOf<File>()
        val seenPaths = mutableMapOf<String, Boolean>()
        var entries = 0
        var files = 0
        var directories = 0
        var totalBytes = 0L
        val buffer = ByteArray(BUFFER_SIZE)

        try {
            readerFactory(NonClosingInputStream(bounded)).use { reader ->
                if (!destination.exists() && !destination.mkdirs()) {
                    throw ArchiveExtractionException("Cannot create destination directory", 0L)
                }
                if (!destination.isDirectory) {
                    throw ArchiveExtractionException("Destination must be a directory", 0L)
                }
                root = destination.canonicalFile

                while (true) {
                    checkCancelled(cancellationRequested, totalBytes)
                    val entry = reader.nextEntry() ?: break
                    entries++
                    if (entries > policy.maxEntries) {
                        throw ArchiveExtractionLimitException(
                            "Archive entry count exceeds ${policy.maxEntries}", totalBytes
                        )
                    }
                    if (entry.size < 0L) {
                        throw ArchiveExtractionIntegrityException("Negative archive entry size: ${entry.name}", totalBytes)
                    }

                    if (entry.type == EntryType.METADATA) {
                        drainEntry(reader, buffer, cancellationRequested, totalBytes)
                        continue
                    }
                    if (entry.type == EntryType.UNSUPPORTED) {
                        throw ArchiveExtractionUnsupportedFeatureException(
                            "Links and special archive entry types are not supported: ${entry.name}", totalBytes
                        )
                    }

                    val relative = ArchiveExtractionFileSystem.sanitizePath(entry.name, policy)
                    val normalized = relative.replace(File.separatorChar, '/')
                    val isDirectory = entry.type == EntryType.DIRECTORY
                    validatePath(normalized, isDirectory, seenPaths, totalBytes)
                    seenPaths[normalized] = isDirectory

                    val output = File(root!!, relative).canonicalFile
                    ArchiveExtractionFileSystem.ensureContained(output, root!!, entry.name, totalBytes)

                    if (isDirectory) {
                        if (entry.size != 0L) {
                            throw ArchiveExtractionIntegrityException(
                                "Directory entry has a non-zero payload: ${entry.name}", totalBytes
                            )
                        }
                        ArchiveExtractionFileSystem.ensureDirectory(
                            output, root!!, createdDirectories, totalBytes
                        )
                        directories++
                        drainEntry(reader, buffer, cancellationRequested, totalBytes)
                        onProgress(ArchiveExtractionProgress(entries, files, directories, totalBytes, relative))
                        continue
                    }

                    if (entry.size > policy.maxSingleFileBytes ||
                        entry.size > policy.maxTotalOutputBytes - totalBytes
                    ) {
                        throw ArchiveExtractionLimitException(
                            "Archive entry exceeds configured output limits: ${entry.name}", totalBytes
                        )
                    }

                    val parent = output.parentFile ?: throw UnsafeArchivePathException(entry.name)
                    ArchiveExtractionFileSystem.ensureDirectory(
                        parent, root!!, createdDirectories, totalBytes
                    )
                    if (output.exists() || !output.createNewFile()) {
                        throw ArchiveExtractionException("Refusing to overwrite: $relative", totalBytes)
                    }
                    createdFiles.add(output)

                    var entryBytes = 0L
                    FileOutputStream(output, false).use { out ->
                        while (true) {
                            checkCancelled(cancellationRequested, totalBytes)
                            val count = reader.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            entryBytes = safeAdd(entryBytes, count.toLong(), totalBytes)
                            totalBytes = safeAdd(totalBytes, count.toLong(), totalBytes)
                            if (entryBytes > entry.size ||
                                entryBytes > policy.maxSingleFileBytes ||
                                totalBytes > policy.maxTotalOutputBytes
                            ) {
                                throw ArchiveExtractionLimitException(
                                    "Actual archive output exceeds configured limits", totalBytes
                                )
                            }
                            enforceExpansionRatio(totalBytes, bounded.bytesRead, policy.maxExpansionRatio)
                            out.write(buffer, 0, count)
                        }
                    }
                    if (entryBytes != entry.size) {
                        throw ArchiveExtractionIntegrityException(
                            "Archive entry size mismatch for ${entry.name}: expected ${entry.size}, got $entryBytes",
                            totalBytes
                        )
                    }
                    files++
                    onProgress(ArchiveExtractionProgress(entries, files, directories, totalBytes, relative))
                }

                if (entries == 0) {
                    throw ArchiveExtractionIntegrityException("Archive contains no entries", totalBytes)
                }
                ArchiveExtractionResult(entries, files, directories, totalBytes, root!!)
            }
        } catch (error: Exception) {
            ArchiveExtractionFileSystem.rollback(
                createdFiles, createdDirectories, if (destinationExisted) null else root, error
            )
            throw error
        }
    }

    private fun validatePath(
        path: String,
        isDirectory: Boolean,
        seen: Map<String, Boolean>,
        bytesWritten: Long
    ) {
        if (seen.containsKey(path)) {
            throw ArchiveExtractionException("Duplicate normalized archive path: $path", bytesWritten)
        }
        val parts = path.split('/')
        for (index in 1 until parts.size) {
            val parent = parts.take(index).joinToString("/")
            if (seen[parent] == false) {
                throw ArchiveExtractionException("Archive file path is also used as a parent: $parent", bytesWritten)
            }
        }
        if (!isDirectory && seen.keys.any { it.startsWith("$path/") }) {
            throw ArchiveExtractionException("Archive file conflicts with an existing child: $path", bytesWritten)
        }
    }

    private fun drainEntry(
        reader: Reader,
        buffer: ByteArray,
        cancellationRequested: () -> Boolean,
        bytesWritten: Long
    ) {
        while (true) {
            checkCancelled(cancellationRequested, bytesWritten)
            val count = reader.read(buffer)
            if (count < 0) return
            if (count == 0) continue
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

    private fun checkCancelled(cancellationRequested: () -> Boolean, bytesWritten: Long) {
        if (cancellationRequested()) throw ArchiveExtractionCancelledException(bytesWritten)
    }

    private class ArReader(input: InputStream) : Reader {
        private val archive = ArArchiveInputStream(input)

        override fun nextEntry(): Entry? {
            val entry = archive.nextEntry ?: return null
            val name = entry.name
            val type = when {
                name in arMetadataEntries -> EntryType.METADATA
                (entry.mode and AR_FILE_TYPE_MASK) == 0 ||
                    (entry.mode and AR_FILE_TYPE_MASK) == AR_REGULAR_FILE -> EntryType.FILE
                else -> EntryType.UNSUPPORTED
            }
            return Entry(name, entry.size, type)
        }

        override fun read(buffer: ByteArray): Int = archive.read(buffer, 0, buffer.size)
        override fun close() = archive.close()
    }

    private class CpioReader(input: InputStream) : Reader {
        private val archive = CpioArchiveInputStream(input)

        override fun nextEntry(): Entry? {
            val entry = archive.nextEntry ?: return null
            val type = when {
                entry.isDirectory -> EntryType.DIRECTORY
                entry.isRegularFile && entry.numberOfLinks <= 1L -> EntryType.FILE
                else -> EntryType.UNSUPPORTED
            }
            return Entry(entry.name, entry.size, type)
        }

        override fun read(buffer: ByteArray): Int = archive.read(buffer, 0, buffer.size)
        override fun close() = archive.close()
    }

    private class NonClosingInputStream(input: InputStream) : FilterInputStream(input) {
        override fun close() = Unit
    }

    private class BoundedInputStream(
        input: InputStream,
        private val maximumBytes: Long,
        private val cancellationRequested: () -> Boolean
    ) : FilterInputStream(input) {
        var bytesRead: Long = 0L
            private set

        override fun read(): Int {
            checkCancelled()
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
            checkCancelled()
            if (bytesRead >= maximumBytes) return read()
            val allowed = minOf(length.toLong(), maximumBytes - bytesRead).toInt()
            val count = super.read(buffer, offset, allowed)
            if (count > 0) bytesRead += count
            return count
        }

        private fun checkCancelled() {
            if (cancellationRequested()) throw ArchiveExtractionCancelledException(0L)
        }

        override fun close() = Unit
    }
}
