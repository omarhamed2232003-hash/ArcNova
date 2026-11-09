package com.arcnova.app.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZipExtractionEngineTest {
    private fun archive(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }


    private fun corruptDataDescriptorCrc(bytes: ByteArray, entryName: String): ByteArray {
        val corrupted = bytes.copyOf()
        fun u16(offset: Int): Int =
            (corrupted[offset].toInt() and 0xff) or
                ((corrupted[offset + 1].toInt() and 0xff) shl 8)
        fun u32(offset: Int): Long =
            (corrupted[offset].toLong() and 0xff) or
                ((corrupted[offset + 1].toLong() and 0xff) shl 8) or
                ((corrupted[offset + 2].toLong() and 0xff) shl 16) or
                ((corrupted[offset + 3].toLong() and 0xff) shl 24)

        var offset = 0
        while (offset <= corrupted.size - 46) {
            if (u32(offset) == 0x02014b50L) {
                val compressedSize = u32(offset + 20)
                val nameLength = u16(offset + 28)
                val extraLength = u16(offset + 30)
                val commentLength = u16(offset + 32)
                val localHeaderOffset = u32(offset + 42).toInt()
                val name = String(corrupted, offset + 46, nameLength, Charsets.UTF_8)
                if (name == entryName) {
                    val localNameLength = u16(localHeaderOffset + 26)
                    val localExtraLength = u16(localHeaderOffset + 28)
                    val dataEnd = localHeaderOffset + 30 + localNameLength +
                        localExtraLength + compressedSize.toInt()
                    val descriptorOffset = if (u32(dataEnd) == 0x08074b50L) {
                        dataEnd + 4
                    } else {
                        dataEnd
                    }
                    corrupted[descriptorOffset] = (corrupted[descriptorOffset].toInt() xor 0x01).toByte()
                    return corrupted
                }
                offset += 46 + nameLength + extraLength + commentLength
            } else {
                offset++
            }
        }
        throw AssertionError("Central directory entry not found for $entryName")
    }

    private fun tempDir(): File = createTempDir(prefix = "arcnova-zip-test-")

    @Test fun extractsFilesAndReportsTotals() {
        val dir = tempDir()
        try {
            val result = ZipExtractionEngine.extract(
                ByteArrayInputStream(archive("folder/hello.txt" to "ArcNova".toByteArray())),
                dir
            )
            assertEquals(1, result.filesExtracted)
            assertEquals(7L, result.bytesWritten)
            assertEquals("ArcNova", File(dir, "folder/hello.txt").readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun rejectsParentTraversal() {
        val dir = tempDir()
        try {
            org.junit.Assert.assertThrows(UnsafeArchivePathException::class.java) {
                ZipExtractionEngine.extract(
                    ByteArrayInputStream(archive("../escape.txt" to byteArrayOf(1))),
                    dir
                )
            }
            assertFalse(File(dir.parentFile, "escape.txt").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun enforcesPerFileAndEntryLimits() {
        val dir = tempDir()
        try {
            org.junit.Assert.assertThrows(ZipExtractionLimitException::class.java) {
                ZipExtractionEngine.extract(
                    ByteArrayInputStream(archive("large.bin" to ByteArray(32))),
                    dir,
                    ExtractionPolicy(maxSingleFileBytes = 16)
                )
            }
            assertFalse(File(dir, "large.bin").exists())
            org.junit.Assert.assertThrows(ZipExtractionLimitException::class.java) {
                ZipExtractionEngine.extract(
                    ByteArrayInputStream(archive("a" to byteArrayOf(1), "b" to byteArrayOf(2))),
                    dir,
                    ExtractionPolicy(maxEntries = 1)
                )
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun rejectsDuplicatePathsAfterSeparatorNormalization() {
        val dir = tempDir()
        try {
            org.junit.Assert.assertThrows(ZipExtractionException::class.java) {
                ZipExtractionEngine.extract(
                    ByteArrayInputStream(archive("folder\\\\item.txt" to byteArrayOf(1), "folder/item.txt" to byteArrayOf(2))),
                    dir
                )
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun enforcesMaximumPathLengthAndDepth() {
        val dir = tempDir()
        try {
            org.junit.Assert.assertThrows(ZipExtractionLimitException::class.java) {
                ZipExtractionEngine.extract(ByteArrayInputStream(archive("longname.txt" to byteArrayOf(1))), dir,
                    ExtractionPolicy(maxPathLength = 5))
            }
            org.junit.Assert.assertThrows(ZipExtractionLimitException::class.java) {
                ZipExtractionEngine.extract(ByteArrayInputStream(archive("a/b/c.txt" to byteArrayOf(1))), dir,
                    ExtractionPolicy(maxPathDepth = 2))
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun rejectsInvalidPathLimits() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { ExtractionPolicy(maxPathLength = 0) }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { ExtractionPolicy(maxPathDepth = 0) }
    }

    @Test fun observesCancellationBeforeFirstEntry() {
        val dir = tempDir()
        try {
            org.junit.Assert.assertThrows(ZipExtractionCancelledException::class.java) {
                ZipExtractionEngine.extract(
                    ByteArrayInputStream(archive("a" to byteArrayOf(1))),
                    dir,
                    cancellationRequested = { true }
                )
            }
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun rejectsBackslashTraversal() {
        val dir = tempDir()
        try {
            org.junit.Assert.assertThrows(UnsafeArchivePathException::class.java) {
                ZipExtractionEngine.extract(
                    ByteArrayInputStream(archive("..\\\\escape.txt" to byteArrayOf(1))),
                    dir
                )
            }
            assertFalse(File(dir.parentFile, "escape.txt").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun refusesToOverwriteExistingFiles() {
        val dir = tempDir()
        try {
            File(dir, "same.txt").writeText("keep me")
            org.junit.Assert.assertThrows(ZipExtractionException::class.java) {
                ZipExtractionEngine.extract(
                    ByteArrayInputStream(archive("same.txt" to "replace me".toByteArray())),
                    dir
                )
            }
            assertEquals("keep me", File(dir, "same.txt").readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun rollsBackEarlierFilesAndCreatedDirectoriesWhenLaterEntryFails() {
        val dir = tempDir()
        try {
            org.junit.Assert.assertThrows(UnsafeArchivePathException::class.java) {
                ZipExtractionEngine.extract(
                    ByteArrayInputStream(archive(
                        "nested/first.txt" to "keep out of partial results".toByteArray(),
                        "../escape.txt" to byteArrayOf(1)
                    )),
                    dir
                )
            }
            assertFalse(File(dir, "nested/first.txt").exists())
            assertFalse(File(dir, "nested").exists())
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun rollbackPreservesPreExistingFilesAndDirectories() {
        val dir = tempDir()
        val existing = File(dir, "existing")
        existing.mkdirs()
        File(existing, "important.txt").writeText("do not delete")
        try {
            org.junit.Assert.assertThrows(UnsafeArchivePathException::class.java) {
                ZipExtractionEngine.extract(
                    ByteArrayInputStream(archive(
                        "existing/new.txt" to "temporary".toByteArray(),
                        "../escape.txt" to byteArrayOf(1)
                    )),
                    dir
                )
            }
            assertEquals("do not delete", File(existing, "important.txt").readText())
            assertTrue(existing.isDirectory)
            assertFalse(File(existing, "new.txt").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun cancellationAfterACompletedEntryRollsBackTheWholeOperation() {
        val dir = tempDir()
        var cancel = false
        try {
            org.junit.Assert.assertThrows(ZipExtractionCancelledException::class.java) {
                ZipExtractionEngine.extract(
                    ByteArrayInputStream(archive(
                        "first.txt" to byteArrayOf(1, 2, 3),
                        "second.txt" to byteArrayOf(4, 5, 6)
                    )),
                    dir,
                    cancellationRequested = { cancel },
                    onProgress = { cancel = true }
                )
            }
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun progressCallbackFailureRollsBackCreatedOutput() {
        val dir = tempDir()
        try {
            org.junit.Assert.assertThrows(IllegalStateException::class.java) {
                ZipExtractionEngine.extract(
                    ByteArrayInputStream(archive("nested/result.txt" to byteArrayOf(1, 2, 3))),
                    dir,
                    onProgress = { throw IllegalStateException("simulated observer failure") }
                )
            }
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun extractsMultiMegabyteFileWithExactContentsAndTotals() {
        val dir = tempDir()
        val payload = ByteArray(3 * 1024 * 1024) { index -> (index % 251).toByte() }
        try {
            val result = ZipExtractionEngine.extract(
                ByteArrayInputStream(archive("large/payload.bin" to payload)),
                dir
            )
            val extracted = File(dir, "large/payload.bin")
            assertEquals(1, result.filesExtracted)
            assertEquals(payload.size.toLong(), result.bytesWritten)
            assertEquals(payload.size.toLong(), extracted.length())
            assertTrue(payload.contentEquals(extracted.readBytes()))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun inputReadFailureRollsBackPreviouslyCreatedOutput() {
        val dir = tempDir()
        val payload = ByteArray(128 * 1024).also { java.util.Random(42L).nextBytes(it) }
        val bytes = archive("nested/large.bin" to payload)
        val failingInput = object : java.io.InputStream() {
            private val delegate = ByteArrayInputStream(bytes)
            private var consumed = 0
            override fun read(): Int {
                if (consumed >= 12 * 1024) throw java.io.IOException("simulated source read failure")
                val value = delegate.read()
                if (value >= 0) consumed++
                return value
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (consumed >= 12 * 1024) throw java.io.IOException("simulated source read failure")
                val allowed = minOf(length, 12 * 1024 - consumed)
                val count = delegate.read(buffer, offset, allowed)
                if (count > 0) consumed += count
                return count
            }
        }
        try {
            org.junit.Assert.assertThrows(java.io.IOException::class.java) {
                ZipExtractionEngine.extract(failingInput, dir)
            }
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun enforcesTotalOutputLimitAndRollsBackAllEntries() {
        val dir = tempDir()
        try {
            org.junit.Assert.assertThrows(ZipExtractionLimitException::class.java) {
                ZipExtractionEngine.extract(
                    ByteArrayInputStream(archive(
                        "first.bin" to ByteArray(8) { 1 },
                        "second.bin" to ByteArray(8) { 2 }
                    )),
                    dir,
                    ExtractionPolicy(maxTotalOutputBytes = 10)
                )
            }
            assertFalse(File(dir, "first.bin").exists())
            assertFalse(File(dir, "second.bin").exists())
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun cancellationDuringActiveFileWriteRollsBackPartialFile() {
        val dir = tempDir()
        val payload = ByteArray(512 * 1024).also { java.util.Random(73L).nextBytes(it) }
        var cancellationChecks = 0
        try {
            val error = org.junit.Assert.assertThrows(ZipExtractionCancelledException::class.java) {
                ZipExtractionEngine.extract(
                    ByteArrayInputStream(archive("nested/large.bin" to payload)),
                    dir,
                    cancellationRequested = {
                        cancellationChecks++
                        cancellationChecks >= 3
                    }
                )
            }
            assertTrue("Cancellation should occur after some output was written", error.bytesWritten > 0L)
            assertTrue(dir.listFiles().orEmpty().isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }


    @Test fun crcMismatchInLaterEntryRollsBackEarlierOutputAndPreservesExistingFiles() {
        val dir = tempDir()
        val existing = File(dir, "existing")
        existing.mkdirs()
        File(existing, "important.txt").writeText("keep this")
        val validArchive = archive(
            "first/complete.txt" to "must be rolled back".toByteArray(),
            "second/corrupt.txt" to "CRC must fail".toByteArray()
        )
        val corruptArchive = corruptDataDescriptorCrc(validArchive, "second/corrupt.txt")
        try {
            org.junit.Assert.assertThrows(java.util.zip.ZipException::class.java) {
                ZipExtractionEngine.extract(ByteArrayInputStream(corruptArchive), dir)
            }
            assertFalse(File(dir, "first/complete.txt").exists())
            assertFalse(File(dir, "second/corrupt.txt").exists())
            assertFalse(File(dir, "first").exists())
            assertFalse(File(dir, "second").exists())
            assertEquals("keep this", File(existing, "important.txt").readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun leavesCallerOwnedInputStreamOpen() {
        val dir = tempDir()
        var closed = false
        val input = object : ByteArrayInputStream(archive("a.txt" to byteArrayOf(1))) {
            override fun close() {
                closed = true
                super.close()
            }
        }
        try {
            ZipExtractionEngine.extract(input, dir)
            assertFalse(closed)
        } finally {
            input.close()
            dir.deleteRecursively()
        }
    }

}
