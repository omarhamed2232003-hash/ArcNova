package com.arcnova.app.engine

/** Detects well-known container signatures from a small prefix of the file. */
object ArchiveSignatureDetector {
    fun detect(prefix: ByteArray): ArchiveFormat {
        fun starts(vararg bytes: Int): Boolean =
            prefix.size >= bytes.size && bytes.indices.all { (prefix[it].toInt() and 0xff) == bytes[it] }

        if (starts(0x50, 0x4b, 0x03, 0x04) || starts(0x50, 0x4b, 0x05, 0x06) ||
            starts(0x50, 0x4b, 0x07, 0x08)) return ArchiveFormat.ZIP
        if (starts(0x37, 0x7a, 0xbc, 0xaf, 0x27, 0x1c)) return ArchiveFormat.SEVEN_ZIP
        if (starts(0x21, 0x3c, 0x61, 0x72, 0x63, 0x68, 0x3e, 0x0a)) return ArchiveFormat.AR
        if (starts(0x30, 0x37, 0x30, 0x37, 0x30, 0x31) ||
            starts(0x30, 0x37, 0x30, 0x37, 0x30, 0x32) ||
            starts(0x30, 0x37, 0x30, 0x37, 0x30, 0x37) ||
            starts(0x71, 0xc7) || starts(0xc7, 0x71)) return ArchiveFormat.CPIO
        if (starts(0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x00) ||
            starts(0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x01, 0x00)) return ArchiveFormat.RAR
        if (starts(0x1f, 0x8b)) return ArchiveFormat.GZIP
        if (starts(0x42, 0x5a, 0x68)) return ArchiveFormat.BZIP2
        if (starts(0xfd, 0x37, 0x7a, 0x58, 0x5a, 0x00)) return ArchiveFormat.XZ
        if (starts(0x28, 0xb5, 0x2f, 0xfd)) return ArchiveFormat.ZSTANDARD
        if (prefix.size >= 262 && prefix.copyOfRange(257, 262).contentEquals("ustar".toByteArray(Charsets.US_ASCII))) {
            return ArchiveFormat.TAR
        }
        return ArchiveFormat.UNKNOWN
    }
}
