package com.arcnova.app.engine

/** Reads basic Portable Executable (PE) metadata from a bounded file prefix. */
object WindowsExecutableDetector {
    enum class Result { NOT_EXECUTABLE, MZ_HEADER_ONLY, PE_EXECUTABLE, INVALID_PE_OFFSET }
    enum class Architecture { X86, X64, ARM32, ARM64, UNKNOWN }
    enum class OptionalHeader { PE32, PE32_PLUS, UNKNOWN }
    enum class Subsystem { NATIVE, WINDOWS_GUI, WINDOWS_CONSOLE, EFI, UNKNOWN }

    data class Inspection(
        val result: Result,
        val architecture: Architecture = Architecture.UNKNOWN,
        val optionalHeader: OptionalHeader = OptionalHeader.UNKNOWN,
        val subsystem: Subsystem = Subsystem.UNKNOWN,
        val isDll: Boolean = false
    )

    fun detect(prefix: ByteArray): Result = inspect(prefix).result

    fun inspect(prefix: ByteArray): Inspection {
        if (prefix.size < 2 || u8(prefix, 0) != 0x4d || u8(prefix, 1) != 0x5a) {
            return Inspection(Result.NOT_EXECUTABLE)
        }
        if (prefix.size < 0x40) return Inspection(Result.MZ_HEADER_ONLY)

        val peOffset = u32(prefix, 0x3c) ?: return Inspection(Result.MZ_HEADER_ONLY)
        if (peOffset == 0L) return Inspection(Result.MZ_HEADER_ONLY)
        if (peOffset < 0x40L) return Inspection(Result.INVALID_PE_OFFSET)
        if (peOffset > prefix.size - 24L) return Inspection(Result.MZ_HEADER_ONLY)
        val p = peOffset.toInt()
        if (u8(prefix, p) != 0x50 || u8(prefix, p + 1) != 0x45 ||
            u8(prefix, p + 2) != 0 || u8(prefix, p + 3) != 0) {
            return Inspection(Result.INVALID_PE_OFFSET)
        }

        val machine = u16(prefix, p + 4) ?: return Inspection(Result.MZ_HEADER_ONLY)
        val optionalSize = u16(prefix, p + 20) ?: 0
        val characteristics = u16(prefix, p + 22) ?: 0
        val optionalStart = p + 24
        val magic = if (optionalSize >= 2) u16(prefix, optionalStart) else null
        val optional = when (magic) {
            0x10b -> OptionalHeader.PE32
            0x20b -> OptionalHeader.PE32_PLUS
            else -> OptionalHeader.UNKNOWN
        }
        val subsystemValue = if (optionalSize >= 70 && optionalStart + 70 <= prefix.size) {
            u16(prefix, optionalStart + 68)
        } else null
        val subsystem = when (subsystemValue) {
            1 -> Subsystem.NATIVE
            2 -> Subsystem.WINDOWS_GUI
            3 -> Subsystem.WINDOWS_CONSOLE
            10, 11, 12, 13, 14 -> Subsystem.EFI
            else -> Subsystem.UNKNOWN
        }
        val architecture = when (machine) {
            0x014c -> Architecture.X86
            0x8664 -> Architecture.X64
            0x01c0, 0x01c4 -> Architecture.ARM32
            0xaa64 -> Architecture.ARM64
            else -> Architecture.UNKNOWN
        }
        return Inspection(
            result = Result.PE_EXECUTABLE,
            architecture = architecture,
            optionalHeader = optional,
            subsystem = subsystem,
            isDll = characteristics and 0x2000 != 0
        )
    }

    private fun u8(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 0xff
    private fun u16(bytes: ByteArray, offset: Int): Int? {
        if (offset < 0 || offset + 2 > bytes.size) return null
        return u8(bytes, offset) or (u8(bytes, offset + 1) shl 8)
    }
    private fun u32(bytes: ByteArray, offset: Int): Long? {
        if (offset < 0 || offset + 4 > bytes.size) return null
        return u8(bytes, offset).toLong() or
            (u8(bytes, offset + 1).toLong() shl 8) or
            (u8(bytes, offset + 2).toLong() shl 16) or
            (u8(bytes, offset + 3).toLong() shl 24)
    }
}
