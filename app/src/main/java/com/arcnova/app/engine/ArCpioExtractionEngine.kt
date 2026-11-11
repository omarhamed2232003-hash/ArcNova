package com.arcnova.app.engine

import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import org.apache.commons.compress.archivers.ar.ArArchiveEntry
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream

/**
 * Bounded extraction for Unix ar and the newc CPIO variants.
 *
 * AR is an outer archive format (including the outer container of Debian .deb packages).
 * CPIO supports the newc and newc-with-CRC variants in this adapter. Older CPIO header
 * formats are deliberately rejected until they have equivalent pre-parser metadata guards.
 */
object ArCpioExtractionEngine {
    private const val BUFFER_SIZE = 64 * 1024
    private const val AR_HEADER_BYTES = 60
    private const val AR_LONG_NAME_TABLE_MAX_BYTES = 1024 * 1024L
    private const val CPIO_NEW_HEADER_BYTES = 110

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
        input, destination, maxArchiveBytes, policy, cancellationRequested, onProgress,
        guardFactory = { ArHeaderGuardInputStream(it, policy.maxPathLength) },
        readerFactory = { ArReader(it) }
    )

    fun extractCpio(
        input: InputStream,
        destination: File,
        maxArchiveBytes: Long,
        policy: ExtractionPolicy = ExtractionPolicy(),
        cancellationRequested: () -> Boolean = { false },
        onProgress: (ArchiveExtractionProgress) -> Unit = {}
    ): ArchiveExtractionResult = extract(
        input, destination, maxArchiveBytes, policy, cancellationRequested, onProgress,
        guardFactory = { NewcHeaderGuardInputStream(it, policy.maxPathLength) },
        readerFactory = { CpioReader(it) }
    )

    private fun extract(
        input: InputStream,
        destination: File,
        maxArchiveBytes: Long,
        policy: ExtractionPolicy,
        cancellationRequested: () -> Boolean,
        onProgress: (ArchiveExtractionProgress) -> Unit,
        guardFactory: (InputStream) -> InputStream,
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
        val guarded = guardFactory(bounded)
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
            return readerFactory(NonClosingInputStream(guarded)).use { reader ->
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
                name in setOf("/", "/SYM64/", "__.SYMDEF", "__.SYMDEF SORTED", "__.SYMDEF_64") -> EntryType.METADATA
                (entry.mode and 0xF000) == 0 || (entry.mode and 0xF000) == 0x8000 -> EntryType.FILE
                else -> EntryType.UNSUPPORTED
            }
            return Entry(name, entry.size, type)
        }

        override fun read(buffer: ByteArray): Int = archive.read(buffer, 0, buffer.size)
        override fun close() = archive.close()
    }

    private class CpioReader(input: InputStream) : Reader {
        private val archive = GuardedCpioArchiveInputStream(input)

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

    /**
     * Commons Compress calls skip(1..3) while parsing newc header/data alignment. During header
     * parsing, its current entry has not yet been assigned, so the superclass skip/read path
     * cannot consume header padding. Consume only these fixed-size alignment bytes directly
     * through the guarded stream. Larger skips still use the superclass so skipped file data
     * participates in entry-byte and CRC accounting.
     */
    private class GuardedCpioArchiveInputStream(
        private val guardedInput: InputStream
    ) : CpioArchiveInputStream(guardedInput) {
        override fun skip(n: Long): Long {
            if (n !in 1L..3L) return super.skip(n)
            val skipped = guardedInput.skip(n)
            count(skipped)
            return skipped
        }
    }

    /**
     * Prevents Commons Compress from allocating a GNU string table or BSD long name from
     * an archive-declared length before ArcNova can validate it.
     */
    private class ArHeaderGuardInputStream(
        input: InputStream,
        private val maximumPathLength: Int
    ) : FilterInputStream(input) {
        private enum class Phase { MAGIC, HEADER, DATA, PADDING, EOF }
        private var phase = Phase.MAGIC
        private val magic = ByteArray(8)
        private var magicPosition = 0
        private val header = ByteArray(AR_HEADER_BYTES)
        private var headerPosition = 0
        private var dataRemaining = 0L
        private var paddingRemaining = 0

        override fun read(): Int {
            val one = ByteArray(1)
            val count = read(one, 0, 1)
            return if (count < 0) -1 else one[0].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (offset < 0 || length < 0 || offset > buffer.size - length) throw IndexOutOfBoundsException()
            if (length == 0) return 0
            while (true) {
                when (phase) {
                    Phase.MAGIC -> {
                        if (magicPosition == 0) {
                            readExactly(magic)
                            if (!magic.contentEquals("!<arch>\n".toByteArray(StandardCharsets.US_ASCII))) {
                                throw ArchiveExtractionIntegrityException("Invalid AR archive signature", 0L)
                            }
                        }
                        val count = minOf(length, magic.size - magicPosition)
                        magic.copyInto(buffer, offset, magicPosition, magicPosition + count)
                        magicPosition += count
                        if (magicPosition == magic.size) phase = Phase.HEADER
                        return count
                    }
                    Phase.HEADER -> {
                        if (headerPosition == 0 && !readHeader()) {
                            phase = Phase.EOF
                            return -1
                        }
                        val count = minOf(length, AR_HEADER_BYTES - headerPosition)
                        header.copyInto(buffer, offset, headerPosition, headerPosition + count)
                        headerPosition += count
                        if (headerPosition == AR_HEADER_BYTES) {
                            headerPosition = 0
                            phase = if (dataRemaining == 0L) {
                                if (paddingRemaining == 0) Phase.HEADER else Phase.PADDING
                            } else Phase.DATA
                        }
                        return count
                    }
                    Phase.DATA -> {
                        val requested = minOf(length.toLong(), dataRemaining).toInt()
                        val count = super.read(buffer, offset, requested)
                        if (count < 0) throw EOFException("Truncated AR member data")
                        if (count == 0) return 0
                        dataRemaining -= count
                        if (dataRemaining == 0L) {
                            phase = if (paddingRemaining == 0) Phase.HEADER else Phase.PADDING
                        }
                        return count
                    }
                    Phase.PADDING -> {
                        val value = super.read()
                        if (value < 0) throw EOFException("Missing AR member alignment byte")
                        paddingRemaining = 0
                        phase = Phase.HEADER
                        buffer[offset] = value.toByte()
                        return 1
                    }
                    Phase.EOF -> return -1
                }
            }
        }

        private fun readHeader(): Boolean {
            var position = 0
            while (position < header.size) {
                val count = super.read(header, position, header.size - position)
                if (count < 0) {
                    if (position == 0) return false
                    throw EOFException("Truncated AR member header")
                }
                if (count == 0) {
                    val value = super.read()
                    if (value < 0) {
                        if (position == 0) return false
                        throw EOFException("Truncated AR member header")
                    }
                    header[position++] = value.toByte()
                } else position += count
            }

            if (header[58] != 0x60.toByte() || header[59] != 0x0a.toByte()) {
                throw ArchiveExtractionIntegrityException("Invalid AR member header trailer", 0L)
            }
            val sizeText = String(header, 48, 10, StandardCharsets.US_ASCII).trim()
            if (sizeText.isEmpty() || sizeText.any { !it.isDigit() }) {
                throw ArchiveExtractionIntegrityException("Invalid AR member size field", 0L)
            }
            val size = sizeText.toLongOrNull()
                ?: throw ArchiveExtractionIntegrityException("AR member size overflows", 0L)
            val name = String(header, 0, 16, StandardCharsets.US_ASCII).trim()
            if (name == "//" && size > AR_LONG_NAME_TABLE_MAX_BYTES) {
                throw ArchiveExtractionLimitException(
                    "GNU AR long-name table exceeds ${AR_LONG_NAME_TABLE_MAX_BYTES} bytes", 0L
                )
            }
            if (name.startsWith("#1/")) {
                val nameLength = name.substring(3).toIntOrNull()
                    ?: throw ArchiveExtractionIntegrityException("Invalid BSD AR long-name length", 0L)
                val maxNameBytes = minOf(maximumPathLength.toLong() + 1L, 4097L)
                if (nameLength < 1 || nameLength.toLong() > maxNameBytes || nameLength.toLong() > size) {
                    throw ArchiveExtractionLimitException("BSD AR long name exceeds configured path limit", 0L)
                }
            }
            dataRemaining = size
            paddingRemaining = (size and 1L).toInt()
            return true
        }

        private fun readExactly(target: ByteArray) {
            var position = 0
            while (position < target.size) {
                val count = super.read(target, position, target.size - position)
                if (count < 0) {
                    throw EOFException("Truncated AR global header")
                }
                if (count == 0) {
                    val value = super.read()
                    if (value < 0) throw EOFException("Truncated AR global header")
                    target[position++] = value.toByte()
                } else position += count
            }
        }

        override fun skip(n: Long): Long {
            if (n <= 0L) return 0L
            val buffer = ByteArray(minOf(BUFFER_SIZE.toLong(), n).toInt())
            var skipped = 0L
            while (skipped < n) {
                val count = read(buffer, 0, minOf(buffer.size.toLong(), n - skipped).toInt())
                if (count < 0) break
                if (count == 0) continue
                skipped += count
            }
            return skipped
        }
    }

    /**
     * Guards newc metadata before the Commons Compress parser receives it. The name-size field
     * is checked before its variable-length NUL-terminated name is exposed to that parser.
     */
    private class NewcHeaderGuardInputStream(
        input: InputStream,
        private val maximumPathLength: Int
    ) : FilterInputStream(input) {
        private enum class Phase { HEADER, NAME, HEADER_PADDING, DATA, DATA_PADDING, EOF }
        private var phase = Phase.HEADER
        private val header = ByteArray(CPIO_NEW_HEADER_BYTES)
        private var headerPosition = 0
        private var name = ByteArray(0)
        private var namePosition = 0
        private var dataRemaining = 0L
        private var headerPaddingRemaining = 0
        private var dataPaddingRemaining = 0
        private var trailer = false

        override fun read(): Int {
            val one = ByteArray(1)
            val count = read(one, 0, 1)
            return if (count < 0) -1 else one[0].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (offset < 0 || length < 0 || offset > buffer.size - length) throw IndexOutOfBoundsException()
            if (length == 0) return 0
            while (true) {
                when (phase) {
                    Phase.HEADER -> {
                        if (headerPosition == 0 && !prepareHeader()) {
                            phase = Phase.EOF
                            return -1
                        }
                        val count = minOf(length, CPIO_NEW_HEADER_BYTES - headerPosition)
                        header.copyInto(buffer, offset, headerPosition, headerPosition + count)
                        headerPosition += count
                        if (headerPosition == CPIO_NEW_HEADER_BYTES) {
                            headerPosition = 0
                            phase = Phase.NAME
                        }
                        return count
                    }
                    Phase.NAME -> {
                        val count = minOf(length, name.size - namePosition)
                        name.copyInto(buffer, offset, namePosition, namePosition + count)
                        namePosition += count
                        if (namePosition == name.size) {
                            namePosition = 0
                            phase = if (headerPaddingRemaining == 0) {
                                afterHeaderPadding()
                            } else Phase.HEADER_PADDING
                        }
                        return count
                    }
                    Phase.HEADER_PADDING -> {
                        val count = readPadding(buffer, offset, length, headerPaddingRemaining, "CPIO header")
                        headerPaddingRemaining -= count
                        if (headerPaddingRemaining == 0) phase = afterHeaderPadding()
                        return count
                    }
                    Phase.DATA -> {
                        val requested = minOf(length.toLong(), dataRemaining).toInt()
                        val count = super.read(buffer, offset, requested)
                        if (count < 0) throw EOFException("Truncated CPIO entry data")
                        if (count == 0) return 0
                        dataRemaining -= count
                        if (dataRemaining == 0L) phase = Phase.DATA_PADDING
                        return count
                    }
                    Phase.DATA_PADDING -> {
                        if (dataPaddingRemaining == 0) {
                            phase = if (trailer) Phase.EOF else Phase.HEADER
                            continue
                        }
                        val count = readPadding(buffer, offset, length, dataPaddingRemaining, "CPIO data")
                        dataPaddingRemaining -= count
                        if (dataPaddingRemaining == 0) phase = if (trailer) Phase.EOF else Phase.HEADER
                        return count
                    }
                    Phase.EOF -> return -1
                }
            }
        }

        private fun prepareHeader(): Boolean {
            var position = 0
            while (position < header.size) {
                val count = super.read(header, position, header.size - position)
                if (count < 0) {
                    if (position == 0) return false
                    throw EOFException("Truncated CPIO newc header")
                }
                if (count == 0) {
                    val value = super.read()
                    if (value < 0) {
                        if (position == 0) return false
                        throw EOFException("Truncated CPIO newc header")
                    }
                    header[position++] = value.toByte()
                } else position += count
            }
            val magic = String(header, 0, 6, StandardCharsets.US_ASCII)
            if (magic != "070701" && magic != "070702") {
                throw ArchiveExtractionUnsupportedFeatureException(
                    "Only CPIO newc (070701) and newc-with-CRC (070702) are supported", 0L
                )
            }
            val nameSize = parseHexField(header, 94, 8, "CPIO name size")
            if (nameSize !in 1L..minOf(maximumPathLength.toLong() + 1L, 4097L)) {
                throw ArchiveExtractionLimitException("CPIO name exceeds configured path limit", 0L)
            }
            dataRemaining = parseHexField(header, 54, 8, "CPIO entry size")
            if (dataRemaining < 0L) throw ArchiveExtractionIntegrityException("Invalid CPIO entry size", 0L)

            name = ByteArray(nameSize.toInt())
            readExactly(name, "CPIO entry name")
            if (name.last() != 0.toByte() || name.dropLast(1).any { it == 0.toByte() }) {
                throw ArchiveExtractionIntegrityException("Invalid CPIO NUL-terminated entry name", 0L)
            }
            trailer = name.copyOfRange(0, name.size - 1)
                .contentEquals("TRAILER!!!".toByteArray(StandardCharsets.US_ASCII))
            if (trailer && dataRemaining != 0L) {
                throw ArchiveExtractionIntegrityException("CPIO trailer has a non-zero payload", 0L)
            }
            headerPaddingRemaining = ((4 - ((CPIO_NEW_HEADER_BYTES + name.size) % 4)) % 4)
            dataPaddingRemaining = ((4 - (dataRemaining % 4).toInt()) % 4)
            return true
        }

        private fun parseHexField(bytes: ByteArray, offset: Int, length: Int, label: String): Long {
            val value = String(bytes, offset, length, StandardCharsets.US_ASCII)
            if (value.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) {
                throw ArchiveExtractionIntegrityException("Invalid $label field", 0L)
            }
            return value.toLongOrNull(16)
                ?: throw ArchiveExtractionIntegrityException("$label field overflows", 0L)
        }

        private fun readExactly(target: ByteArray, label: String) {
            var position = 0
            while (position < target.size) {
                val count = super.read(target, position, target.size - position)
                if (count < 0) throw EOFException("Truncated $label")
                if (count == 0) {
                    val value = super.read()
                    if (value < 0) throw EOFException("Truncated $label")
                    target[position++] = value.toByte()
                } else position += count
            }
        }

        private fun readPadding(
            buffer: ByteArray,
            offset: Int,
            length: Int,
            remaining: Int,
            label: String
        ): Int {
            val count = super.read(buffer, offset, minOf(length, remaining))
            if (count < 0) throw EOFException("Truncated $label alignment padding")
            return count
        }

        private fun afterHeaderPadding(): Phase {
            return if (trailer) Phase.EOF
            else if (dataRemaining == 0L) Phase.DATA_PADDING
            else Phase.DATA
        }

        override fun skip(n: Long): Long {
            if (n <= 0L) return 0L
            val buffer = ByteArray(minOf(BUFFER_SIZE.toLong(), n).toInt())
            var skipped = 0L
            while (skipped < n) {
                val count = read(buffer, 0, minOf(buffer.size.toLong(), n - skipped).toInt())
                if (count < 0) break
                if (count == 0) continue
                skipped += count
            }
            return skipped
        }
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
            if (offset < 0 || length < 0 || offset > buffer.size - length) throw IndexOutOfBoundsException()
            if (length == 0) return 0
            checkCancelled()
            if (bytesRead >= maximumBytes) return read()
            val allowed = minOf(length.toLong(), maximumBytes - bytesRead).toInt()
            val count = super.read(buffer, offset, allowed)
            if (count > 0) bytesRead += count
            return count
        }

        override fun skip(n: Long): Long {
            if (n <= 0L) return 0L
            val buffer = ByteArray(minOf(BUFFER_SIZE.toLong(), n).toInt())
            var skipped = 0L
            while (skipped < n) {
                val count = read(buffer, 0, minOf(buffer.size.toLong(), n - skipped).toInt())
                if (count < 0) break
                if (count == 0) continue
                skipped += count
            }
            return skipped
        }

        private fun checkCancelled() {
            if (cancellationRequested()) throw ArchiveExtractionCancelledException(0L)
        }

        override fun close() = Unit
    }
}
