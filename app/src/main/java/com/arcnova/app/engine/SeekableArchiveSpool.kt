package com.arcnova.app.engine

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Owns a bounded seekable copy of a caller-provided archive stream.
 *
 * Random-access archive readers require a seekable source. This spool keeps archive
 * bytes on private app storage rather than loading the complete archive into memory.
 * The caller retains ownership of [input]; the returned handle owns the temp file.
 */
object SeekableArchiveSpool {
    private const val BUFFER_SIZE = 64 * 1024

    fun create(
        input: InputStream,
        cacheDirectory: File,
        maxArchiveBytes: Long,
        cancellationRequested: () -> Boolean = { false }
    ): SeekableArchiveFile {
        require(maxArchiveBytes > 0L) { "maxArchiveBytes must be positive" }
        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
            throw IOException("Cannot create archive cache directory")
        }
        if (!cacheDirectory.isDirectory) {
            throw IOException("Archive cache path is not a directory")
        }

        val tempFile = File.createTempFile("arcnova-archive-", ".archive", cacheDirectory)
        var copiedBytes = 0L
        try {
            BufferedOutputStream(FileOutputStream(tempFile)).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    if (cancellationRequested()) {
                        throw ArchiveSpoolCancelledException(copiedBytes)
                    }
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue

                    if (count.toLong() > Long.MAX_VALUE - copiedBytes) {
                        throw ArchiveSpoolLimitException("Archive byte counter overflow", copiedBytes)
                    }
                    val nextBytes = copiedBytes + count
                    if (nextBytes > maxArchiveBytes) {
                        throw ArchiveSpoolLimitException(
                            "Archive exceeds configured size limit of $maxArchiveBytes bytes",
                            copiedBytes
                        )
                    }
                    output.write(buffer, 0, count)
                    copiedBytes = nextBytes
                }
                output.flush()
            }
            return SeekableArchiveFile(tempFile, copiedBytes)
        } catch (error: Exception) {
            try {
                if (tempFile.exists() && !tempFile.delete()) {
                    throw IOException("Could not remove incomplete archive spool: $tempFile")
                }
            } catch (cleanupError: Exception) {
                error.addSuppressed(cleanupError)
            }
            throw error
        }
    }
}

/** A temporary seekable archive. Close it after the backend has closed its reader. */
class SeekableArchiveFile internal constructor(
    val file: File,
    val sizeBytes: Long
) : AutoCloseable {
    override fun close() {
        if (file.exists() && !file.delete()) {
            throw IOException("Could not remove temporary archive spool: $file")
        }
    }
}

class ArchiveSpoolLimitException(message: String, val copiedBytes: Long) : IOException(message)
class ArchiveSpoolCancelledException(val copiedBytes: Long) : IOException("Archive spooling cancelled")
