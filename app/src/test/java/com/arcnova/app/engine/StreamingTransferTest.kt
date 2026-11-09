package com.arcnova.app.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StreamingTransferTest {
    @Test
    fun copiesInBoundedChunksAndReportsProgress() {
        val source = ByteArray(150_000) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<TransferProgress>()

        val result = StreamingTransfer.copy(
            ByteArrayInputStream(source),
            output,
            DecodeLimits(maxOutputBytes = 200_000, maxExpansionRatio = 1.0),
            bufferSize = 16 * 1024,
            onProgress = progress::add
        )

        assertEquals(source.size.toLong(), result.inputBytesRead)
        assertEquals(source.size.toLong(), result.outputBytesWritten)
        assertEquals(source.toList(), output.toByteArray().toList())
        assertEquals(10, progress.size)
    }

    @Test
    fun rejectsOutputBeyondConfiguredLimit() {
        val error = assertThrows(OutputLimitExceededException::class.java) {
            StreamingTransfer.copy(
                ByteArrayInputStream(ByteArray(32)),
                ByteArrayOutputStream(),
                DecodeLimits(maxOutputBytes = 16)
            )
        }
        assertEquals(16L, error.limitBytes)
    }

    @Test
    fun observesCancellationBeforeReading() {
        val error = assertThrows(TransferCancelledException::class.java) {
            StreamingTransfer.copy(
                ByteArrayInputStream(ByteArray(32)),
                ByteArrayOutputStream(),
                DecodeLimits(maxOutputBytes = 100, cancellationRequested = { true })
            )
        }
        assertEquals(0L, error.inputBytesRead)
    }

    @Test
    fun rejectsInvalidBufferSize() {
        assertThrows(IllegalArgumentException::class.java) {
            StreamingTransfer.copy(
                ByteArrayInputStream(byteArrayOf(1)),
                ByteArrayOutputStream(),
                DecodeLimits(maxOutputBytes = 10),
                bufferSize = 1024
            )
        }
    }
}
