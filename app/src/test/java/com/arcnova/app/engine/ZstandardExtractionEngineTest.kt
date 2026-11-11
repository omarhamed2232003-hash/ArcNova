package com.arcnova.app.engine

import com.github.luben.zstd.ZstdOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import org.junit.Assert.*
import org.junit.Test

class ZstandardExtractionEngineTest {
    @Test fun decompressesZstandardFrameToCallerNamedFile() {
        val destination = tempDir()
        try {
            val result = ZstandardExtractionEngine.decompress(
                ByteArrayInputStream(zstd("ArcNova Zstandard")), destination,
                "decoded.txt", 1024 * 1024L
            )
            assertEquals(1, result.filesExtracted)
            assertEquals("ArcNova Zstandard", File(destination, "decoded.txt").readText())
        } finally { destination.deleteRecursively() }
    }

    @Test fun decompressesConcatenatedZstandardFrames() {
        val destination = tempDir()
        try {
            ZstandardExtractionEngine.decompress(
                ByteArrayInputStream(zstd("first-") + zstd("second")), destination,
                "decoded.txt", 1024 * 1024L
            )
            assertEquals("first-second", File(destination, "decoded.txt").readText())
        } finally { destination.deleteRecursively() }
    }

    @Test fun rejectsTruncatedFrameAndRollsBack() {
        val destination = tempDir()
        val encoded = zstd("truncated payload").dropLast(3).toByteArray()
        try {
            assertThrows(Exception::class.java) {
                ZstandardExtractionEngine.decompress(ByteArrayInputStream(encoded), destination,
                    "decoded.txt", 1024 * 1024L)
            }
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        } finally { destination.deleteRecursively() }
    }

    @Test fun rejectsOutputBeyondConfiguredLimitAndRollsBack() {
        val destination = tempDir()
        try {
            assertThrows(ArchiveExtractionLimitException::class.java) {
                ZstandardExtractionEngine.decompress(ByteArrayInputStream(zstd("x".repeat(4096))),
                    destination, "decoded.txt", 1024 * 1024L,
                    policy = ExtractionPolicy(maxSingleFileBytes = 128))
            }
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        } finally { destination.deleteRecursively() }
    }

    @Test fun rejectsExpansionRatioBeyondConfiguredLimitAndRollsBack() {
        val destination = tempDir()
        try {
            assertThrows(ArchiveExtractionLimitException::class.java) {
                ZstandardExtractionEngine.decompress(ByteArrayInputStream(zstd("A".repeat(64 * 1024))),
                    destination, "decoded.txt", 1024 * 1024L,
                    policy = ExtractionPolicy(maxExpansionRatio = 2.0))
            }
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        } finally { destination.deleteRecursively() }
    }

    @Test fun rejectsCompressedInputBeyondConfiguredLimit() {
        val destination = tempDir()
        val encoded = zstd("payload")
        try {
            assertThrows(ArchiveExtractionLimitException::class.java) {
                ZstandardExtractionEngine.decompress(ByteArrayInputStream(encoded), destination,
                    "decoded.txt", (encoded.size - 1).toLong())
            }
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        } finally { destination.deleteRecursively() }
    }

    @Test fun cancellationRollsBackOutput() {
        val destination = tempDir()
        var cancelled = false
        try {
            assertThrows(ArchiveExtractionCancelledException::class.java) {
                ZstandardExtractionEngine.decompress(ByteArrayInputStream(zstd("x".repeat(1024 * 1024))),
                    destination, "decoded.txt", 2 * 1024 * 1024L,
                    cancellationRequested = { cancelled }, onProgress = { cancelled = true })
            }
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        } finally { destination.deleteRecursively() }
    }

    @Test fun refusesToOverwriteExistingOutput() {
        val destination = tempDir()
        File(destination, "decoded.txt").writeText("keep")
        try {
            assertThrows(ArchiveExtractionException::class.java) {
                ZstandardExtractionEngine.decompress(ByteArrayInputStream(zstd("replacement")),
                    destination, "decoded.txt", 1024 * 1024L)
            }
            assertEquals("keep", File(destination, "decoded.txt").readText())
        } finally { destination.deleteRecursively() }
    }

    @Test fun callerOwnedInputRemainsOpen() {
        val destination = tempDir()
        var closed = false
        val source = object : FilterInputStream(ByteArrayInputStream(zstd("open"))) {
            override fun close() { closed = true }
        }
        try {
            ZstandardExtractionEngine.decompress(source, destination, "decoded.txt", 1024 * 1024L)
            assertFalse(closed)
        } finally {
            source.close()
            destination.deleteRecursively()
        }
    }

    private fun zstd(value: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZstdOutputStream(output).use { it.write(value.toByteArray()) }
        return output.toByteArray()
    }

    private fun tempDir() = File(
        System.getProperty("java.io.tmpdir"), "arcnova-zstd-${System.nanoTime()}"
    ).apply { check(mkdirs()) }
}
