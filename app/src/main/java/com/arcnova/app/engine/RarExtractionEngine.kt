package com.arcnova.app.engine

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry

/** Single-volume RAR3/RAR5 extraction with ArcNova-owned safety and rollback. */
object RarExtractionEngine {
    private const val BUFFER_SIZE = 64 * 1024
    private const val READ_BLOCK_SIZE = 64L * 1024L

    internal enum class EntryType { FILE, DIRECTORY, LINK, OTHER }
    internal data class Entry(val name: String?, val type: EntryType, val size: Long?, val encrypted: Boolean = false)
    internal interface Reader : AutoCloseable {
        fun nextEntry(): Entry?
        fun readData(buffer: ByteBuffer): Int
        fun skipData()
    }
    internal fun interface ReaderFactory { fun open(archiveFile: File): Reader }

    fun extract(
        input: InputStream,
        cacheDirectory: File,
        destination: File,
        maxArchiveBytes: Long,
        policy: ExtractionPolicy = ExtractionPolicy(),
        cancellationRequested: () -> Boolean = { false },
        onProgress: (ArchiveExtractionProgress) -> Unit = {}
    ): ArchiveExtractionResult = extractInternal(
        input, cacheDirectory, destination, maxArchiveBytes, policy,
        cancellationRequested, onProgress, ReaderFactory(::NativeReader)
    )

    internal fun extractWithReaderFactory(
        input: InputStream,
        cacheDirectory: File,
        destination: File,
        maxArchiveBytes: Long,
        policy: ExtractionPolicy = ExtractionPolicy(),
        cancellationRequested: () -> Boolean = { false },
        onProgress: (ArchiveExtractionProgress) -> Unit = {},
        readerFactory: ReaderFactory
    ): ArchiveExtractionResult = extractInternal(
        input, cacheDirectory, destination, maxArchiveBytes, policy,
        cancellationRequested, onProgress, readerFactory
    )

    private fun extractInternal(
        input: InputStream, cacheDirectory: File, destination: File, maxArchiveBytes: Long,
        policy: ExtractionPolicy, cancellationRequested: () -> Boolean,
        onProgress: (ArchiveExtractionProgress) -> Unit, readerFactory: ReaderFactory
    ): ArchiveExtractionResult {
        require(maxArchiveBytes > 0L)
        require(policy.maxEntries > 0)
        require(policy.maxSingleFileBytes >= 0L && policy.maxTotalOutputBytes >= 0L)
        require(policy.maxExpansionRatio.isFinite() && policy.maxExpansionRatio > 0.0)

        val spool = try {
            SeekableArchiveSpool.create(input, cacheDirectory, maxArchiveBytes, cancellationRequested)
        } catch (error: ArchiveSpoolCancelledException) {
            throw ArchiveExtractionCancelledException(error.copiedBytes)
        } catch (error: ArchiveSpoolLimitException) {
            throw ArchiveExtractionLimitException(error.message ?: "RAR input limit exceeded", error.copiedBytes)
        }

        val destinationExisted = destination.exists()
        var root: File? = null
        val createdFiles = mutableListOf<File>()
        val createdDirectories = mutableListOf<File>()
        val seen = mutableMapOf<String, Boolean>()
        var entries = 0
        var files = 0
        var directories = 0
        var total = 0L
        val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)
        val copy = ByteArray(BUFFER_SIZE)

        try {
            return spool.use { seekable ->
                val compressedSize = seekable.file.length()
                if (compressedSize <= 0L) throw ArchiveExtractionIntegrityException("RAR archive is empty", 0L)
                readerFactory.open(seekable.file).use { reader ->
                    if (!destination.exists() && !destination.mkdirs()) throw ArchiveExtractionException("Cannot create destination", 0L)
                    if (!destination.isDirectory) throw ArchiveExtractionException("Destination must be a directory", 0L)
                    root = destination.canonicalFile

                    while (true) {
                        checkCancelled(cancellationRequested, total)
                        val entry = reader.nextEntry() ?: break
                        entries++
                        if (entries > policy.maxEntries) throw ArchiveExtractionLimitException("RAR entry count exceeds limit", total)
                        if (entry.encrypted) throw ArchiveExtractionUnsupportedFeatureException("Encrypted RAR entries are not supported", total)
                        if (entry.type == EntryType.LINK || entry.type == EntryType.OTHER) {
                            throw ArchiveExtractionUnsupportedFeatureException("RAR links and special entries are not supported: ${entry.name}", total)
                        }
                        val name = entry.name ?: throw ArchiveExtractionUnsupportedFeatureException("RAR entry has no name", total)
                        val relative = ArchiveExtractionFileSystem.sanitizePath(name, policy)
                        val normalized = relative.replace(File.separatorChar, '/')
                        val isDirectory = entry.type == EntryType.DIRECTORY
                        validatePath(normalized, isDirectory, seen, total)
                        seen[normalized] = isDirectory
                        val output = File(root!!, relative).canonicalFile
                        ArchiveExtractionFileSystem.ensureContained(output, root!!, name, total)

                        if (isDirectory) {
                            if (entry.size != null && entry.size != 0L) throw ArchiveExtractionIntegrityException("RAR directory has non-zero size: $name", total)
                            ArchiveExtractionFileSystem.ensureDirectory(output, root!!, createdDirectories, total)
                            directories++
                            reader.skipData()
                            onProgress(ArchiveExtractionProgress(entries, files, directories, total, relative))
                            continue
                        }

                        val declared = entry.size ?: throw ArchiveExtractionUnsupportedFeatureException("RAR entry size is unavailable: $name", total)
                        if (declared < 0L || declared > policy.maxSingleFileBytes || declared > policy.maxTotalOutputBytes - total) {
                            throw ArchiveExtractionLimitException("RAR entry exceeds configured limits: $name", total)
                        }
                        val parent = output.parentFile ?: throw UnsafeArchivePathException(name)
                        ArchiveExtractionFileSystem.ensureDirectory(parent, root!!, createdDirectories, total)
                        if (output.exists() || !output.createNewFile()) throw ArchiveExtractionException("Refusing to overwrite: $relative", total)
                        createdFiles.add(output)

                        var entryBytes = 0L
                        FileOutputStream(output, false).use { out ->
                            while (true) {
                                checkCancelled(cancellationRequested, total)
                                buffer.clear()
                                val count = reader.readData(buffer)
                                if (count < 0 || count > BUFFER_SIZE) throw ArchiveExtractionIntegrityException("Invalid RAR decoder byte count", total)
                                if (count == 0) break
                                entryBytes = safeAdd(entryBytes, count.toLong(), total)
                                total = safeAdd(total, count.toLong(), total)
                                if (entryBytes > declared || entryBytes > policy.maxSingleFileBytes || total > policy.maxTotalOutputBytes) {
                                    throw ArchiveExtractionLimitException("RAR output exceeds configured limits", total)
                                }
                                if (total.toDouble() / compressedSize.toDouble() > policy.maxExpansionRatio) {
                                    throw ArchiveExtractionLimitException("RAR expansion ratio exceeds configured limit", total)
                                }
                                buffer.flip()
                                buffer.get(copy, 0, count)
                                out.write(copy, 0, count)
                            }
                        }
                        if (entryBytes != declared) throw ArchiveExtractionIntegrityException("RAR size mismatch for $name", total)
                        files++
                        onProgress(ArchiveExtractionProgress(entries, files, directories, total, relative))
                    }
                    if (entries == 0) throw ArchiveExtractionIntegrityException("Not a supported RAR3/RAR5 archive", total)
                    ArchiveExtractionResult(entries, files, directories, total, root!!)
                }
            }
        } catch (error: Exception) {
            ArchiveExtractionFileSystem.rollback(createdFiles, createdDirectories, if (destinationExisted) null else root, error)
            throw error
        }
    }

    private fun validatePath(path: String, isDirectory: Boolean, seen: Map<String, Boolean>, bytes: Long) {
        if (seen.containsKey(path)) throw ArchiveExtractionException("Duplicate RAR path: $path", bytes)
        val parts = path.split('/')
        for (i in 1 until parts.size) {
            val parent = parts.take(i).joinToString("/")
            if (seen[parent] == false) throw ArchiveExtractionException("RAR file path is a parent: $parent", bytes)
        }
        if (!isDirectory && seen.keys.any { it.startsWith("$path/") }) {
            throw ArchiveExtractionException("RAR file conflicts with existing child: $path", bytes)
        }
    }

    private fun safeAdd(a: Long, b: Long, bytes: Long): Long {
        if (b > Long.MAX_VALUE - a) throw ArchiveExtractionLimitException("RAR byte counter overflow", bytes)
        return a + b
    }
    private fun checkCancelled(cancel: () -> Boolean, bytes: Long) {
        if (cancel()) throw ArchiveExtractionCancelledException(bytes)
    }

    private class NativeReader(file: File) : Reader {
        private val archive = Archive.readNew()
        private var opened = false
        private var closed = false
        init {
            try {
                Archive.readSupportFilterNone(archive)
                Archive.readSupportFormatRar(archive)
                Archive.readSupportFormatRar5(archive)
                Archive.readOpenFileName(archive, file.absolutePath.toByteArray(Charsets.UTF_8), READ_BLOCK_SIZE)
                opened = true
            } catch (error: Exception) {
                runCatching { Archive.readFree(archive) }
                closed = true
                throw error
            }
        }
        override fun nextEntry(): Entry? {
            val pointer = Archive.readNextHeader(archive)
            if (pointer == 0L) return null
            val baseFormat = Archive.format(archive) and Archive.FORMAT_BASE_MASK
            if (baseFormat != Archive.FORMAT_RAR && baseFormat != Archive.FORMAT_RAR_V5) {
                throw ArchiveExtractionUnsupportedFeatureException("Detected container is not RAR3/RAR5", 0L)
            }
            val fileType = ArchiveEntry.filetype(pointer)
            val type = when {
                ArchiveEntry.hardlinkIsSet(pointer) || ArchiveEntry.symlink(pointer) != null -> EntryType.LINK
                fileType == ArchiveEntry.AE_IFREG -> EntryType.FILE
                fileType == ArchiveEntry.AE_IFDIR -> EntryType.DIRECTORY
                else -> EntryType.OTHER
            }
            val size = if (ArchiveEntry.sizeIsSet(pointer)) ArchiveEntry.size(pointer) else null
            return Entry(ArchiveEntry.pathnameUtf8(pointer), type, size, ArchiveEntry.isEncrypted(pointer))
        }
        override fun readData(buffer: ByteBuffer): Int {
            Archive.readData(archive, buffer)
            return buffer.position()
        }
        override fun skipData() { Archive.readDataSkip(archive) }
        override fun close() {
            if (closed) return
            closed = true
            try { if (opened) Archive.readClose(archive) } finally { Archive.readFree(archive) }
        }
    }
}
