package com.arcnova.app.engine

import java.io.InputStream
import java.security.MessageDigest

/**
 * Streams a file once, retaining only a bounded header while calculating SHA-256.
 * A signature match is identification evidence, not full-file validation or safety proof.
 */
data class FileAnalysisReport(
    val displayName: String,
    val extension: String?,
    val signature: FileSignatureMatch?,
    val extensionMatchesSignature: Boolean?,
    val sizeBytes: Long,
    val sha256: String
)

object FileAnalysisAnalyzer {
    fun analyze(
        displayName: String,
        input: InputStream,
        maxHeaderBytes: Int = FileSignatureDetector.DEFAULT_MAX_HEADER_BYTES
    ): FileAnalysisReport {
        require(maxHeaderBytes in 1..FileSignatureDetector.DEFAULT_MAX_HEADER_BYTES) {
            "maxHeaderBytes must be between 1 and ${FileSignatureDetector.DEFAULT_MAX_HEADER_BYTES}"
        }
        val extension = displayName.substringAfterLast('/').substringAfterLast('\\')
            .let { name ->
                val dot = name.lastIndexOf('.')
                if (dot > 0 && dot < name.lastIndex) name.substring(dot + 1).lowercase() else null
            }
        val digest = MessageDigest.getInstance("SHA-256")
        val header = ByteArray(maxHeaderBytes)
        var headerSize = 0
        var total = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) {
                // InputStream implementations should not return zero for a non-empty
                // buffer, but a defensive single-byte read prevents a busy loop.
                val single = input.read()
                if (single < 0) break
                digest.update(single.toByte())
                if (headerSize < maxHeaderBytes) header[headerSize++] = single.toByte()
                total = Math.addExact(total, 1L)
                continue
            }

            digest.update(buffer, 0, count)
            val toCopy = minOf(count, maxHeaderBytes - headerSize)
            if (toCopy > 0) {
                buffer.copyInto(header, headerSize, 0, toCopy)
                headerSize += toCopy
            }
            total = Math.addExact(total, count.toLong())
        }

        val signature = FileSignatureDetector.detect(header.copyOf(headerSize))
        val matches = signature?.extension?.let { detected ->
            extension?.let { supplied -> extensionsEquivalent(supplied, detected) }
        }
        return FileAnalysisReport(
            displayName = displayName,
            extension = extension,
            signature = signature,
            extensionMatchesSignature = matches,
            sizeBytes = total,
            sha256 = digest.digest().toLowerCaseHex()
        )
    }

    /** Encodes digest bytes without allocating a formatted String for every byte. */
    private fun ByteArray.toLowerCaseHex(): String {
        val alphabet = "0123456789abcdef"
        val output = CharArray(size * 2)
        for (index in indices) {
            val value = this[index].toInt() and 0xff
            output[index * 2] = alphabet[value ushr 4]
            output[index * 2 + 1] = alphabet[value and 0x0f]
        }
        return String(output)
    }

    private fun extensionsEquivalent(left: String, right: String): Boolean {
        val aliases = mapOf("jpeg" to "jpg", "tif" to "tiff", "htm" to "html")
        val normalizedLeft = aliases[left] ?: left
        val normalizedRight = aliases[right] ?: right
        return normalizedLeft == normalizedRight
    }
}
