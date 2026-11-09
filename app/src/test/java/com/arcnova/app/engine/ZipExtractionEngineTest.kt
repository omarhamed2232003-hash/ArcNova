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
