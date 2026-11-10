package com.arcnova.app.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.*
import org.junit.Test

class TarGzipExtractionEngineTest {
    @Test fun extractsTarFilesAndDirectories() {
        val destination = tempDir()
        try {
            val result = TarGzipExtractionEngine.extractTar(
                ByteArrayInputStream(tarArchive("docs/readme.txt" to "ArcNova".toByteArray())),
                destination,
                maxArchiveBytes = 1024 * 1024L
            )
            assertEquals(1, result.filesExtracted)
            assertEquals("ArcNova", File(destination, "docs/readme.txt").readText())
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test fun tarTraversalRollsBackEarlierFilesAndPreservesExistingContent() {
        val destination = tempDir()
        val existing = File(destination, "keep.txt").apply { writeText("preserve") }
        try {
            org.junit.Assert.assertThrows(UnsafeArchivePathException::class.java) {
                TarGzipExtractionEngine.extractTar(
                    ByteArrayInputStream(tarArchive(
                        "valid/first.txt" to "remove".toByteArray(),
                        "../escape.txt" to "blocked".toByteArray()
                    )),
                    destination,
                    maxArchiveBytes = 1024 * 1024L
                )
            }
            assertFalse(File(destination, "valid").exists())
            assertFalse(File(destination.parentFile, "escape.txt").exists())
            assertEquals("preserve", existing.readText())
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test fun tarCancellationRollsBackCreatedOutput() {
        val destination = tempDir()
        var cancelled = false
        try {
            org.junit.Assert.assertThrows(ArchiveExtractionCancelledException::class.java) {
                TarGzipExtractionEngine.extractTar(
                    ByteArrayInputStream(tarArchive(
                        "first.txt" to "first".toByteArray(),
                        "second.txt" to "second".toByteArray()
                    )),
                    destination,
                    maxArchiveBytes = 1024 * 1024L,
                    cancellationRequested = { cancelled },
                    onProgress = { cancelled = true }
                )
            }
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test fun tarEnforcesEntryAndOutputLimits() {
        val destination = tempDir()
        try {
            org.junit.Assert.assertThrows(ArchiveExtractionLimitException::class.java) {
                TarGzipExtractionEngine.extractTar(
                    ByteArrayInputStream(tarArchive("large.bin" to ByteArray(128))),
                    destination,
                    maxArchiveBytes = 1024 * 1024L,
                    policy = ExtractionPolicy(maxSingleFileBytes = 64)
                )
            }
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test fun gzipDecompressesOneCallerNamedFile() {
        val destination = tempDir()
        try {
            val result = TarGzipExtractionEngine.decompressGzip(
                ByteArrayInputStream(gzip("decoded payload".toByteArray())),
                destination,
                outputFileName = "payload.bin",
                maxInputBytes = 1024 * 1024L
            )
            assertEquals(1, result.filesExtracted)
            assertEquals("decoded payload", File(destination, "payload.bin").readText())
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test fun gzipChecksumFailureRemovesPartialOutput() {
        val destination = tempDir()
        val corrupt = gzip(ByteArray(4096) { 7 }).also { it[it.lastIndex - 8] = (it[it.lastIndex - 8].toInt() xor 0x01).toByte() }
        try {
            org.junit.Assert.assertThrows(Exception::class.java) {
                TarGzipExtractionEngine.decompressGzip(
                    ByteArrayInputStream(corrupt),
                    destination,
                    outputFileName = "payload.bin",
                    maxInputBytes = 1024 * 1024L
                )
            }
            assertFalse(File(destination, "payload.bin").exists())
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test fun gzipEnforcesExpansionRatioAndRollsBack() {
        val destination = tempDir()
        try {
            org.junit.Assert.assertThrows(ArchiveExtractionLimitException::class.java) {
                TarGzipExtractionEngine.decompressGzip(
                    ByteArrayInputStream(gzip(ByteArray(256 * 1024))),
                    destination,
                    outputFileName = "payload.bin",
                    maxInputBytes = 1024 * 1024L,
                    policy = ExtractionPolicy(maxExpansionRatio = 2.0)
                )
            }
            assertFalse(File(destination, "payload.bin").exists())
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test fun callerOwnedGzipInputRemainsOpen() {
        val destination = tempDir()
        var closed = false
        val source = object : FilterInputStream(ByteArrayInputStream(gzip("open".toByteArray()))) {
            override fun close() { closed = true }
        }
        try {
            TarGzipExtractionEngine.decompressGzip(
                source,
                destination,
                outputFileName = "payload.txt",
                maxInputBytes = 1024 * 1024L
            )
            assertFalse(closed)
        } finally {
            source.close()
            destination.deleteRecursively()
        }
    }

    private fun tarArchive(vararg files: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        TarArchiveOutputStream(output).use { tar ->
            files.forEach { (name, content) ->
                val entry = TarArchiveEntry(name)
                entry.size = content.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(content)
                tar.closeArchiveEntry()
            }
            tar.finish()
        }
        return output.toByteArray()
    }

    private fun gzip(content: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GzipCompressorOutputStream(output).use { it.write(content) }
        return output.toByteArray()
    }

    private fun tempDir() = File(System.getProperty("java.io.tmpdir"), "arcnova-tar-gzip-${System.nanoTime()}").apply {
        check(mkdirs())
    }
}
