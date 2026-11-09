package com.arcnova.app.engine

/** Lightweight PE/Windows executable signature check; does not execute or unpack the file. */
object WindowsExecutableDetector {
    enum class Result {
        NOT_EXECUTABLE,
        MZ_HEADER_ONLY,
        PE_EXECUTABLE,
        INVALID_PE_OFFSET
    }

    fun detect(prefix: ByteArray): Result {
        if (prefix.size < 2 || prefix[0] != 0x4d.toByte() || prefix[1] != 0x5a.toByte()) {
            return Result.NOT_EXECUTABLE
        }
        if (prefix.size < 0x40) return Result.MZ_HEADER_ONLY

        val peOffset = (prefix[0x3c].toInt() and 0xff) or
            ((prefix[0x3d].toInt() and 0xff) shl 8) or
            ((prefix[0x3e].toInt() and 0xff) shl 16) or
            ((prefix[0x3f].toInt() and 0xff) shl 24)

        if (peOffset < 0x40) return Result.INVALID_PE_OFFSET
        if (peOffset > prefix.size - 4) return Result.MZ_HEADER_ONLY
        val isPe = prefix[peOffset] == 0x50.toByte() &&
            prefix[peOffset + 1] == 0x45.toByte() &&
            prefix[peOffset + 2] == 0.toByte() &&
            prefix[peOffset + 3] == 0.toByte()
        return if (isPe) Result.PE_EXECUTABLE else Result.INVALID_PE_OFFSET
    }
}
