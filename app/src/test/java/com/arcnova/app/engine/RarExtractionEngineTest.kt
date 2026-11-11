package com.arcnova.app.engine

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class RarExtractionEngineTest {
    private data class Fixture(val entry: RarExtractionEngine.Entry, val data: ByteArray = byteArrayOf())
    private class FakeReader(private val fixtures: List<Fixture>) : RarExtractionEngine.Reader {
        private var index = -1
        private var offset = 0
        override fun nextEntry(): RarExtractionEngine.Entry? {
            index++
            offset = 0
            return fixtures.getOrNull(index)?.entry
        }
        override fun readData(buffer: ByteBuffer): Int {
            val data = fixtures.getOrNull(index)?.data ?: return 0
            if (offset >= data.size) return 0
            val count = minOf(buffer.remaining(), data.size - offset)
            buffer.put(data, offset, count)
            offset += count
            return count
        }
        override fun skipData() { offset = fixtures.getOrNull(index)?.data?.size ?: 0 }
        override fun close() = Unit
    }

    @Test fun extractsRegularFilesAndDirectories() {
        val cache = Files.createTempDirectory("arcnova-rar-ok").toFile()
        val destination = File(cache, "out")
        val data = "rar payload".toByteArray()
        val result = RarExtractionEngine.extractWithReaderFactory(
            ByteArrayInputStream("synthetic archive".toByteArray()), cache, destination, 1024,
            readerFactory = RarExtractionEngine.ReaderFactory {
                FakeReader(listOf(
                    Fixture(RarExtractionEngine.Entry("folder", RarExtractionEngine.EntryType.DIRECTORY, 0)),
                    Fixture(RarExtractionEngine.Entry("folder/data.txt", RarExtractionEngine.EntryType.FILE, data.size.toLong()), data)
                ))
            }
        )
        assertEquals(2, result.entriesProcessed)
        assertEquals(1, result.filesExtracted)
        assertArrayEquals(data, File(destination, "folder/data.txt").readBytes())
        cache.deleteRecursively()
    }

    @Test fun rejectsTraversalAndRollsBack() {
        val cache = Files.createTempDirectory("arcnova-rar-path").toFile()
        val destination = File(cache, "out")
        try {
            RarExtractionEngine.extractWithReaderFactory(
                ByteArrayInputStream(byteArrayOf(1)), cache, destination, 1024,
                readerFactory = RarExtractionEngine.ReaderFactory {
                    FakeReader(listOf(Fixture(RarExtractionEngine.Entry("../escape", RarExtractionEngine.EntryType.FILE, 1), byteArrayOf(1))))
                }
            )
            fail("Expected unsafe path rejection")
        } catch (_: UnsafeArchivePathException) {
            assertFalse(destination.exists())
        } finally { cache.deleteRecursively() }
    }

    @Test fun rejectsEncryptedEntriesAndLinks() {
        val cache = Files.createTempDirectory("arcnova-rar-flags").toFile()
        val destination = File(cache, "out")
        try {
            RarExtractionEngine.extractWithReaderFactory(
                ByteArrayInputStream(byteArrayOf(1)), cache, destination, 1024,
                readerFactory = RarExtractionEngine.ReaderFactory {
                    FakeReader(listOf(Fixture(RarExtractionEngine.Entry("secret", RarExtractionEngine.EntryType.FILE, 1, true), byteArrayOf(1))))
                }
            )
            fail("Expected encrypted-entry rejection")
        } catch (_: ArchiveExtractionUnsupportedFeatureException) {
            assertFalse(destination.exists())
        } finally { cache.deleteRecursively() }
    }

    @Test fun enforcesOutputLimits() {
        val cache = Files.createTempDirectory("arcnova-rar-limit").toFile()
        val destination = File(cache, "out")
        try {
            RarExtractionEngine.extractWithReaderFactory(
                ByteArrayInputStream(byteArrayOf(1)), cache, destination, 1024,
                policy = ExtractionPolicy(maxSingleFileBytes = 2, maxTotalOutputBytes = 2),
                readerFactory = RarExtractionEngine.ReaderFactory {
                    FakeReader(listOf(Fixture(RarExtractionEngine.Entry("large", RarExtractionEngine.EntryType.FILE, 3), byteArrayOf(1, 2, 3))))
                }
            )
            fail("Expected limit rejection")
        } catch (_: ArchiveExtractionLimitException) {
            assertFalse(destination.exists())
        } finally { cache.deleteRecursively() }
    }

    @Test fun cancellationRollsBackPartialOutput() {
        val cache = Files.createTempDirectory("arcnova-rar-cancel").toFile()
        val destination = File(cache, "out")
        var checks = 0
        try {
            RarExtractionEngine.extractWithReaderFactory(
                ByteArrayInputStream(byteArrayOf(1)), cache, destination, 1024,
                cancellationRequested = { ++checks > 2 },
                readerFactory = RarExtractionEngine.ReaderFactory {
                    FakeReader(listOf(Fixture(RarExtractionEngine.Entry("data", RarExtractionEngine.EntryType.FILE, 4), byteArrayOf(1, 2, 3, 4))))
                }
            )
            fail("Expected cancellation")
        } catch (_: ArchiveExtractionCancelledException) {
            assertFalse(destination.exists())
        } finally { cache.deleteRecursively() }
    }
}
