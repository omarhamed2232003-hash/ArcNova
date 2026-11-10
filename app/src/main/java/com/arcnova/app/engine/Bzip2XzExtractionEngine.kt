package com.arcnova.app.engine

import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream

/**
 * BZIP2 and XZ single-stream decompression.
 *
 * These formats produce one output file rather than a multi-entry archive.
 * The caller supplies the output name; compressed-stream metadata is never
 * interpreted as a filesystem path.
 */
object Bzip2XzExtractionEngine {
    private const val BUFFER_SIZE = 64 * 1024
    private const val MAX_XZ_MEMORY_KIB = 128 * 1024

    fun decompress(
        format: ArchiveFormat,
        input: InputStream,
        destination: File,
        outputFileName: String,
        maxInputBytes: Long,
        policy: ExtractionPolicy = ExtractionPolicy(),
        cancellationRequested: () -> Boolean = { false },
        onProgress: (ArchiveExtractionProgress) -> Unit = {}
    ): ArchiveExtractionResult {
        require(format == ArchiveFormat.BZIP2 || format == ArchiveFormat.XZ) {
            "Only BZIP2 and XZ streams are supported"
        }
        require(maxInputBytes > 0L) { "maxInputBytes must be positive" }
        require(policy.maxSingleFileBytes >= 0L) { "maxSingleFileBytes must not be negative" }
        require(policy.maxTotalOutputBytes >= 0L) { "maxTotalOutputBytes must not be negative" }
        require(policy.maxPathLength > 0) { "maxPathLength must be positive" }
        require(policy.maxPathDepth > 0) { "maxPathDepth must be positive" }
        require(policy.maxExpansionRatio.isFinite() && policy.maxExpansionRatio > 0.0) {
            "maxExpansionRatio must be finite and greater than zero"
        }

        val relative = ArchiveExtractionFileSystem.sanitizePath(outputFileName, policy)
        val destinationExisted = destination.exists()
        require(destinationExisted || destination.mkdirs()) { "Cannot create destination directory" }
        require(destination.isDirectory) { "Destination must be a directory" }
        val root = destination.canonicalFile
        val output = File(root, relative).canonicalFile
        ArchiveExtractionFileSystem.ensureContained(output, root, outputFileName, 0L)

        val createdDirectories = mutableListOf<File>()
        val createdFiles = mutableListOf<File>()
        try {
            ArchiveExtractionFileSystem.ensureDirectory(
                output.parentFile ?: throw UnsafeArchivePathException(outputFileName),
                root,
                createdDirectories,
                0L
            )
            if (output.exists() || !output.createNewFile()) {
                throw ArchiveExtractionException("Refusing to overwrite: $relative", 0L)
            }
            createdFiles.add(output)
        } catch (error: Exception) {
            ArchiveExtractionFileSystem.rollback(
                createdFiles, createdDirectories, if (destinationExisted) null else root, error
            )
            throw error
        }

        val boundedInput = BoundedInputStream(input, maxInputBytes, cancellationRequested)
        val decoded = try {
            val nonClosing = NonClosingInputStream(boundedInput)
            when (format) {
                ArchiveFormat.BZIP2 -> BZip2CompressorInputStream(nonClosing, true)
                ArchiveFormat.XZ -> XZCompressorInputStream.builder()
                    .setInputStream(nonClosing)
                    .setDecompressConcatenated(true)
                    .setMemoryLimitKiB(MAX_XZ_MEMORY_KIB)
                    .get()
                else -> error("Unsupported format")
            }
        } catch (error: Exception) {
            ArchiveExtractionFileSystem.rollback(
                createdFiles, createdDirectories, if (destinationExisted) null else root, error
            )
            throw error
        }

        val buffer = ByteArray(BUFFER_SIZE)
        var totalBytes = 0L
        var operationFailure: Exception? = null
        var closeAttempted = false
        try {
            FileOutputStream(output, false).use { out ->
                while (true) {
                    if (cancellationRequested()) throw ArchiveExtractionCancelledException(totalBytes)
                    val count = decoded.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    if (count.toLong() > Long.MAX_VALUE - totalBytes) {
                        throw ArchiveExtractionLimitException("Output byte counter overflow", totalBytes)
                    }
                    totalBytes += count.toLong()
                    if (totalBytes > policy.maxSingleFileBytes ||
                        totalBytes > policy.maxTotalOutputBytes
                    ) {
                        throw ArchiveExtractionLimitException(
                            "${format.label} output exceeds configured limits", totalBytes
                        )
                    }
                    val compressedBytes = boundedInput.bytesRead
                    if (compressedBytes > 0L &&
                        totalBytes.toDouble() / compressedBytes.toDouble() > policy.maxExpansionRatio
                    ) {
                        throw ArchiveExtractionLimitException(
                            "Expansion ratio exceeds configured limit", totalBytes
                        )
                    }
                    out.write(buffer, 0, count)
                    onProgress(ArchiveExtractionProgress(1, 1, createdDirectories.size, totalBytes, relative))
                }
            }
            decoded.close()
            closeAttempted = true
            return ArchiveExtractionResult(1, 1, createdDirectories.size, totalBytes, root)
        } catch (error: Exception) {
            operationFailure = error
            ArchiveExtractionFileSystem.rollback(
                createdFiles, createdDirectories, if (destinationExisted) null else root, error
            )
            throw error
        } finally {
            if (!closeAttempted) {
                try {
                    decoded.close()
                } catch (closeError: Exception) {
                    val primary = operationFailure
                    if (primary != null) primary.addSuppressed(closeError) else {
                        ArchiveExtractionFileSystem.rollback(
                            createdFiles, createdDirectories,
                            if (destinationExisted) null else root, closeError
                        )
                        throw closeError
                    }
                }
            }
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
            checkCancellation()
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
            checkCancellation()
            if (bytesRead >= maximumBytes) return read()
            val allowed = minOf(length.toLong(), maximumBytes - bytesRead).toInt()
            val count = super.read(buffer, offset, allowed)
            if (count > 0) bytesRead += count
            return count
        }

        private fun checkCancellation() {
            if (cancellationRequested()) throw ArchiveExtractionCancelledException(0L)
        }

        override fun close() = Unit
    }
}
