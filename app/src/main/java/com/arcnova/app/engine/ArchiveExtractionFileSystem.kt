package com.arcnova.app.engine

import java.io.File
import java.io.IOException

/** Shared filesystem safeguards for transactional archive extraction. */
internal object ArchiveExtractionFileSystem {
    fun sanitizePath(name: String, policy: ExtractionPolicy): String {
        if (name.isBlank() || name.indexOf(0.toChar()) >= 0) throw UnsafeArchivePathException(name)
        val normalized = name.replace('\\', '/')
        if (normalized.startsWith('/') || Regex("^[a-zA-Z]:").containsMatchIn(normalized)) {
            throw UnsafeArchivePathException(name)
        }
        val parts = normalized.split('/')
        val meaningful = parts.dropLastWhile { it.isEmpty() }
        if (meaningful.isEmpty() || meaningful.any { it.isEmpty() || it == "." || it == ".." }) {
            throw UnsafeArchivePathException(name)
        }
        if (normalized.length > policy.maxPathLength || meaningful.size > policy.maxPathDepth) {
            throw ArchiveExtractionLimitException("Archive path exceeds configured length or depth: $name", 0L)
        }
        return meaningful.joinToString(File.separator)
    }

    fun ensureContained(output: File, root: File, entryName: String, bytesWritten: Long) {
        if (output != root && !output.path.startsWith(root.path + File.separator)) {
            throw UnsafeArchivePathException(entryName)
        }
    }

    fun ensureDirectory(
        directory: File,
        root: File,
        createdDirectories: MutableList<File>,
        bytesWritten: Long
    ) {
        if (directory == root) return
        ensureContained(directory, root, directory.path, bytesWritten)
        if (directory.exists()) {
            if (!directory.isDirectory) {
                throw ArchiveExtractionException("Path component is not a directory: $directory", bytesWritten)
            }
            return
        }
        val parent = directory.parentFile
            ?: throw ArchiveExtractionException("Cannot resolve parent directory: $directory", bytesWritten)
        ensureDirectory(parent, root, createdDirectories, bytesWritten)
        if (directory.mkdir()) createdDirectories.add(directory)
        else if (!directory.isDirectory) {
            throw ArchiveExtractionException("Cannot create directory: $directory", bytesWritten)
        }
    }

    fun rollback(
        files: List<File>,
        directories: List<File>,
        createdRoot: File?,
        originalError: Exception?
    ) {
        files.asReversed().forEach { deleteForRollback(it, originalError) }
        directories.asReversed().forEach { deleteForRollback(it, originalError) }
        if (createdRoot != null) deleteForRollback(createdRoot, originalError)
    }

    private fun deleteForRollback(file: File, originalError: Exception?) {
        try {
            if (file.exists() && !file.delete()) throw IOException("Rollback could not remove created path: $file")
        } catch (cleanupError: Exception) {
            if (originalError != null) originalError.addSuppressed(cleanupError) else throw cleanupError
        }
    }
}
