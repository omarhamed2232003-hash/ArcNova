package com.arcnova.app.engine

import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

/**
 * Minimal adapter over a document-tree destination (for example Android SAF).
 * IDs are opaque to the copier and are owned by the backing document provider.
 */
interface TreeDocumentStore {
    fun childNames(parentDocumentId: String): Set<String>
    fun createDirectory(parentDocumentId: String, name: String): String
    fun createFile(parentDocumentId: String, name: String, mimeType: String): String
    fun openOutput(documentId: String): OutputStream
    fun delete(documentId: String): Boolean
}

data class TreeCopyProgress(
    val filesCopied: Int,
    val directoriesCreated: Int,
    val bytesCopied: Long,
    val currentPath: String
)

data class TreeCopyResult(
    val filesCopied: Int,
    val directoriesCreated: Int,
    val bytesCopied: Long
)

/**
 * Copies a staged directory to a document tree and rolls back every document
 * created by this operation if any later step fails. Existing destination
 * documents are never modified or deleted.
 *
 * SAF providers do not expose a portable atomic directory-rename operation,
 * so this uses a tracked create/copy/delete transaction instead.
 */
object TransactionalTreeCopy {
    private const val BUFFER_SIZE = 64 * 1024
    private const val GENERIC_MIME_TYPE = "application/octet-stream"

    fun copy(
        sourceDirectory: File,
        destinationRootId: String,
        store: TreeDocumentStore,
        cancellationRequested: () -> Boolean = { false },
        onProgress: (TreeCopyProgress) -> Unit = {}
    ): TreeCopyResult {
        require(sourceDirectory.isDirectory) { "Source must be a directory" }
        val sourceRoot = sourceDirectory.canonicalFile
        val createdDocumentIds = mutableListOf<String>()
        val reservedNames = mutableMapOf<String, MutableSet<String>>()
        var filesCopied = 0
        var directoriesCreated = 0
        var bytesCopied = 0L

        fun namesIn(parentId: String): MutableSet<String> =
            reservedNames.getOrPut(parentId) { store.childNames(parentId).toMutableSet() }

        fun ensureAvailable(parentId: String, name: String) {
            validateName(name)
            if (!namesIn(parentId).add(name)) {
                throw TreeCopyException("Destination already contains: $name")
            }
        }

        fun copyChildren(source: File, parentId: String, relativePath: String) {
            checkCancelled(cancellationRequested)
            val children = source.listFiles()
                ?: throw TreeCopyException("Cannot list staged directory: $source")
            children.sortedBy { it.name }.forEach { child ->
                checkCancelled(cancellationRequested)
                val canonical = child.canonicalFile
                if (canonical != sourceRoot && !canonical.path.startsWith(sourceRoot.path + File.separator)) {
                    throw TreeCopyException("Staged path escapes source directory: $child")
                }
                val name = child.name
                validateName(name)
                val path = if (relativePath.isEmpty()) name else "$relativePath/$name"
                ensureAvailable(parentId, name)

                if (child.isDirectory) {
                    val directoryId = store.createDirectory(parentId, name)
                    createdDocumentIds.add(directoryId)
                    directoriesCreated++
                    onProgress(TreeCopyProgress(filesCopied, directoriesCreated, bytesCopied, path))
                    copyChildren(child, directoryId, path)
                } else if (child.isFile) {
                    val fileId = store.createFile(parentId, name, GENERIC_MIME_TYPE)
                    createdDocumentIds.add(fileId)
                    FileInputStream(child).use { input ->
                        store.openOutput(fileId).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                checkCancelled(cancellationRequested)
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (count == 0) continue
                                val nextBytes = safeAdd(bytesCopied, count.toLong())
                                output.write(buffer, 0, count)
                                bytesCopied = nextBytes
                            }
                        }
                    }
                    filesCopied++
                    onProgress(TreeCopyProgress(filesCopied, directoriesCreated, bytesCopied, path))
                } else {
                    throw TreeCopyException("Unsupported staged filesystem entry: $child")
                }
            }
        }

        try {
            copyChildren(sourceRoot, destinationRootId, "")
            checkCancelled(cancellationRequested)
            return TreeCopyResult(filesCopied, directoriesCreated, bytesCopied)
        } catch (error: Exception) {
            rollback(createdDocumentIds, store, error)
            throw error
        }
    }

    private fun validateName(name: String) {
        if (name.isBlank() || name == "." || name == ".." ||
            name.contains('/') || name.contains('\\\\') || name.indexOf(0.toChar()) >= 0
        ) {
            throw TreeCopyException("Invalid destination item name")
        }
    }

    private fun checkCancelled(cancel: () -> Boolean) {
        if (cancel()) throw TreeCopyCancelledException()
    }

    private fun safeAdd(left: Long, right: Long): Long {
        if (right > Long.MAX_VALUE - left) throw TreeCopyException("Copied byte counter overflow")
        return left + right
    }

    private fun rollback(createdIds: List<String>, store: TreeDocumentStore, originalError: Exception) {
        createdIds.asReversed().forEach { id ->
            try {
                if (!store.delete(id)) {
                    throw TreeCopyException("Destination provider could not roll back document: $id")
                }
            } catch (cleanupError: Exception) {
                originalError.addSuppressed(cleanupError)
            }
        }
    }
}

open class TreeCopyException(message: String) : Exception(message)
class TreeCopyCancelledException : TreeCopyException("Destination copy cancelled")
