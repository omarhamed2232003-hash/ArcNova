package com.arcnova.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowsExecutableDetectorTest {
    @Test fun recognizesPeExecutable() {
        val bytes = ByteArray(128)
        bytes[0] = 0x4d
        bytes[1] = 0x5a
        bytes[0x3c] = 0x40
        bytes[0x40] = 0x50
        bytes[0x41] = 0x45
        assertEquals(WindowsExecutableDetector.Result.PE_EXECUTABLE, WindowsExecutableDetector.detect(bytes))
    }

    @Test fun distinguishesDosHeaderWithoutPeAndNonExecutable() {
        val mz = ByteArray(64)
        mz[0] = 0x4d
        mz[1] = 0x5a
        assertEquals(WindowsExecutableDetector.Result.MZ_HEADER_ONLY, WindowsExecutableDetector.detect(mz))
        assertEquals(WindowsExecutableDetector.Result.NOT_EXECUTABLE, WindowsExecutableDetector.detect(byteArrayOf(1, 2)))
    }

    @Test fun rejectsInvalidPeOffset() {
        val bytes = ByteArray(64)
        bytes[0] = 0x4d
        bytes[1] = 0x5a
        bytes[0x3c] = 0x20
        assertEquals(WindowsExecutableDetector.Result.INVALID_PE_OFFSET, WindowsExecutableDetector.detect(bytes))
    }
}
