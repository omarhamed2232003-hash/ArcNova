package com.arcnova.app.engine

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilterInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.Assert.*
import org.junit.Test

class SevenZExtractionEngineTest {
    @Test fun extractsLzma2SevenZArchive() {
        val archive = createArchive("folder/readme.txt" to "ArcNova 7z".toByteArray())
        val destination = tempDir()
        try {
            val result = ByteArrayInputStream(archive.readBytes()).use { input ->
                SevenZExtractionEngine.extract(
                    input = input,
                    cacheDirectory = destination,
                    destination = File(destination, "output"),
                    maxArchiveBytes = 4 * 1024 * 1024L
                )
            }
            assertEquals(1, result.filesExtracted)
            assertEquals("ArcNova 7z", File(destination, "output/folder/readme.txt").readText())
        } finally {
            archive.delete()
            destination.deleteRecursively()
        }
    }

    @Test fun sevenZTraversalRollsBackEarlierFiles() {
        val archive = createArchive(
            "safe/first.txt" to "remove".toByteArray(),
            "../escape.txt" to "blocked".toByteArray()
        )
        val destination = tempDir()
        val output = File(destination, "output")
        try {
            org.junit.Assert.assertThrows(UnsafeArchivePathException::class.java) {
                ByteArrayInputStream(archive.readBytes()).use { input ->
                    SevenZExtractionEngine.extract(
                        input,
                        destination,
                        output,
                        maxArchiveBytes = 4 * 1024 * 1024L
                    )
                }
            }
            assertFalse(output.exists())
            assertFalse(File(destination.parentFile, "escape.txt").exists())
        } finally {
            archive.delete()
            destination.deleteRecursively()
        }
    }

    @Test fun sevenZEntryCountLimitIsEnforcedDuringPreflight() {
        val archive = createArchive(
            "first.txt" to "one".toByteArray(),
            "second.txt" to "two".toByteArray()
        )
        val destination = tempDir()
        try {
            org.junit.Assert.assertThrows(ArchiveExtractionLimitException::class.java) {
                ByteArrayInputStream(archive.readBytes()).use { input ->
                    SevenZExtractionEngine.extract(
                        input,
                        destination,
                        File(destination, "output"),
                        maxArchiveBytes = 4 * 1024 * 1024L,
                        policy = ExtractionPolicy(maxEntries = 1)
                    )
                }
            }
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        } finally {
            archive.delete()
            destination.deleteRecursively()
        }
    }

    @Test fun sevenZPreflightsPerFileOutputLimit() {
        val archive = createArchive("large.bin" to ByteArray(128))
        val destination = tempDir()
        try {
            org.junit.Assert.assertThrows(ArchiveExtractionLimitException::class.java) {
                ByteArrayInputStream(archive.readBytes()).use { input ->
                    SevenZExtractionEngine.extract(
                        input,
                        destination,
                        File(destination, "output"),
                        maxArchiveBytes = 4 * 1024 * 1024L,
                        policy = ExtractionPolicy(maxSingleFileBytes = 64)
                    )
                }
            }
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        } finally {
            archive.delete()
            destination.deleteRecursively()
        }
    }

    @Test fun sevenZCancellationRollsBackCreatedFiles() {
        val archive = createArchive(
            "first.txt" to "first".toByteArray(),
            "second.txt" to "second".toByteArray()
        )
        val destination = tempDir()
        val output = File(destination, "output")
        var cancelled = false
        try {
            org.junit.Assert.assertThrows(ArchiveExtractionCancelledException::class.java) {
                ByteArrayInputStream(archive.readBytes()).use { input ->
                    SevenZExtractionEngine.extract(
                        input,
                        destination,
                        output,
                        maxArchiveBytes = 4 * 1024 * 1024L,
                        cancellationRequested = { cancelled },
                        onProgress = { cancelled = true }
                    )
                }
            }
            assertFalse(output.exists())
        } finally {
            archive.delete()
            destination.deleteRecursively()
        }
    }

    @Test fun sevenZExpansionRatioIsEnforcedAndRolledBack() {
        val archive = createArchive("zeros.bin" to ByteArray(256 * 1024))
        val destination = tempDir()
        val output = File(destination, "output")
        try {
            org.junit.Assert.assertThrows(ArchiveExtractionLimitException::class.java) {
                ByteArrayInputStream(archive.readBytes()).use { input ->
                    SevenZExtractionEngine.extract(
                        input,
                        destination,
                        output,
                        maxArchiveBytes = 4 * 1024 * 1024L,
                        policy = ExtractionPolicy(maxExpansionRatio = 2.0)
                    )
                }
            }
            assertFalse(output.exists())
        } finally {
            archive.delete()
            destination.deleteRecursively()
        }
    }

    @Test fun sevenZInputLimitIsEnforcedBeforeDestinationCreation() {
        val archive = createArchive("file.txt" to "content".toByteArray())
        val destination = tempDir()
        try {
            org.junit.Assert.assertThrows(ArchiveExtractionLimitException::class.java) {
                ByteArrayInputStream(archive.readBytes()).use { input ->
                    SevenZExtractionEngine.extract(
                        input,
                        destination,
                        File(destination, "output"),
                        maxArchiveBytes = 16
                    )
                }
            }
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        } finally {
            archive.delete()
            destination.deleteRecursively()
        }
    }

    @Test fun sevenZChecksumFailureRollsBackPartialOutput() {
        val archive = createArchive("payload.bin" to ByteArray(64 * 1024) { (it % 251).toByte() })
        val bytes = archive.readBytes()
        bytes[40] = (bytes[40].toInt() xor 0x20).toByte()
        val destination = tempDir()
        val output = File(destination, "output")
        try {
            org.junit.Assert.assertThrows(Exception::class.java) {
                ByteArrayInputStream(bytes).use { input ->
                    SevenZExtractionEngine.extract(
                        input,
                        destination,
                        output,
                        maxArchiveBytes = 4 * 1024 * 1024L
                    )
                }
            }
            assertFalse(output.exists())
        } finally {
            archive.delete()
            destination.deleteRecursively()
        }
    }

    @Test fun sevenZEngineDoesNotCloseCallerOwnedInput() {
        val archive = createArchive("file.txt" to "content".toByteArray())
        val destination = tempDir()
        var closed = false
        val source = object : FilterInputStream(ByteArrayInputStream(archive.readBytes())) {
            override fun close() { closed = true }
        }
        try {
            SevenZExtractionEngine.extract(
                source,
                destination,
                File(destination, "output"),
                maxArchiveBytes = 4 * 1024 * 1024L
            )
            assertFalse(closed)
        } finally {
            source.close()
            archive.delete()
            destination.deleteRecursively()
        }
    }

    private fun createArchive(vararg files: Pair<String, ByteArray>): File {
        val archive = File.createTempFile("arcnova-sevenz-", ".7z")
        SevenZOutputFile(archive).use { output ->
            output.setContentCompression(SevenZMethod.LZMA2)
            files.forEach { (name, bytes) ->
                val entry = SevenZArchiveEntry().apply {
                    this.name = name
                    size = bytes.size.toLong()
                }
                output.putArchiveEntry(entry)
                output.write(bytes)
                output.closeArchiveEntry()
            }
        }
        return archive
    }

    private fun tempDir() = File(
        System.getProperty("java.io.tmpdir"),
        "arcnova-sevenz-${System.nanoTime()}"
    ).apply { check(mkdirs()) }
}
