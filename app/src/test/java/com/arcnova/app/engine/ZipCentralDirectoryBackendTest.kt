package com.arcnova.app.engine

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ZipCentralDirectoryBackendTest {
    private fun createArchive(): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("folder/valid.txt"))
            zip.write("central-directory-backed".toByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun firstCentralDirectoryOffset(bytes: ByteArray): Int {
        for (offset in 0..bytes.size - 4) {
            val signature = (bytes[offset].toLong() and 0xff) or
                ((bytes[offset + 1].toLong() and 0xff) shl 8) or
                ((bytes[offset + 2].toLong() and 0xff) shl 16) or
                ((bytes[offset + 3].toLong() and 0xff) shl 24)
            if (signature == 0x02014b50L) return offset
        }
        throw AssertionError("Central Directory signature not found")
    }

    @Test fun readsEntriesThroughCentralDirectoryAndStreamsPayload() {
        val archive = File.createTempFile("arcnova-central-", ".zip")
        try {
            archive.writeBytes(createArchive())
            ZipFile.builder().setFile(archive).get().use { zip ->
                val entries = Collections.list(zip.entries)
                assertEquals(1, entries.size)
                assertEquals("folder/valid.txt", entries.single().name)
                val header = zip.getEntry("folder/valid.txt")
                assertNotNull(header)
                val payload = zip.getInputStream(header).use { it.readBytes() }
                assertTrue("central-directory-backed".toByteArray().contentEquals(payload))
            }
        } finally {
            archive.delete()
        }
    }

    @Test fun rejectsArchiveWhoseCentralDirectoryWasRemoved() {
        val archive = File.createTempFile("arcnova-truncated-", ".zip")
        try {
            val bytes = createArchive()
            archive.writeBytes(bytes.copyOf(firstCentralDirectoryOffset(bytes)))
            assertThrows(IOException::class.java) {
                ZipFile.builder().setFile(archive).get().use { zip ->
                    Collections.list(zip.entries)
                }
            }
        } finally {
            archive.delete()
        }
    }
}
