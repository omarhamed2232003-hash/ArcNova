package com.arcnova.app.engine

import java.io.File
import java.io.IOException

internal object ExtractionStagingCleanup {
    fun cleanup(
        stagingDirectory: File,
        primaryFailure: Exception?,
        deleteRecursively: (File) -> Boolean = { it.deleteRecursively() }
    ): IOException? {
        val cleanupFailure = try {
            if (!stagingDirectory.exists() || deleteRecursively(stagingDirectory) || !stagingDirectory.exists()) {
                return null
            }
            IOException("Could not remove temporary extraction data from app cache")
        } catch (error: Exception) {
            IOException("Could not remove temporary extraction data from app cache", error)
        }

        primaryFailure?.addSuppressed(cleanupFailure)
        return cleanupFailure
    }
}