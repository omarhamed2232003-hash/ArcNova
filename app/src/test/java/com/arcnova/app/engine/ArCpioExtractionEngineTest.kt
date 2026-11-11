package com.arcnova.app.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import org.apache.commons.compress.archivers.ar.ArArchiveEntry
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream
import org.apache.commons.compress.archivers.cpio.CpioConstants
import org.junit.Assert.*
import org.junit.Test

class ArCpioExtractionEngineTest {
    @Test fun extractsDebianStyleArMembersAndPreservesNames() {
        val destination = tempDir()
        val control = "control archive".toByteArray()
        val data = "data archive".toByteArray()
        try {
            val result = ArCpioExtractionEngine.extractAr(
                ByteArrayInputStream(arArchive("debian-binary" to "2.0\n".toByteArray(),
                    "control.tar.xz" to control, "data.tar.xz" to data)),
                destination,
                maxArchiveBytes = 1024 * 1024L
            )
            assertEquals(3, result.filesExtracted)
            assertEquals("2.0\n", File(destination, "debian-binary").readText())
            assertArrayEquals(control, File(destination, "control.tar.xz").readBytes())
            assertArrayEquals(data, File(destination, "data.tar.xz").readBytes())
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test fun arTraversalRollsBackCreatedMembersAndPreservesExistingContent() {
        val destination = tempDir()
        val existing = File(destination, "keep.txt").apply { writeText("preserve") }
        try {
            assertThrows(UnsafeArchivePathException::class.java) {
                ArCpioExtractionEngine.extractAr(
                    ByteArrayInputStream(arArchive(
                        "valid.txt" to "remove".toByteArray(),
                        "../escape.txt" to "blocked".toByteArray()
                    )),
                    destination,
                    maxArchiveBytes = 1024 * 1024L
                )
            }
            assertFalse(File(destination, "valid.txt").exists())
            assertFalse(File(destination.parentFile, "escape.txt").exists())
            assertEquals("preserve", existing.readText())
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test fun arRejectsNonRegularMembersAndRollsBack() {
        val destination = tempDir()
        val output = ByteArrayOutputStream()
        try {
            ArArchiveOutputStream(output).use { archive ->
                archive.putArchiveEntry(ArArchiveEntry("valid.txt", 6))
                archive.write("remove".toByteArray())
                archive.closeArchiveEntry()
                archive.putArchiveEntry(ArArchiveEntry("link", 6, 0, 0, 0xA1FF, 0))
                archive.write("target".toByteArray())
                archive.closeArchiveEntry()
                archive.finish()
            }
            assertThrows(ArchiveExtractionUnsupportedFeatureException::class.java) {
                ArCpioExtractionEngine.extractAr(
                    ByteArrayInputStream(output.toByteArray()),
                    destination,
                    maxArchiveBytes = 1024 * 1024L
                )
            }
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test fun cpioNewAsciiWithChecksumExtractsFilesAndDirectories() {
        val destination = tempDir()
        val file = "checksum-verified".toByteArray()
        try {
            val result = ArCpioExtractionEngine.extractCpio(
                ByteArrayInputStream(cpioArchive(CpioConstants.FORMAT_NEW_CRC,
                    CpioFixture("usr", CpioConstants.C_ISDIR, byteArrayOf(), 2),
                    CpioFixture("usr/share/app.txt", CpioConstants.C_ISREG, file, 1))),
                destination,
                maxArchiveBytes = 1024 * 1024L
            )
            assertEquals(1, result.filesExtracted)
            assertEquals(1, result.directoriesCreated)
            assertArrayEquals(file, File(destination, "usr/share/app.txt").readBytes())
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test fun rejectsLegacyCpioHeadersBeforePassingThemToTheParser() {
        listOf(CpioConstants.FORMAT_OLD_ASCII, CpioConstants.FORMAT_OLD_BINARY).forEach { format ->
            val destination = tempDir()
            try {
                val legacy = cpioArchive(format, CpioFixture("legacy.txt", CpioConstants.C_ISREG, "legacy".toByteArray(), 1))
                assertThrows(ArchiveExtractionUnsupportedFeatureException::class.java) {
                    ArCpioExtractionEngine.extractCpio(
                        ByteArrayInputStream(legacy),
                        destination,
                        maxArchiveBytes = 1024 * 1024L
                    )
                }
                assertTrue(destination.listFiles().orEmpty().isEmpty())
            } finally {
                destination.deleteRecursively()
            }
        }
    }

    @Test fun rejectsCpioNameSizeBeforeTheParserCanAllocateIt() {
        val malicious = ByteArray(110)
        "070701".toByteArray().copyInto(malicious, 0)
        "FFFFFFFF".toByteArray().copyInto(malicious, 94)
        val destination = tempDir()
        try {
            assertThrows(ArchiveExtractionLimitException::class.java) {
                ArCpioExtractionEngine.extractCpio(
                    ByteArrayInputStream(malicious),
                    destination,
                    maxArchiveBytes = 1024 * 1024L
                )
            }
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test fun rejectsOversizedArGnuLongNameTableBeforeTheParserCanAllocateIt() {
        val header = ByteArray(60) { 0x20 }
        "//".toByteArray().copyInto(header, 0)
        "1048577".toByteArray().copyInto(header, 48)
        byteArrayOf(0x60, 0x0a).copyInto(header, 58)
        val archive = "!<arch>\\n".toByteArray() + header
        val destination = tempDir()
        try {
            assertThrows(ArchiveExtractionLimitException::class.java) {
                ArCpioExtractionEngine.extractAr(
                    ByteArrayInputStream(archive),
                    destination,
                    maxArchiveBytes = 2 * 1024 * 1024L
                )
            }
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test fun cpioChecksumFailureRollsBackEarlierFiles() {
        val destination = tempDir()
        val damaged = cpioArchive(CpioConstants.FORMAT_NEW_CRC,
            CpioFixture("first.txt", CpioConstants.C_ISREG, "first".toByteArray(), 1),
            CpioFixture("damaged.txt", CpioConstants.C_ISREG, "checksum-payload".toByteArray(), 1))
        val marker = "checksum-payload".toByteArray()
        val offset = damaged.indices.firstOrNull { index ->
            index + marker.size <= damaged.size &&
                marker.indices.all { damaged[index + it] == marker[it] }
        } ?: error("Fixture payload was not found")
        damaged[offset] = (damaged[offset].toInt() xor 0x01).toByte()
        try {
            assertThrows(Exception::class.java) {
                ArCpioExtractionEngine.extractCpio(
                    ByteArrayInputStream(damaged),
                    destination,
                    maxArchiveBytes = 1024 * 1024L
                )
            }
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test fun cpioRejectsSymbolicLinksAndHardLinks() {
        listOf(
            CpioFixture("link", CpioConstants.C_ISLNK, "target".toByteArray(), 1),
            CpioFixture("hard-link", CpioConstants.C_ISREG, "content".toByteArray(), 2)
        ).forEach { fixture ->
            val destination = tempDir()
            try {
                assertThrows(ArchiveExtractionUnsupportedFeatureException::class.java) {
                    ArCpioExtractionEngine.extractCpio(
                        ByteArrayInputStream(cpioArchive(CpioConstants.FORMAT_NEW, fixture)),
                        destination,
                        maxArchiveBytes = 1024 * 1024L
                    )
                }
                assertTrue(destination.listFiles().orEmpty().isEmpty())
            } finally {
                destination.deleteRecursively()
            }
        }
    }

    @Test fun cpioTraversalAndMalformedPathRollBackEarlierEntries() {
        val destination = tempDir()
        val existing = File(destination, "keep.txt").apply { writeText("safe") }
        try {
            assertThrows(UnsafeArchivePathException::class.java) {
                ArCpioExtractionEngine.extractCpio(
                    ByteArrayInputStream(cpioArchive(CpioConstants.FORMAT_NEW,
                        CpioFixture("safe/first.txt", CpioConstants.C_ISREG, "remove".toByteArray(), 1),
                        CpioFixture("../outside.txt", CpioConstants.C_ISREG, "blocked".toByteArray(), 1))),
                    destination,
                    maxArchiveBytes = 1024 * 1024L
                )
            }
            assertFalse(File(destination, "safe").exists())
            assertEquals("safe", existing.readText())
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test fun cpioEnforcesOutputLimitsAndRollsBack() {
        val destination = tempDir()
        try {
            assertThrows(ArchiveExtractionLimitException::class.java) {
                ArCpioExtractionEngine.extractCpio(
                    ByteArrayInputStream(cpioArchive(CpioConstants.FORMAT_NEW,
                        CpioFixture("large.bin", CpioConstants.C_ISREG, ByteArray(128), 1))),
                    destination,
                    maxArchiveBytes = 1024 * 1024L,
                    policy = ExtractionPolicy(maxSingleFileBytes = 64)
                )
            }
            assertTrue(destination.listFiles().orEmpty().isEmpty())
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test fun arAndCpioKeepCallerOwnedStreamsOpen() {
        val destination = tempDir()
        var arClosed = false
        var cpioClosed = false
        val arStream = TrackingInputStream(arArchive("file.txt" to "ar".toByteArray())) { arClosed = true }
        val cpioStream = TrackingInputStream(
            cpioArchive(CpioConstants.FORMAT_NEW, CpioFixture("file.txt", CpioConstants.C_ISREG, "cpio".toByteArray(), 1))
        ) { cpioClosed = true }
        try {
            ArCpioExtractionEngine.extractAr(arStream, destination, 1024 * 1024L)
            assertFalse(arClosed)
            File(destination, "file.txt").delete()
            ArCpioExtractionEngine.extractCpio(cpioStream, destination, 1024 * 1024L)
            assertFalse(cpioClosed)
        } finally {
            arStream.close()
            cpioStream.close()
            destination.deleteRecursively()
        }
    }

    private data class CpioFixture(
        val name: String,
        val mode: Int,
        val payload: ByteArray,
        val links: Long
    )

    private fun arArchive(vararg files: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ArArchiveOutputStream(output).use { archive ->
            files.forEach { (name, payload) ->
                archive.putArchiveEntry(ArArchiveEntry(name, payload.size.toLong()))
                archive.write(payload)
                archive.closeArchiveEntry()
            }
            archive.finish()
        }
        return output.toByteArray()
    }

    private fun cpioArchive(format: Short, vararg entries: CpioFixture): ByteArray {
        val output = ByteArrayOutputStream()
        CpioArchiveOutputStream(output, format).use { archive ->
            entries.forEachIndexed { index, fixture ->
                val entry = CpioArchiveEntry(format, fixture.name, fixture.payload.size.toLong())
                entry.setMode(fixture.mode.toLong())
                entry.setNumberOfLinks(fixture.links)
                entry.setInode((index + 1).toLong())
                if (format == CpioConstants.FORMAT_NEW_CRC) {
                    entry.setChksum(fixture.payload.fold(0L) { sum, byte -> (sum + (byte.toInt() and 0xff)) and 0xffffffffL })
                }
                archive.putArchiveEntry(entry)
                archive.write(fixture.payload)
                archive.closeArchiveEntry()
            }
            archive.finish()
        }
        return output.toByteArray()
    }

    private class TrackingInputStream(bytes: ByteArray, private val onClose: () -> Unit) :
        FilterInputStream(ByteArrayInputStream(bytes)) {
        override fun close() {
            onClose()
            super.close()
        }
    }

    private fun tempDir() = File(
        System.getProperty("java.io.tmpdir"), "arcnova-ar-cpio-${System.nanoTime()}"
    ).apply { check(mkdirs()) }
}
