package com.arcnova.app.engine

import java.io.InputStream
import java.io.OutputStream

/**
 * Contract for one independently versioned codec implementation.
 *
 * Implementations must document their license, provenance, supported
 * architectures, memory bounds, and conformance fixtures before registration.
 * This interface does not imply that any codec is currently available.
 */
interface CodecEngine {
    val id: String
    val displayName: String
    val version: String
    val supportsStreaming: Boolean

    /**
     * Decode one codec stream. The caller owns both streams.
     * Implementations must not close either stream.
     */
    fun decode(input: InputStream, output: OutputStream, limits: DecodeLimits): DecodeResult
}

data class DecodeLimits(
    val maxOutputBytes: Long,
    val maxExpansionRatio: Double = 10_000.0,
    val cancellationRequested: () -> Boolean = { false }
)

data class DecodeResult(
    val bytesWritten: Long,
    val checksumVerified: Boolean? = null
)
