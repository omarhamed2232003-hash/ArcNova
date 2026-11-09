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
        require(maxHeaderBytes > 0) { "maxHeaderBytes must be positive" }
        val extension = displayName.substringAfterLast('.', "").lowercase()
            .takeIf { it.isNotBlank() && it != displayName.lowercase() }
        val digest = MessageDigest.getInstance("SHA-256")
        val header = ByteArray(maxHeaderBytes)
        var headerSize = 0
        var total = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            digest.update(buffer, 0, count)
            val toCopy = minOf(count, maxHeaderBytes - headerSize)
            if (toCopy > 0) {
                buffer.copyInto(header, headerSize, 0, toCopy)
                headerSize += toCopy
            }
            total += count.toLong()
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
            sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        )
    }

    private fun extensionsEquivalent(left: String, right: String): Boolean {
        val aliases = mapOf("jpeg" to "jpg", "tif" to "tiff", "htm" to "html")
        val normalizedLeft = aliases[left] ?: left
        val normalizedRight = aliases[right] ?: right
        return normalizedLeft == normalizedRight
    }
}
