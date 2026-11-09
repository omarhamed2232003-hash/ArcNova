package com.arcnova.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowsInstallerDetectorTest {
    private fun peWithMarker(marker: String): ByteArray {
        val bytes = ByteArray(512)
        bytes[0] = 0x4d; bytes[1] = 0x5a; bytes[0x3c] = 0x40
        bytes[0x40] = 0x50; bytes[0x41] = 0x45
        bytes[0x44] = 0x64.toByte(); bytes[0x45] = 0x86.toByte()
        bytes[0x54] = 0xf0.toByte()
        bytes[0x58] = 0x0b; bytes[0x59] = 0x02
        val markerBytes = marker.toByteArray(Charsets.ISO_8859_1)
        markerBytes.copyInto(bytes, 128)
        return bytes
    }

    @Test fun recognizesMsiCompoundFileSignature() {
        val bytes = byteArrayOf(0xd0.toByte(), 0xcf.toByte(), 0x11, 0xe0.toByte(), 0xa1.toByte(), 0xb1.toByte(), 0x1a, 0xe1.toByte())
        assertEquals(WindowsInstallerDetector.Kind.MSI_PACKAGE, WindowsInstallerDetector.detect("setup.msi", bytes))
    }

    @Test fun recognizesKnownExeInstallerMarkers() {
        assertEquals(WindowsInstallerDetector.Kind.INNO_SETUP, WindowsInstallerDetector.detect("setup.exe", peWithMarker("Inno Setup Setup Data")))
        assertEquals(WindowsInstallerDetector.Kind.NSIS, WindowsInstallerDetector.detect("setup.exe", peWithMarker("NullsoftInst")))
        assertEquals(WindowsInstallerDetector.Kind.WIX_BUNDLE, WindowsInstallerDetector.detect("bundle.exe", peWithMarker("WixBundle")))
    }

    @Test fun keepsUnrecognizedPeAsUnknownExecutable() {
        assertEquals(WindowsInstallerDetector.Kind.PE_EXECUTABLE_UNKNOWN, WindowsInstallerDetector.detect("tool.exe", peWithMarker("ordinary program")))
    }

    @Test fun reportsMissingHeaderAsUnknown() {
        assertEquals(WindowsInstallerDetector.Kind.UNKNOWN, WindowsInstallerDetector.detect("setup.exe", null))
    }
}
