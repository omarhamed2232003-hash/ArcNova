package com.arcnova.app.engine

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.CRC32

class ZipInspectionEngineTest {
    private fun ByteArray.indexOfSubsequence(needle: ByteArray): Int =
        indices.firstOrNull { start ->
            start + needle.size <= size &&
                needle.indices.all { index -> this[start + index] == needle[index] }
        } ?: -1

    private fun archive(vararg files: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    @Test fun inspectsEntriesWithoutExtracting() {
        val report = ZipInspectionEngine.inspect(
            ByteArrayInputStream(archive("docs/readme.txt" to "hello".toByteArray()))
        )
        assertEquals(1, report.fileCount)
        assertEquals("docs/readme.txt", report.entries.single().name)
        assertEquals(5L, report.entries.single().actualSizeBytes)
        assertEquals(5L, report.totalUncompressedBytes)
        assertEquals(0, report.unsafePathCount)
    }

    @Test fun reportsUnsafeEntryNamesInsteadOfWritingThem() {
        val report = ZipInspectionEngine.inspect(
            ByteArrayInputStream(archive("../outside.txt" to byteArrayOf(1)))
        )
        assertEquals(1, report.unsafePathCount)
        assertTrue(report.entries.single().hasUnsafePath)
    }

    @Test fun detectsDuplicateNamesAfterSeparatorNormalization() {
        val report = ZipInspectionEngine.inspect(ByteArrayInputStream(archive(
            "folder\\\\item.txt" to byteArrayOf(1), "folder/item.txt" to byteArrayOf(2)
        )))
        assertEquals(1, report.duplicateNameCount)
        assertTrue(report.entries.last().isDuplicateName)
    }

    @Test fun enforcesEntryAndOutputLimits() {
        val bytes = archive("one" to ByteArray(32) { 1 })
        try {
            ZipInspectionEngine.inspect(
                ByteArrayInputStream(bytes),
                ZipInspectionLimits(maxSingleEntryBytes = 8)
            )
            fail("Expected per-entry limit")
        } catch (_: ZipInspectionLimitException) { }
    }

    @Test fun supportsCancellationBeforeReading() {
        try {
            ZipInspectionEngine.inspect(
                ByteArrayInputStream(archive("one" to byteArrayOf(1))),
                cancellationRequested = { true }
            )
            fail("Expected cancellation")
        } catch (_: ZipInspectionCancelledException) { }
    }

    @Test fun centralDirectoryInspectionValidatesAndReportsPayloads() {
        val cache = createTempDir(prefix = "arcnova-inspect-")
        val report = ZipInspectionEngine.inspectCentralDirectory(
            input = ByteArrayInputStream(archive(
                "docs/readme.txt" to "hello".toByteArray(),
                "empty.bin" to byteArrayOf()
            )),
            cacheDirectory = cache,
            maxArchiveBytes = 4096
        )
        assertEquals(2, report.fileCount)
        assertEquals(5L, report.totalUncompressedBytes)
        assertEquals(5L, report.entries.first { it.name == "docs/readme.txt" }.actualSizeBytes)
        assertTrue(cache.listFiles().orEmpty().isEmpty())
        cache.deleteRecursively()
    }

    @Test fun centralDirectoryInspectionRejectsUnsafePathsBeforeReporting() {
        val cache = createTempDir(prefix = "arcnova-inspect-")
        try {
            ZipInspectionEngine.inspectCentralDirectory(
                input = ByteArrayInputStream(archive("../outside.txt" to byteArrayOf(1))),
                cacheDirectory = cache,
                maxArchiveBytes = 4096
            )
            fail("Expected unsafe path rejection")
        } catch (error: ZipInspectionUnsafePathException) {
            assertEquals("../outside.txt", error.entryName)
            assertTrue(cache.listFiles().orEmpty().isEmpty())
        } finally {
            cache.deleteRecursively()
        }
    }


    @Test fun centralDirectoryInspectionReportsCrcMismatchAsIntegrityFailure() {
        val cache = createTempDir(prefix = "arcnova-inspect-crc-")
        try {
            val payload = "original".toByteArray()
            val output = ByteArrayOutputStream()
            ZipOutputStream(output).use { zip ->
                val crc = CRC32().apply { update(payload) }
                val entry = ZipEntry("payload.txt").apply {
                    method = ZipEntry.STORED
                    size = payload.size.toLong()
                    compressedSize = payload.size.toLong()
                    this.crc = crc.value
                }
                zip.putNextEntry(entry)
                zip.write(payload)
                zip.closeEntry()
            }
            val valid = output.toByteArray()
            val corrupted = valid.clone()
            val payloadOffset = valid.indexOfSubsequence(payload)
            assertTrue(payloadOffset >= 0)
            corrupted[payloadOffset] = 'X'.code.toByte()
            try {
                ZipInspectionEngine.inspectCentralDirectory(
                    input = ByteArrayInputStream(corrupted),
                    cacheDirectory = cache,
                    maxArchiveBytes = 4096
                )
                fail("Expected CRC integrity failure")
            } catch (error: ZipInspectionIntegrityException) {
                assertEquals("payload.txt", error.entryName)
                assertNotEquals(error.expectedCrc32, error.actualCrc32)
            }
            assertTrue(cache.listFiles().orEmpty().isEmpty())
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test fun centralDirectoryInspectionReportsPathConflictsAsFormatFailure() {
        val cache = createTempDir(prefix = "arcnova-inspect-path-conflict-")
        try {
            ZipInspectionEngine.inspectCentralDirectory(
                input = ByteArrayInputStream(archive(
                    "folder/child.txt" to byteArrayOf(1),
                    "folder" to byteArrayOf(2)
                )),
                cacheDirectory = cache,
                maxArchiveBytes = 4096
            )
            fail("Expected invalid archive path conflict")
        } catch (error: ZipInspectionFormatException) {
            assertTrue(error.message.orEmpty().contains("conflicts"))
            assertTrue(cache.listFiles().orEmpty().isEmpty())
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test fun centralDirectoryInspectionHonorsCancellationAndCleansSpool() {
        val cache = createTempDir(prefix = "arcnova-inspect-")
        try {
            ZipInspectionEngine.inspectCentralDirectory(
                input = ByteArrayInputStream(archive("one.txt" to byteArrayOf(1))),
                cacheDirectory = cache,
                maxArchiveBytes = 4096,
                cancellationRequested = { true }
            )
            fail("Expected cancellation")
        } catch (_: ZipInspectionCancelledException) {
            assertTrue(cache.listFiles().orEmpty().isEmpty())
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test fun centralDirectoryInspectionMapsArchiveInputLimitAndCleansSpool() {
        val cache = createTempDir(prefix = "arcnova-inspect-limit-")
        try {
            ZipInspectionEngine.inspectCentralDirectory(
                input = ByteArrayInputStream(archive("large-enough.txt" to ByteArray(128))),
                cacheDirectory = cache,
                maxArchiveBytes = 16
            )
            fail("Expected archive input limit")
        } catch (_: ZipInspectionLimitException) {
            assertTrue(cache.listFiles().orEmpty().isEmpty())
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test fun centralDirectoryInspectionDoesNotCloseCallerInput() {
        val cache = createTempDir(prefix = "arcnova-inspect-owner-")
        var closed = false
        val source = object : ByteArrayInputStream(archive("one.txt" to byteArrayOf(1))) {
            override fun close() { closed = true; super.close() }
        }
        try {
            ZipInspectionEngine.inspectCentralDirectory(
                input = source,
                cacheDirectory = cache,
                maxArchiveBytes = 4096
            )
            assertFalse(closed)
            assertTrue(cache.listFiles().orEmpty().isEmpty())
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test fun doesNotCloseCallerInput() {
        var closed = false
        val source = object : ByteArrayInputStream(archive("one" to byteArrayOf(1))) {
            override fun close() { closed = true; super.close() }
        }
        ZipInspectionEngine.inspect(source)
        assertFalse(closed)
    }
}
