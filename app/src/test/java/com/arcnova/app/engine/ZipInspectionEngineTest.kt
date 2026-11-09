package com.arcnova.app.engine

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipInspectionEngineTest {
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

    @Test fun doesNotCloseCallerInput() {
        var closed = false
        val source = object : ByteArrayInputStream(archive("one" to byteArrayOf(1))) {
            override fun close() { closed = true; super.close() }
        }
        ZipInspectionEngine.inspect(source)
        assertFalse(closed)
    }
}
