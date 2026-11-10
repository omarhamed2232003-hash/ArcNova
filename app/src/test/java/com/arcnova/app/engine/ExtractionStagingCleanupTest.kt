package com.arcnova.app.engine

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractionStagingCleanupTest {
    private fun tempDir(): File = createTempDir(prefix = "arcnova-staging-cleanup-test-")

    @Test fun successfulCleanupRemovesStagingDirectory() {
        val staging = tempDir()
        File(staging, "payload.txt").writeText("temporary")

        val error = ExtractionStagingCleanup.cleanup(staging, primaryFailure = null)

        assertNull(error)
        assertFalse(staging.exists())
    }

    @Test fun failedCleanupReturnsAnErrorInsteadOfIgnoringDeleteFailure() {
        val staging = tempDir()
        File(staging, "payload.txt").writeText("temporary")
        try {
            val error = ExtractionStagingCleanup.cleanup(
                staging,
                primaryFailure = null,
                deleteRecursively = { false }
            )

            assertNotNull(error)
            assertEquals("Could not remove temporary extraction data from app cache", error?.message)
            assertTrue(staging.exists())
        } finally {
            staging.deleteRecursively()
        }
    }

    @Test fun wrapsThrownDeleteExceptionWhenThereIsNoPrimaryFailure() {
        val staging = tempDir()
        File(staging, "payload.txt").writeText("temporary")
        val deleteFailure = SecurityException("simulated delete permission failure")
        try {
            val cleanupFailure = ExtractionStagingCleanup.cleanup(
                staging,
                primaryFailure = null,
                deleteRecursively = { throw deleteFailure }
            )

            assertNotNull(cleanupFailure)
            assertEquals(IOException::class.java, cleanupFailure?.javaClass)
            assertEquals("Could not remove temporary extraction data from app cache", cleanupFailure?.message)
            assertSame(deleteFailure, cleanupFailure?.cause)
            assertTrue(staging.exists())
        } finally {
            staging.deleteRecursively()
        }
    }

    @Test fun treatsMissingStagingDirectoryAsAlreadyCleaned() {
        val parent = tempDir()
        val staging = File(parent, "missing-staging")
        try {
            assertFalse(staging.exists())

            val error = ExtractionStagingCleanup.cleanup(staging, primaryFailure = null)

            assertNull(error)
            assertFalse(staging.exists())
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test fun cleanupFailureIsSuppressedOnThePrimaryOperationFailure() {
        val staging = tempDir()
        File(staging, "payload.txt").writeText("temporary")
        val primaryFailure = IllegalStateException("destination copy failed")
        try {
            val cleanupFailure = ExtractionStagingCleanup.cleanup(
                staging,
                primaryFailure = primaryFailure,
                deleteRecursively = { false }
            )

            assertNotNull(cleanupFailure)
            assertEquals("destination copy failed", primaryFailure.message)
            assertEquals(1, primaryFailure.suppressed.size)
            assertSame(cleanupFailure, primaryFailure.suppressed.single())
            assertTrue(staging.exists())
        } finally {
            staging.deleteRecursively()
        }
    }

    @Test fun wrapsThrownDeleteExceptionAndSuppressesItOnPrimaryFailure() {
        val staging = tempDir()
        File(staging, "payload.txt").writeText("temporary")
        val primaryFailure = IllegalStateException("destination copy failed")
        val deleteFailure = SecurityException("simulated delete permission failure")
        try {
            val cleanupFailure = ExtractionStagingCleanup.cleanup(
                staging,
                primaryFailure = primaryFailure,
                deleteRecursively = { throw deleteFailure }
            )

            assertNotNull(cleanupFailure)
            assertEquals("Could not remove temporary extraction data from app cache", cleanupFailure?.message)
            assertSame(deleteFailure, cleanupFailure?.cause)
            assertEquals("destination copy failed", primaryFailure.message)
            assertEquals(1, primaryFailure.suppressed.size)
            assertSame(cleanupFailure, primaryFailure.suppressed.single())
            assertTrue(staging.exists())
        } finally {
            staging.deleteRecursively()
        }
    }
}