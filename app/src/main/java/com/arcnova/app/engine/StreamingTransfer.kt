package com.arcnova.app.engine

import java.io.InputStream
import java.io.OutputStream

/**
 * Copies a stream through a fixed-size buffer without taking ownership of either stream.
 *
 * This is the bounded-I/O primitive for ArcNova's future archive/codec pipeline.
 * It is not an archive extractor or decompressor by itself.
 */
object StreamingTransfer {
    const val DEFAULT_BUFFER_SIZE: Int = 64 * 1024
    private const val MIN_BUFFER_SIZE: Int = 4 * 1024
    private const val MAX_BUFFER_SIZE: Int = 1024 * 1024

    fun copy(
        input: InputStream,
        output: OutputStream,
        limits: DecodeLimits,
        bufferSize: Int = DEFAULT_BUFFER_SIZE,
        onProgress: (TransferProgress) -> Unit = {}
    ): TransferResult {
        require(limits.maxOutputBytes >= 0) { "maxOutputBytes must not be negative" }
        require(limits.maxExpansionRatio.isFinite() && limits.maxExpansionRatio > 0.0) {
            "maxExpansionRatio must be finite and greater than zero"
        }
        require(bufferSize in MIN_BUFFER_SIZE..MAX_BUFFER_SIZE) {
            "bufferSize must be between $MIN_BUFFER_SIZE and $MAX_BUFFER_SIZE"
        }

        val buffer = ByteArray(bufferSize)
        var inputBytes = 0L
        var outputBytes = 0L

        while (true) {
            if (limits.cancellationRequested()) {
                throw TransferCancelledException(inputBytes, outputBytes)
            }

            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue

            inputBytes = checkedAdd(inputBytes, count.toLong(), "input byte count")
            val nextOutputBytes = checkedAdd(outputBytes, count.toLong(), "output byte count")

            if (nextOutputBytes > limits.maxOutputBytes) {
                throw OutputLimitExceededException(limits.maxOutputBytes, nextOutputBytes)
            }

            val ratio = if (inputBytes == 0L) Double.POSITIVE_INFINITY
                else nextOutputBytes.toDouble() / inputBytes.toDouble()
            if (ratio > limits.maxExpansionRatio) {
                throw ExpansionRatioExceededException(limits.maxExpansionRatio, ratio)
            }

            output.write(buffer, 0, count)
            outputBytes = nextOutputBytes
            onProgress(TransferProgress(inputBytes, outputBytes))
        }

        output.flush()
        return TransferResult(inputBytes, outputBytes)
    }

    private fun checkedAdd(current: Long, increment: Long, label: String): Long {
        if (increment > Long.MAX_VALUE - current) {
            throw IllegalStateException("$label overflow")
        }
        return current + increment
    }
}

data class TransferProgress(
    val inputBytesRead: Long,
    val outputBytesWritten: Long
)

data class TransferResult(
    val inputBytesRead: Long,
    val outputBytesWritten: Long
)

class TransferCancelledException(
    val inputBytesRead: Long,
    val outputBytesWritten: Long
) : Exception("Transfer cancelled")

class OutputLimitExceededException(
    val limitBytes: Long,
    val attemptedBytes: Long
) : Exception("Output limit exceeded: $attemptedBytes > $limitBytes")

class ExpansionRatioExceededException(
    val limitRatio: Double,
    val actualRatio: Double
) : Exception("Expansion ratio exceeded: $actualRatio > $limitRatio")
