package com.arcnova.app.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.LZMAOutputStream
import org.junit.Assert.*
import org.junit.Test

class Bzip2XzExtractionEngineTest {
    @Test fun decompressesBzip2ToCallerNamedFile() {
        assertDecoded(ArchiveFormat.BZIP2, bzip2("ArcNova BZIP2"))
    }

    @Test fun decompressesLzmaAloneToCallerNamedFile() {
        assertDecoded(ArchiveFormat.LZMA, lzma("ArcNova LZMA-alone"))
    }

    @Test fun decompressesXzToCallerNamedFile() {
        assertDecoded(ArchiveFormat.XZ, xz("ArcNova XZ"))
    }

    @Test fun decompressesConcatenatedBzip2Members() {
        val first = bzip2("first-")
        val second = bzip2("second")
        assertDecodedBytes(ArchiveFormat.BZIP2, first + second, "first-second")
    }

    @Test fun decompressesConcatenatedXzMembers() {
        val first = xz("first-")
        val second = xz("second")
        assertDecodedBytes(ArchiveFormat.XZ, first + second, "first-second")
    }

    @Test fun rejectsTruncatedLzmaAndRollsBack() {
        val destination = tempDir()
        val encoded = lzma("truncated LZMA payload").dropLast(3).toByteArray()
        try {
            assertThrows(Exception::class.java) {
                Bzip2XzExtractionEngine.decompress(
                    ArchiveFormat.LZMA, ByteArrayInputStream(encoded), destination,
                    "payload.bin", 1024 * 1024L
                )
            }
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test fun rejectsExpansionRatioBeyondConfiguredLimitAndRollsBack() {
        val destination = tempDir()
        try {
            assertThrows(ArchiveExtractionLimitException::class.java) {
                Bzip2XzExtractionEngine.decompress(
                    ArchiveFormat.XZ,
                    ByteArrayInputStream(xz("A".repeat(64 * 1024))),
                    destination,
                    "payload.bin",
                    1024 * 1024L,
                    policy = ExtractionPolicy(maxExpansionRatio = 2.0)
                )
            }
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test fun rejectsTruncatedCompressedStreamAndRollsBack() {
        val destination = tempDir()
        val encoded = xz("truncated payload").dropLast(4).toByteArray()
        try {
            assertThrows(Exception::class.java) {
                Bzip2XzExtractionEngine.decompress(
                    ArchiveFormat.XZ, ByteArrayInputStream(encoded), destination,
                    "payload.bin", 1024 * 1024L
                )
            }
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test fun rejectsOutputBeyondConfiguredLimitAndRollsBack() {
        val destination = tempDir()
        try {
            assertThrows(ArchiveExtractionLimitException::class.java) {
                Bzip2XzExtractionEngine.decompress(
                    ArchiveFormat.BZIP2,
                    ByteArrayInputStream(bzip2("x".repeat(4096))),
                    destination,
                    "payload.bin",
                    1024 * 1024L,
                    policy = ExtractionPolicy(maxSingleFileBytes = 128)
                )
            }
            assertFalse(File(destination, "payload.bin").exists())
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test fun rejectsCompressedInputBeyondConfiguredLimit() {
        val destination = tempDir()
        val encoded = bzip2("payload")
        try {
            assertThrows(ArchiveExtractionLimitException::class.java) {
                Bzip2XzExtractionEngine.decompress(
                    ArchiveFormat.BZIP2, ByteArrayInputStream(encoded), destination,
                    "payload.bin", (encoded.size - 1).toLong()
                )
            }
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test fun cancellationRollsBackOutput() {
        val destination = tempDir()
        var cancelled = false
        try {
            assertThrows(ArchiveExtractionCancelledException::class.java) {
                Bzip2XzExtractionEngine.decompress(
                    ArchiveFormat.XZ, ByteArrayInputStream(xz("x".repeat(1024 * 1024))),
                    destination, "payload.bin", 1024 * 1024L,
                    cancellationRequested = { cancelled },
                    onProgress = { cancelled = true }
                )
            }
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test fun callerOwnedInputRemainsOpen() {
        val destination = tempDir()
        var closed = false
        val source = object : FilterInputStream(ByteArrayInputStream(bzip2("open"))) {
            override fun close() { closed = true }
        }
        try {
            Bzip2XzExtractionEngine.decompress(
                ArchiveFormat.BZIP2, source, destination, "payload.txt", 1024 * 1024L
            )
            assertFalse(closed)
        } finally {
            source.close()
            destination.deleteRecursively()
        }
    }

    private fun assertDecodedBytes(format: ArchiveFormat, encoded: ByteArray, expected: String) {
        val destination = tempDir()
        try {
            Bzip2XzExtractionEngine.decompress(
                format, ByteArrayInputStream(encoded), destination, "decoded.txt", 1024 * 1024L
            )
            assertEquals(expected, File(destination, "decoded.txt").readText())
        } finally {
            destination.deleteRecursively()
        }
    }

    private fun assertDecoded(format: ArchiveFormat, encoded: ByteArray) {
        val destination = tempDir()
        try {
            val result = Bzip2XzExtractionEngine.decompress(
                format, ByteArrayInputStream(encoded), destination, "decoded.txt", 1024 * 1024L
            )
            assertEquals(1, result.filesExtracted)
            assertEquals("ArcNova ${format.label}", File(destination, "decoded.txt").readText())
        } finally {
            destination.deleteRecursively()
        }
    }

    private fun bzip2(value: String): ByteArray {
        val output = ByteArrayOutputStream()
        BZip2CompressorOutputStream(output).use { it.write(value.toByteArray()) }
        return output.toByteArray()
    }

    private fun lzma(value: String): ByteArray {
        val bytes = value.toByteArray()
        val output = ByteArrayOutputStream()
        LZMAOutputStream(output, LZMA2Options(), bytes.size.toLong()).use { it.write(bytes) }
        return output.toByteArray()
    }

    private fun xz(value: String): ByteArray {
        val output = ByteArrayOutputStream()
        XZCompressorOutputStream(output).use { it.write(value.toByteArray()) }
        return output.toByteArray()
    }

    private fun tempDir() = File(
        System.getProperty("java.io.tmpdir"),
        "arcnova-bzip2-xz-${System.nanoTime()}"
    ).apply { check(mkdirs()) }
}
