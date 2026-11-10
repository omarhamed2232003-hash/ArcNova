package com.arcnova.app.engine

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionalTreeCopyTest {
    private data class Node(
        val id: String,
        val name: String,
        val parentId: String?,
        val directory: Boolean,
        var bytes: ByteArray = byteArrayOf()
    )

    private class FakeTreeStore : TreeDocumentStore {
        val nodes = linkedMapOf("root" to Node("root", "", null, true))
        var failCreateName: String? = null
        var failOpenName: String? = null
        var failWriteName: String? = null
        var failCloseName: String? = null
        var failDeleteName: String? = null
        var throwDeleteName: String? = null
        private var nextId = 0

        override fun childNames(parentDocumentId: String): Set<String> =
            nodes.values.filter { it.parentId == parentDocumentId }.map { it.name }.toSet()

        override fun createDirectory(parentDocumentId: String, name: String): String =
            create(parentDocumentId, name, true)

        override fun createFile(parentDocumentId: String, name: String, mimeType: String): String =
            create(parentDocumentId, name, false)

        private fun create(parentId: String, name: String, directory: Boolean): String {
            if (name == failCreateName) throw IllegalStateException("simulated provider create failure")
            val id = "doc-${++nextId}"
            nodes[id] = Node(id, name, parentId, directory)
            return id
        }

        override fun openOutput(documentId: String): OutputStream {
            val node = nodes.getValue(documentId)
            if (node.name == failOpenName) throw IllegalStateException("simulated provider output failure")
            return object : ByteArrayOutputStream() {
                override fun write(value: Int) {
                    if (node.name == failWriteName && size() >= 3) {
                        throw java.io.IOException("simulated partial provider write failure")
                    }
                    super.write(value)
                }

                override fun write(buffer: ByteArray, offset: Int, length: Int) {
                    if (node.name == failWriteName && size() + length > 3) {
                        val allowed = (3 - size()).coerceAtLeast(0)
                        if (allowed > 0) super.write(buffer, offset, allowed)
                        throw java.io.IOException("simulated partial provider write failure")
                    }
                    super.write(buffer, offset, length)
                }

                override fun close() {
                    super.close()
                    node.bytes = toByteArray()
                    if (node.name == failCloseName) {
                        throw java.io.IOException("simulated provider close failure")
                    }
                }
            }
        }

        override fun delete(documentId: String): Boolean {
            val node = nodes[documentId] ?: return false
            if (node.name == throwDeleteName) throw java.io.IOException("simulated provider delete exception")
            if (node.name == failDeleteName) return false
            if (nodes.values.any { it.parentId == documentId }) return false
            return nodes.remove(documentId) != null
        }

        fun addExisting(name: String, bytes: ByteArray = byteArrayOf()): String {
            val id = "existing-$name"
            nodes[id] = Node(id, name, "root", false, bytes)
            return id
        }

        fun paths(parentId: String = "root", prefix: String = ""): List<String> =
            nodes.values.filter { it.parentId == parentId }.flatMap { node ->
                val path = if (prefix.isEmpty()) node.name else "$prefix/${node.name}"
                listOf(path) + if (node.directory) paths(node.id, path) else emptyList()
            }
    }

    private fun tempDir(): File = createTempDir(prefix = "arcnova-tree-copy-test-")

    @Test fun copiesNestedFilesAndReportsTotals() {
        val source = tempDir()
        val store = FakeTreeStore()
        try {
            File(source, "folder").mkdirs()
            File(source, "folder/a.txt").writeText("ArcNova")
            File(source, "b.bin").writeBytes(byteArrayOf(1, 2, 3))

            val result = TransactionalTreeCopy.copy(source, "root", store)

            assertEquals(2, result.filesCopied)
            assertEquals(1, result.directoriesCreated)
            assertEquals(10L, result.bytesCopied)
            assertEquals(setOf("folder", "folder/a.txt", "b.bin"), store.paths().toSet())
            val file = store.nodes.values.single { it.name == "a.txt" }
            assertEquals("ArcNova", file.bytes.toString(Charsets.UTF_8))
        } finally {
            source.deleteRecursively()
        }
    }

    @Test fun rollsBackPreviouslyCreatedItemsWhenLaterCreateFails() {
        val source = tempDir()
        val store = FakeTreeStore()
        store.addExisting("keep.txt", "important".toByteArray())
        store.failCreateName = "z-fail.txt"
        try {
            File(source, "folder").mkdirs()
            File(source, "folder/a.txt").writeText("temporary")
            File(source, "z-fail.txt").writeText("trigger")

            org.junit.Assert.assertThrows(IllegalStateException::class.java) {
                TransactionalTreeCopy.copy(source, "root", store)
            }

            assertEquals(listOf("keep.txt"), store.paths())
            assertEquals("important", store.nodes.values.single { it.name == "keep.txt" }.bytes.toString(Charsets.UTF_8))
        } finally {
            source.deleteRecursively()
        }
    }

    @Test fun preservesExistingDestinationItemOnNameCollision() {
        val source = tempDir()
        val store = FakeTreeStore()
        store.addExisting("same.txt", "do not replace".toByteArray())
        try {
            File(source, "a-new.txt").writeText("temporary")
            File(source, "same.txt").writeText("replacement")

            org.junit.Assert.assertThrows(TreeCopyException::class.java) {
                TransactionalTreeCopy.copy(source, "root", store)
            }

            assertEquals(listOf("same.txt"), store.paths())
            assertEquals("do not replace", store.nodes.values.single { it.name == "same.txt" }.bytes.toString(Charsets.UTF_8))
        } finally {
            source.deleteRecursively()
        }
    }

    @Test fun cancellationAfterACompletedFileRollsBackAllCreatedItems() {
        val source = tempDir()
        val store = FakeTreeStore()
        var cancelled = false
        try {
            File(source, "a.txt").writeText("first")
            File(source, "b.txt").writeText("second")

            org.junit.Assert.assertThrows(TreeCopyCancelledException::class.java) {
                TransactionalTreeCopy.copy(
                    source, "root", store,
                    cancellationRequested = { cancelled },
                    onProgress = { cancelled = true }
                )
            }

            assertTrue(store.paths().isEmpty())
        } finally {
            source.deleteRecursively()
        }
    }

    @Test fun outputOpenFailureRollsBackTheCreatedDocument() {
        val source = tempDir()
        val store = FakeTreeStore()
        store.failOpenName = "cannot-write.txt"
        try {
            File(source, "cannot-write.txt").writeText("payload")

            org.junit.Assert.assertThrows(IllegalStateException::class.java) {
                TransactionalTreeCopy.copy(source, "root", store)
            }

            assertTrue(store.paths().isEmpty())
        } finally {
            source.deleteRecursively()
        }
    }

    @Test fun partialOutputWriteFailureRollsBackDocumentAndPreservesExistingItems() {
        val source = tempDir()
        val store = FakeTreeStore()
        val existingId = store.addExisting("keep.txt", "important".toByteArray())
        store.failWriteName = "partial.txt"
        try {
            File(source, "partial.txt").writeText("payload-that-fails-after-three-bytes")

            org.junit.Assert.assertThrows(java.io.IOException::class.java) {
                TransactionalTreeCopy.copy(source, "root", store)
            }

            assertEquals(listOf("keep.txt"), store.paths())
            assertEquals(
                "important",
                store.nodes.getValue(existingId).bytes.toString(Charsets.UTF_8)
            )
        } finally {
            source.deleteRecursively()
        }
    }

    @Test fun outputCloseFailureRollsBackCreatedDocumentAndPreservesExistingItems() {
        val source = tempDir()
        val store = FakeTreeStore()
        val existingId = store.addExisting("keep.txt", "important".toByteArray())
        store.failCloseName = "close-fails.txt"
        try {
            File(source, "close-fails.txt").writeText("payload")
            org.junit.Assert.assertThrows(java.io.IOException::class.java) {
                TransactionalTreeCopy.copy(source, "root", store)
            }
            assertEquals(listOf("keep.txt"), store.paths())
            assertEquals(
                "important",
                store.nodes.getValue(existingId).bytes.toString(Charsets.UTF_8)
            )
        } finally {
            source.deleteRecursively()
        }
    }

    @Test fun reportsWhenProviderRefusesRollbackDeletion() {
        val source = tempDir()
        val store = FakeTreeStore()
        store.failDeleteName = "created.txt"
        try {
            File(source, "created.txt").writeText("payload")
            File(source, "z-fail.txt").writeText("trigger")
            store.failCreateName = "z-fail.txt"

            val error = org.junit.Assert.assertThrows(TreeCopyRollbackException::class.java) {
                TransactionalTreeCopy.copy(source, "root", store)
            }

            assertTrue(error.message.orEmpty().contains("rollback was incomplete"))
            assertTrue(error.suppressed.isNotEmpty())
            assertTrue(store.paths().contains("created.txt"))
        } finally {
            source.deleteRecursively()
        }
    }

    @Test fun reportsDeleteExceptionAndContinuesRollingBackOtherCreatedDocuments() {
        val source = tempDir()
        val store = FakeTreeStore()
        val existingId = store.addExisting("keep.txt", "important".toByteArray())
        store.throwDeleteName = "a-created.txt"
        store.failCreateName = "z-fail.txt"
        try {
            File(source, "a-created.txt").writeText("cannot delete")
            File(source, "b-removed.txt").writeText("should roll back")
            File(source, "z-fail.txt").writeText("trigger create failure")

            val error = org.junit.Assert.assertThrows(TreeCopyRollbackException::class.java) {
                TransactionalTreeCopy.copy(source, "root", store)
            }

            assertTrue(error.message.orEmpty().contains("1 destination item(s) could not be removed"))
            assertTrue(error.cause is IllegalStateException)
            assertEquals(1, error.suppressed.size)
            assertTrue(error.suppressed.single() is java.io.IOException)
            assertEquals("simulated provider delete exception", error.suppressed.single().message)
            assertEquals(setOf("keep.txt", "a-created.txt"), store.paths().toSet())
            assertEquals(
                "important",
                store.nodes.getValue(existingId).bytes.toString(Charsets.UTF_8)
            )
        } finally {
            source.deleteRecursively()
        }
    }

    @Test fun progressCallbackFailureRollsBackTheWholeCopy() {
        val source = tempDir()
        val store = FakeTreeStore()
        try {
            File(source, "nested").mkdirs()
            File(source, "nested/file.txt").writeText("payload")

            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                TransactionalTreeCopy.copy(
                    source, "root", store,
                    onProgress = { throw IllegalArgumentException("simulated observer failure") }
                )
            }

            assertTrue(store.paths().isEmpty())
        } finally {
            source.deleteRecursively()
        }
    }
}
