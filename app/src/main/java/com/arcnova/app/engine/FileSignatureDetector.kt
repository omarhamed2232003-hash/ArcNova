package com.arcnova.app.engine

import java.io.InputStream

enum class FileCategory {
    ARCHIVE, IMAGE, DOCUMENT, EXECUTABLE, AUDIO, VIDEO, DATABASE, UNKNOWN
}

data class FileSignatureMatch(
    val id: String,
    val extension: String?,
    val category: FileCategory,
    val description: String
)

/**
 * Bounded magic-byte detection for common file families.
 *
 * A match identifies a recognizable header, not full-file validity or safety.
 * InputStream overload reads only a bounded prefix and never closes the stream.
 */
object FileSignatureDetector {
    const val DEFAULT_MAX_HEADER_BYTES = 64 * 1024

    fun detect(input: InputStream, maxHeaderBytes: Int = DEFAULT_MAX_HEADER_BYTES): FileSignatureMatch? {
        require(maxHeaderBytes > 0) { "maxHeaderBytes must be positive" }
        val buffer = ByteArray(maxHeaderBytes)
        var count = 0
        while (count < buffer.size) {
            val read = input.read(buffer, count, buffer.size - count)
            if (read < 0) break
            if (read == 0) {
                val single = input.read()
                if (single < 0) break
                buffer[count++] = single.toByte()
            } else {
                count += read
            }
        }
        return detect(buffer.copyOf(count))
    }

    fun detect(bytes: ByteArray): FileSignatureMatch? {
        if (bytes.isEmpty()) return null
        fun starts(vararg signature: Int): Boolean =
            bytes.size >= signature.size && signature.indices.all { (bytes[it].toInt() and 0xff) == signature[it] }
        fun startsAt(offset: Int, vararg signature: Int): Boolean =
            offset >= 0 && bytes.size >= offset + signature.size &&
                signature.indices.all { (bytes[offset + it].toInt() and 0xff) == signature[it] }
        fun ascii(offset: Int, value: String): Boolean =
            bytes.size >= offset + value.length && value.indices.all { bytes[offset + it].toInt() == value[it].code }

        return when {
            starts(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) ->
                match("png", "png", FileCategory.IMAGE, "PNG image")
            starts(0xff, 0xd8, 0xff) ->
                match("jpeg", "jpg", FileCategory.IMAGE, "JPEG image")
            ascii(0, "GIF87a") || ascii(0, "GIF89a") ->
                match("gif", "gif", FileCategory.IMAGE, "GIF image")
            ascii(0, "RIFF") && ascii(8, "WEBP") ->
                match("webp", "webp", FileCategory.IMAGE, "WebP image")
            starts(0x42, 0x4d) ->
                match("bmp", "bmp", FileCategory.IMAGE, "BMP image")
            starts(0x49, 0x49, 0x2a, 0x00) || starts(0x4d, 0x4d, 0x00, 0x2a) ->
                match("tiff", "tif", FileCategory.IMAGE, "TIFF image")
            starts(0x25, 0x50, 0x44, 0x46, 0x2d) ->
                match("pdf", "pdf", FileCategory.DOCUMENT, "PDF document")
            starts(0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1) ->
                match("ole-compound", null, FileCategory.DOCUMENT, "OLE Compound File container")
            starts(0x4d, 0x5a) ->
                match("pe-candidate", "exe", FileCategory.EXECUTABLE, "Windows PE/DOS executable candidate")
            starts(0x7f, 0x45, 0x4c, 0x46) ->
                match("elf", null, FileCategory.EXECUTABLE, "ELF executable or shared object")
            starts(0x50, 0x4b, 0x03, 0x04) || starts(0x50, 0x4b, 0x05, 0x06) || starts(0x50, 0x4b, 0x07, 0x08) ->
                match("zip", "zip", FileCategory.ARCHIVE, "ZIP container")
            starts(0x37, 0x7a, 0xbc, 0xaf, 0x27, 0x1c) ->
                match("7z", "7z", FileCategory.ARCHIVE, "7-Zip archive")
            starts(0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x00) ||
                starts(0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x01, 0x00) ->
                match("rar", "rar", FileCategory.ARCHIVE, "RAR archive")
            starts(0x1f, 0x8b) ->
                match("gzip", "gz", FileCategory.ARCHIVE, "GZIP stream")
            ascii(0, "BZh") ->
                match("bzip2", "bz2", FileCategory.ARCHIVE, "BZIP2 stream")
            starts(0xfd, 0x37, 0x7a, 0x58, 0x5a, 0x00) ->
                match("xz", "xz", FileCategory.ARCHIVE, "XZ stream")
            starts(0x28, 0xb5, 0x2f, 0xfd) ->
                match("zstandard", "zst", FileCategory.ARCHIVE, "Zstandard frame")
            ascii(0, "MSCF") ->
                match("cab", "cab", FileCategory.ARCHIVE, "Microsoft Cabinet archive")
            starts(0x34, 0x12, 0xaa, 0x55) ->
                match("source-vpk", "vpk", FileCategory.ARCHIVE, "Valve package (VPK) container")
            starts(0x58, 0x50, 0x33, 0x0d, 0x0a, 0x20, 0x0a, 0x1a, 0x8b, 0x67, 0x01) ->
                match("xp3", "xp3", FileCategory.ARCHIVE, "KiriKiri XP3 archive")
            ascii(0, "UnityWebData1.0") ->
                match("unity-web-data", null, FileCategory.ARCHIVE, "Unity Web data container")
            (ascii(0, "UnityFS") && bytes.getOrNull(7) == 0.toByte()) ->
                match("unityfs", "bundle", FileCategory.ARCHIVE, "UnityFS asset bundle")
            (ascii(0, "UnityWeb") && bytes.getOrNull(8) == 0.toByte()) ->
                match("unityweb", "unity3d", FileCategory.ARCHIVE, "Legacy UnityWeb asset bundle")
            (ascii(0, "UnityRaw") && bytes.getOrNull(8) == 0.toByte()) ->
                match("unityraw", "unity3d", FileCategory.ARCHIVE, "Legacy UnityRaw asset bundle")
            (ascii(0, "UnityArchive") && bytes.getOrNull(12) == 0.toByte()) ->
                match("unityarchive", "unity3d", FileCategory.ARCHIVE, "Legacy UnityArchive container")
            ascii(0, "RPA-3.0 ") || ascii(0, "RPA-2.0 ") ->
                match("renpy-rpa", "rpa", FileCategory.ARCHIVE, "Ren'Py RPA archive")
            ascii(257, "ustar") ->
                match("tar-ustar", "tar", FileCategory.ARCHIVE, "POSIX USTAR archive")
            (bytes.size >= 0x8007 &&
                ascii(0x8001, "CD001") &&
                (bytes[0x8000].toInt() and 0xff) in setOf(1, 2, 3, 255) &&
                (bytes[0x8006].toInt() and 0xff) == 1) ->
                match("iso9660", "iso", FileCategory.ARCHIVE, "ISO 9660 optical disc image")
            ascii(0, "SQLite format 3\u0000") ->
                match("sqlite", "sqlite", FileCategory.DATABASE, "SQLite database")
            ascii(0, "ID3") || (bytes.size >= 2 &&
                (bytes[0].toInt() and 0xff) == 0xff &&
                (bytes[1].toInt() and 0xe0) == 0xe0) ->
                match("mp3", "mp3", FileCategory.AUDIO, "MP3 audio")
            ascii(0, "fLaC") ->
                match("flac", "flac", FileCategory.AUDIO, "FLAC audio")
            ascii(0, "OggS") ->
                match("ogg", "ogg", FileCategory.AUDIO, "Ogg container")
            ascii(0, "RIFF") && ascii(8, "WAVE") ->
                match("wav", "wav", FileCategory.AUDIO, "WAVE audio")
            ascii(4, "ftyp") ->
                match("mp4", "mp4", FileCategory.VIDEO, "ISO base media / MP4 container")
            starts(0x1a, 0x45, 0xdf, 0xa3) ->
                match("matroska", "mkv", FileCategory.VIDEO, "Matroska/WebM EBML container")
            else -> null
        }
    }

    private fun match(id: String, extension: String?, category: FileCategory, description: String) =
        FileSignatureMatch(id, extension, category, description)
}
