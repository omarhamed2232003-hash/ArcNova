package com.arcnova.app.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CentralDirectoryZipBackendTest {
    private fun tempDir(): File = createTempDir(prefix = "arcnova-cd-backend-")

    private fun archive(name: String, payload: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(payload)
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun centralDirectoryOffset(bytes: ByteArray): Int {
        for (offset in 0..bytes.size - 4) {
            val signature = (bytes[offset].toLong() and 0xff) or
                ((bytes[offset + 1].toLong() and 0xff) shl 8) or
                ((bytes[offset + 2].toLong() and 0xff) shl 16) or
                ((bytes[offset + 3].toLong() and 0xff) shl 24)
            if (signature == 0x02014b50L) return offset
        }
        throw AssertionError("Central Directory signature not found")
    }

    private fun writeLittleEndianInt(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { bytes[offset + it] = (value ushr (it * 8)).toByte() }
    }

    @Test fun opensCentralDirectoryAndCopiesPayloadWithCrcValidation() {
        val directory = tempDir()
        val payload = "verified central directory payload".toByteArray()
        try {
            val archive = CentralDirectoryZipBackend.open(
                ByteArrayInputStream(archive("folder/data.txt", payload)),
                directory,
                1024 * 1024L
            )
            val spoolFile = directory.listFiles()!!.single()
            try {
                assertEquals(1, archive.entries.size)
                val output = ByteArrayOutputStream()
                val result = archive.copyEntry(archive.entries.single(), output)
                assertTrue(payload.contentEquals(output.toByteArray()))
                assertEquals(payload.size.toLong(), result.bytesCopied)
                assertEquals(payload.size.toLong(), archive.entries.single().size)
            } finally {
                archive.close()
            }
            assertFalse(spoolFile.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun closeIsIdempotentCleansSpoolAndPreventsFurtherCopies() {
        val directory = tempDir()
        val opened = CentralDirectoryZipBackend.open(
            ByteArrayInputStream(archive("close.txt", "payload".toByteArray())),
            directory,
            4096
        )
        val spool = directory.listFiles().orEmpty().single()
        assertTrue(spool.exists())
        opened.close()
        assertFalse(spool.exists())
        // A second close must be harmless and must not recreate or leak resources.
        opened.close()
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            opened.copyEntry(opened.entries.single(), ByteArrayOutputStream())
        }
        directory.deleteRecursively()
    }

    @Test fun rejectsUnsafePathBeforeReturningArchive() {
        val directory = tempDir()
        try {
            org.junit.Assert.assertThrows(CentralDirectoryZipUnsafePathException::class.java) {
                CentralDirectoryZipBackend.open(
                    ByteArrayInputStream(archive("../escape.txt", byteArrayOf(1))),
                    directory,
                    1024
                )
            }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun rejectsDeclaredOutputLimitBeforePayloadCopy() {
        val directory = tempDir()
        try {
            org.junit.Assert.assertThrows(CentralDirectoryZipException::class.java) {
                CentralDirectoryZipBackend.open(
                    ByteArrayInputStream(archive("large.bin", ByteArray(128) { it.toByte() })),
                    directory,
                    1024,
                    CentralDirectoryZipPolicy(maxSingleFileBytes = 64)
                )
            }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun rejectsCentralDirectoryCrcMismatchDuringCopy() {
        val directory = tempDir()
        val payload = "central directory crc mismatch".toByteArray()
        val bytes = archive("crc.txt", payload)
        val central = centralDirectoryOffset(bytes)
        val crcOffset = central + 16
        val declared = (bytes[crcOffset].toLong() and 0xff) or
            ((bytes[crcOffset + 1].toLong() and 0xff) shl 8) or
            ((bytes[crcOffset + 2].toLong() and 0xff) shl 16) or
            ((bytes[crcOffset + 3].toLong() and 0xff) shl 24)
        writeLittleEndianInt(bytes, crcOffset, declared.toInt() xor 1)
        try {
            val archive = CentralDirectoryZipBackend.open(ByteArrayInputStream(bytes), directory, 1024 * 1024L)
            try {
                org.junit.Assert.assertThrows(CentralDirectoryZipCrcException::class.java) {
                    archive.copyEntry(archive.entries.single(), ByteArrayOutputStream())
                }
            } finally {
                archive.close()
            }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun readsZip64AndCleansSpoolOnClose() {
        val directory = tempDir()
        val archiveFile = File.createTempFile("arcnova-zip64-fixture-", ".zip", directory)
        val payload = "zip64 backend".toByteArray()
        try {
            ZipArchiveOutputStream(archiveFile).use { output ->
                output.setUseZip64(Zip64Mode.Always)
                output.putArchiveEntry(ZipArchiveEntry("data.txt"))
                output.write(payload)
                output.closeArchiveEntry()
            }
            val bytes = archiveFile.readBytes()
            val opened = CentralDirectoryZipBackend.open(
                ByteArrayInputStream(bytes), directory, 1024 * 1024L
            )
            try {
                val output = ByteArrayOutputStream()
                opened.copyEntry(opened.entries.single(), output)
                assertTrue(payload.contentEquals(output.toByteArray()))
            } finally {
                opened.close()
            }
            assertEquals(1, directory.listFiles().orEmpty().size) // only the test fixture remains
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun rejectsFilePathUsedAsParentDirectory() {
        val directory = tempDir()
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("node"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("node/child.txt"))
            zip.write(byteArrayOf(2))
            zip.closeEntry()
        }
        try {
            org.junit.Assert.assertThrows(CentralDirectoryZipException::class.java) {
                CentralDirectoryZipBackend.open(ByteArrayInputStream(output.toByteArray()), directory, 4096)
            }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun rejectsDeclaredExpansionRatioBeforePayloadCopy() {
        val directory = tempDir()
        try {
            org.junit.Assert.assertThrows(CentralDirectoryZipException::class.java) {
                CentralDirectoryZipBackend.open(
                    ByteArrayInputStream(archive("repeat.txt", ByteArray(16 * 1024) { 65 })),
                    directory,
                    64 * 1024L,
                    CentralDirectoryZipPolicy(maxExpansionRatio = 1.1)
                )
            }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun cancellationDuringPayloadCopyLeavesCallerOutputOpen() {
        val directory = tempDir()
        var cancelled = false
        val opened = CentralDirectoryZipBackend.open(
            ByteArrayInputStream(archive("cancel.bin", ByteArray(128 * 1024) { it.toByte() })),
            directory,
            256 * 1024L,
            cancellationRequested = { cancelled }
        )
        val output = object : ByteArrayOutputStream() {
            var closedByBackend = false
            override fun close() {
                closedByBackend = true
                super.close()
            }
        }
        try {
            cancelled = true
            org.junit.Assert.assertThrows(CentralDirectoryZipCancelledException::class.java) {
                opened.copyEntry(opened.entries.single(), output)
            }
            assertFalse(output.closedByBackend)
        } finally {
            opened.close()
            assertTrue(directory.listFiles().orEmpty().isEmpty())
            directory.deleteRecursively()
        }
    }


    @Test fun rejectsChildPathBeforeItsFileParentRegardlessOfCentralDirectoryOrder() {
        val directory = tempDir()
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("node/child.txt"))
            zip.write(byteArrayOf(2))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("node"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        try {
            org.junit.Assert.assertThrows(CentralDirectoryZipException::class.java) {
                CentralDirectoryZipBackend.open(
                    ByteArrayInputStream(output.toByteArray()),
                    directory,
                    4096
                )
            }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }


    @Test fun enforcesActualTotalOutputLimitWhileStreamingPayload() {
        val directory = tempDir()
        val payload = ByteArray(32) { (it + 1).toByte() }
        val bytes = archive("actual.bin", payload)
        // Lie in the Central Directory: declare one uncompressed byte, while the
        // compressed stream still expands to the complete payload.
        val central = centralDirectoryOffset(bytes)
        writeLittleEndianInt(bytes, central + 24, 1)
        try {
            val opened = CentralDirectoryZipBackend.open(
                ByteArrayInputStream(bytes),
                directory,
                4096,
                CentralDirectoryZipPolicy(maxTotalOutputBytes = 8)
            )
            try {
                val output = ByteArrayOutputStream()
                org.junit.Assert.assertThrows(CentralDirectoryZipException::class.java) {
                    opened.copyEntry(opened.entries.single(), output)
                }
                assertTrue("The actual stream should stop at the configured cap", output.size() <= 8)
            } finally {
                opened.close()
            }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun outputWriteFailureDoesNotCloseCallerStreamAndArchiveCanStillBeClosed() {
        val directory = tempDir()
        val opened = CentralDirectoryZipBackend.open(
            ByteArrayInputStream(archive("write-fail.bin", ByteArray(64) { it.toByte() })),
            directory,
            4096
        )
        var closeCalled = false
        val failingOutput = object : java.io.OutputStream() {
            private var written = 0
            override fun write(value: Int) {
                if (written++ >= 7) throw java.io.IOException("simulated destination failure")
            }
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                repeat(length) { write(bytes[offset + it].toInt()) }
            }
            override fun close() { closeCalled = true }
        }
        try {
            org.junit.Assert.assertThrows(java.io.IOException::class.java) {
                opened.copyEntry(opened.entries.single(), failingOutput)
            }
            assertFalse("Backend must not close caller-owned output", closeCalled)
        } finally {
            opened.close()
        }
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        directory.deleteRecursively()
    }

    @Test fun rejectsEncryptedEntryDuringPreflightBeforeReturningArchive() {
        val directory = tempDir()
        val bytes = archive("secret.txt", "payload".toByteArray())
        val central = centralDirectoryOffset(bytes)
        // General-purpose bit 0 marks traditional ZIP encryption in both headers.
        bytes[6] = (bytes[6].toInt() or 1).toByte()
        bytes[central + 8] = (bytes[central + 8].toInt() or 1).toByte()
        try {
            org.junit.Assert.assertThrows(CentralDirectoryZipUnsupportedFeatureException::class.java) {
                CentralDirectoryZipBackend.open(ByteArrayInputStream(bytes), directory, 4096)
            }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

}
