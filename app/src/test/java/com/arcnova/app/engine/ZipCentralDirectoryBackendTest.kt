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

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun writeLittleEndianInt(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun createStoredArchive(name: String, payload: ByteArray): ByteArray {
        val crc = java.util.zip.CRC32().apply { update(payload) }.value
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(
                ZipEntry(name).apply {
                    method = ZipEntry.STORED
                    size = payload.size.toLong()
                    compressedSize = payload.size.toLong()
                    this.crc = crc
                },
            )
            zip.write(payload)
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun insertOrphanLocalRecord(archive: ByteArray, orphan: ByteArray): ByteArray {
        val archiveCentralOffset = firstCentralDirectoryOffset(archive)
        val orphanNameLength = (orphan[26].toInt() and 0xff) or
            ((orphan[27].toInt() and 0xff) shl 8)
        val orphanExtraLength = (orphan[28].toInt() and 0xff) or
            ((orphan[29].toInt() and 0xff) shl 8)
        val orphanCompressedSize = littleEndianInt(orphan, 18)
        val orphanRecordLength = 30 + orphanNameLength + orphanExtraLength + orphanCompressedSize
        val orphanRecord = orphan.copyOfRange(0, orphanRecordLength)
        val combined = ByteArray(archive.size + orphanRecord.size)
        archive.copyInto(combined, 0, 0, archiveCentralOffset)
        orphanRecord.copyInto(combined, archiveCentralOffset)
        archive.copyInto(combined, archiveCentralOffset + orphanRecord.size, archiveCentralOffset)

        var eocdOffset = -1
        for (offset in combined.size - 22 downTo 0) {
            if (littleEndianInt(combined, offset) == 0x06054b50) {
                eocdOffset = offset
                break
            }
        }
        check(eocdOffset >= 0) { "End of Central Directory not found" }
        val originalCentralOffset = littleEndianInt(combined, eocdOffset + 16) - orphanRecord.size
        writeLittleEndianInt(combined, eocdOffset + 16, originalCentralOffset + orphanRecord.size)
        return combined
    }

    @Test fun ignoresLocalFileRecordThatIsAbsentFromCentralDirectory() {
        val archive = File.createTempFile("arcnova-orphan-", ".zip")
        try {
            val validArchive = createArchive()
            val orphan = createStoredArchive("orphan/not-indexed.txt", "orphan".toByteArray())
            archive.writeBytes(insertOrphanLocalRecord(validArchive, orphan))

            ZipFile.builder().setFile(archive).get().use { zip ->
                val entries = Collections.list(zip.entries)
                assertEquals(1, entries.size)
                assertEquals("folder/valid.txt", entries.single().name)
                assertEquals(null, zip.getEntry("orphan/not-indexed.txt"))
            }
        } finally {
            archive.delete()
        }
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
