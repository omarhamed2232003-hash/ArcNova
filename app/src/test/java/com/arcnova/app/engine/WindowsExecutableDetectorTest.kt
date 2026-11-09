package com.arcnova.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowsExecutableDetectorTest {
    private fun pe(machine: Int, magic: Int, subsystem: Int = 3, characteristics: Int = 0x0002): ByteArray {
        val bytes = ByteArray(512)
        fun put8(offset: Int, value: Int) {
            bytes[offset] = value.toByte()
        }
        put8(0, 0x4d)
        put8(1, 0x5a)
        put8(0x3c, 0x80)
        put8(0x80, 0x50)
        put8(0x81, 0x45)
        fun put16(offset: Int, value: Int) {
            put8(offset, value)
            put8(offset + 1, value ushr 8)
        }
        put16(0x84, machine)
        put16(0x94, 0x00f0)
        put16(0x96, characteristics)
        put16(0x98, magic)
        put16(0x98 + 68, subsystem)
        return bytes
    }

    @Test fun recognizesPeExecutable() {
        assertEquals(WindowsExecutableDetector.Result.PE_EXECUTABLE,
            WindowsExecutableDetector.detect(pe(0x8664, 0x20b)))
    }

    @Test fun identifiesX64Pe32PlusConsoleExecutable() {
        val info = WindowsExecutableDetector.inspect(pe(0x8664, 0x20b, 3))
        assertEquals(WindowsExecutableDetector.Architecture.X64, info.architecture)
        assertEquals(WindowsExecutableDetector.OptionalHeader.PE32_PLUS, info.optionalHeader)
        assertEquals(WindowsExecutableDetector.Subsystem.WINDOWS_CONSOLE, info.subsystem)
        assertFalse(info.isDll)
    }

    @Test fun identifiesX86GuiDll() {
        val info = WindowsExecutableDetector.inspect(pe(0x014c, 0x10b, 2, 0x2000))
        assertEquals(WindowsExecutableDetector.Architecture.X86, info.architecture)
        assertEquals(WindowsExecutableDetector.Subsystem.WINDOWS_GUI, info.subsystem)
        assertTrue(info.isDll)
    }

    @Test fun identifiesArm64() {
        assertEquals(WindowsExecutableDetector.Architecture.ARM64,
            WindowsExecutableDetector.inspect(pe(0xaa64, 0x20b)).architecture)
    }

    @Test fun distinguishesDosHeaderAndNonExecutable() {
        val mz = ByteArray(64)
        mz[0] = 0x4d.toByte()
        mz[1] = 0x5a.toByte()
        assertEquals(WindowsExecutableDetector.Result.MZ_HEADER_ONLY, WindowsExecutableDetector.detect(mz))
        assertEquals(WindowsExecutableDetector.Result.NOT_EXECUTABLE,
            WindowsExecutableDetector.detect(byteArrayOf(1, 2)))
    }

    @Test fun rejectsInvalidPeOffset() {
        val bytes = ByteArray(64)
        bytes[0] = 0x4d.toByte()
        bytes[1] = 0x5a.toByte()
        bytes[0x3c] = 0x20.toByte()
        assertEquals(WindowsExecutableDetector.Result.INVALID_PE_OFFSET,
            WindowsExecutableDetector.detect(bytes))
    }
}
