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
}
