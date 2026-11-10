package com.arcnova.app.engine

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekableArchiveSpoolTest {
    private fun tempDir(): File = createTempDir(prefix = "arcnova-spool-test-")

    @Test fun spoolsWithinLimitAndDeletesFileWhenHandleCloses() {
        val directory = tempDir()
        val payload = ByteArray(128 * 1024) { (it % 239).toByte() }
        try {
            val spool = SeekableArchiveSpool.create(
                ByteArrayInputStream(payload), directory, payload.size.toLong()
            )
            assertTrue(spool.file.isFile)
            assertEquals(payload.size.toLong(), spool.sizeBytes)
            assertTrue(payload.contentEquals(spool.file.readBytes()))
            spool.close()
            assertFalse(spool.file.exists())
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun rejectsArchiveLargerThanLimitAndRemovesPartialFile() {
        val directory = tempDir()
        try {
            org.junit.Assert.assertThrows(ArchiveSpoolLimitException::class.java) {
                SeekableArchiveSpool.create(
                    ByteArrayInputStream(ByteArray(32)), directory, 16L
                )
            }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun cancellationRemovesPartialFile() {
        val directory = tempDir()
        var checks = 0
        try {
            org.junit.Assert.assertThrows(ArchiveSpoolCancelledException::class.java) {
                SeekableArchiveSpool.create(
                    ByteArrayInputStream(ByteArray(128 * 1024)),
                    directory,
                    256 * 1024L,
                    cancellationRequested = {
                        checks++
                        checks >= 2
                    }
                )
            }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun doesNotCloseCallerOwnedInputStream() {
        val directory = tempDir()
        var closed = false
        val input = object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
            override fun close() {
                closed = true
                super.close()
            }
        }
        try {
            val spool = SeekableArchiveSpool.create(input, directory, 3L)
            assertFalse(closed)
            spool.close()
            assertFalse(closed)
        } finally {
            input.close()
            directory.deleteRecursively()
        }
    }

    @Test fun rejectsNonDirectoryCachePath() {
        val parent = tempDir()
        val file = File(parent, "not-a-directory").apply { writeText("x") }
        try {
            org.junit.Assert.assertThrows(IOException::class.java) {
                SeekableArchiveSpool.create(ByteArrayInputStream(byteArrayOf(1)), file, 10L)
            }
        } finally {
            parent.deleteRecursively()
        }
    }
}
