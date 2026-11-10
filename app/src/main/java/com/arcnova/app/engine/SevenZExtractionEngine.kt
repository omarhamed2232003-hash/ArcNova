package com.arcnova.app.engine

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.CRC32
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod

/**
 * Bounded 7z extraction backed by Apache Commons Compress and XZ for Java.
 *
 * This backend intentionally supports only the method/filter chain implemented by
 * Commons Compress 1.28.0. It does not claim full 7-Zip method compatibility.
 */
object SevenZExtractionEngine {
    private const val BUFFER_SIZE = 64 * 1024
    private const val DEFAULT_MAX_DECODER_MEMORY_KIB = 256 * 1024
    private const val WINDOWS_FILE_ATTRIBUTE_REPARSE_POINT = 0x400

    private val supportedMethods = setOf(
        SevenZMethod.COPY,
        SevenZMethod.LZMA,
        SevenZMethod.LZMA2,
        SevenZMethod.BZIP2,
        SevenZMethod.DEFLATE,
        SevenZMethod.DEFLATE64,
        SevenZMethod.BCJ_X86_FILTER,
        SevenZMethod.BCJ_PPC_FILTER,
        SevenZMethod.BCJ_IA64_FILTER,
        SevenZMethod.BCJ_ARM_FILTER,
        SevenZMethod.BCJ_ARM_THUMB_FILTER,
        SevenZMethod.BCJ_SPARC_FILTER,
        SevenZMethod.DELTA_FILTER
    )

    fun extract(
        input: InputStream,
        cacheDirectory: File,
        destination: File,
        maxArchiveBytes: Long,
        policy: ExtractionPolicy = ExtractionPolicy(),
        maxDecoderMemoryKiB: Int = DEFAULT_MAX_DECODER_MEMORY_KIB,
        cancellationRequested: () -> Boolean = { false },
        onProgress: (ArchiveExtractionProgress) -> Unit = {}
    ): ArchiveExtractionResult {
        require(maxArchiveBytes > 0L) { "maxArchiveBytes must be positive" }
        require(maxDecoderMemoryKiB > 0) { "maxDecoderMemoryKiB must be positive" }

        val spool = try {
            SeekableArchiveSpool.create(
                input = input,
                cacheDirectory = cacheDirectory,
                maxArchiveBytes = maxArchiveBytes,
                cancellationRequested = cancellationRequested
            )
        } catch (error: ArchiveSpoolCancelledException) {
            throw ArchiveExtractionCancelledException(error.copiedBytes)
        } catch (error: ArchiveSpoolLimitException) {
            throw ArchiveExtractionLimitException(error.message ?: "7z input exceeds configured limit", error.copiedBytes)
        }

        val destinationExisted = destination.exists()
        var root: File? = null
        val createdFiles = mutableListOf<File>()
        val createdDirectories = mutableListOf<File>()
        var entriesProcessed = 0
        var filesExtracted = 0
        var directoriesCreated = 0
        var totalBytes = 0L

        try {
            return spool.use { seekable ->
                val archive = SevenZFile.builder()
                    .setFile(seekable.file)
                    .setMaxMemoryLimitKiB(maxDecoderMemoryKiB)
                    .setUseDefaultNameForUnnamedEntries(false)
                    .get()

                archive.use { sevenZ ->
                    val metadata = sevenZ.entries.toList()
                    val planned = preflight(metadata, policy)
                    checkCancelled(cancellationRequested, totalBytes)

                    if (!destination.exists() && !destination.mkdirs()) {
                        throw ArchiveExtractionException("Cannot create destination directory", totalBytes)
                    }
                    if (!destination.isDirectory) {
                        throw ArchiveExtractionException("Destination must be a directory", totalBytes)
                    }
                    root = destination.canonicalFile

                    val buffer = ByteArray(BUFFER_SIZE)
                    for ((index, plan) in planned.withIndex()) {
                        checkCancelled(cancellationRequested, totalBytes)
                        val entry = sevenZ.nextEntry
                            ?: throw ArchiveExtractionIntegrityException("7z entry count changed during extraction", totalBytes)
                        entriesProcessed++

                        if (entry.name != plan.archiveName) {
                            throw ArchiveExtractionIntegrityException(
                                "7z entry order or name changed during extraction",
                                totalBytes
                            )
                        }

                        if (entry.isAntiItem) {
                            throw ArchiveExtractionUnsupportedFeatureException(
                                "7z anti-items are not supported: ${plan.archiveName}",
                                totalBytes
                            )
                        }
                        if (entry.hasWindowsAttributes &&
                            (entry.windowsAttributes and WINDOWS_FILE_ATTRIBUTE_REPARSE_POINT) != 0
                        ) {
                            throw ArchiveExtractionUnsupportedFeatureException(
                                "7z reparse-point entries are not supported: ${plan.archiveName}",
                                totalBytes
                            )
                        }

                        val methods = entry.contentMethods.map { it.method }
                        val unsupported = methods.firstOrNull { it !in supportedMethods }
                        if (unsupported != null) {
                            throw ArchiveExtractionUnsupportedFeatureException(
                                "Unsupported 7z method: $unsupported",
                                totalBytes
                            )
                        }
                        if (SevenZMethod.AES256SHA256 in methods) {
                            throw ArchiveExtractionUnsupportedFeatureException(
                                "Encrypted 7z entries are not supported by this build",
                                totalBytes
                            )
                        }

                        val output = File(root, plan.relativePath).canonicalFile
                        ArchiveExtractionFileSystem.ensureContained(output, root!!, plan.archiveName, totalBytes)

                        if (plan.isDirectory) {
                            ArchiveExtractionFileSystem.ensureDirectory(
                                output,
                                root!!,
                                createdDirectories,
                                totalBytes
                            )
                            directoriesCreated++
                            onProgress(
                                ArchiveExtractionProgress(
                                    entriesProcessed,
                                    filesExtracted,
                                    directoriesCreated,
                                    totalBytes,
                                    plan.relativePath
                                )
                            )
                            continue
                        }

                        val parent = output.parentFile
                            ?: throw UnsafeArchivePathException(plan.archiveName)
                        ArchiveExtractionFileSystem.ensureDirectory(
                            parent,
                            root!!,
                            createdDirectories,
                            totalBytes
                        )
                        if (output.exists() || !output.createNewFile()) {
                            throw ArchiveExtractionException("Refusing to overwrite: ${plan.relativePath}", totalBytes)
                        }
                        createdFiles.add(output)

                        var entryBytes = 0L
                        val crc = CRC32()
                        val statistics = sevenZ.statisticsForCurrentEntry
                        FileOutputStream(output, false).use { out ->
                            while (true) {
                                checkCancelled(cancellationRequested, totalBytes)
                                val count = sevenZ.read(buffer)
                                if (count < 0) break
                                if (count == 0) continue

                                entryBytes = safeAdd(entryBytes, count.toLong(), totalBytes)
                                totalBytes = safeAdd(totalBytes, count.toLong(), totalBytes)
                                if (entryBytes > plan.sizeBytes ||
                                    entryBytes > policy.maxSingleFileBytes ||
                                    totalBytes > policy.maxTotalOutputBytes
                                ) {
                                    throw ArchiveExtractionLimitException("Actual 7z output exceeds configured limits", totalBytes)
                                }

                                enforceExpansionRatio(
                                    statistics.uncompressedCount,
                                    statistics.compressedCount,
                                    policy.maxExpansionRatio,
                                    totalBytes
                                )
                                crc.update(buffer, 0, count)
                                out.write(buffer, 0, count)
                            }
                        }

                        if (entryBytes != plan.sizeBytes) {
                            throw ArchiveExtractionIntegrityException(
                                "7z entry size mismatch for ${plan.archiveName}: expected ${plan.sizeBytes}, got $entryBytes",
                                totalBytes
                            )
                        }
                        if (entry.hasCrc && crc.value != entry.crcValue) {
                            throw ArchiveExtractionIntegrityException(
                                "7z CRC mismatch for ${plan.archiveName}",
                                totalBytes
                            )
                        }

                        filesExtracted++
                        onProgress(
                            ArchiveExtractionProgress(
                                entriesProcessed,
                                filesExtracted,
                                directoriesCreated,
                                totalBytes,
                                plan.relativePath
                            )
                        )
                    }

                    if (sevenZ.nextEntry != null) {
                        throw ArchiveExtractionIntegrityException("7z archive contains unplanned entries", totalBytes)
                    }
                    ArchiveExtractionResult(
                        entriesProcessed,
                        filesExtracted,
                        directoriesCreated,
                        totalBytes,
                        root!!
                    )
                }
            }
        } catch (error: Exception) {
            ArchiveExtractionFileSystem.rollback(
                createdFiles,
                createdDirectories,
                if (destinationExisted) null else root,
                error
            )
            throw error
        }
    }

    private data class PlannedEntry(
        val archiveName: String,
        val relativePath: String,
        val isDirectory: Boolean,
        val sizeBytes: Long
    )

    private fun preflight(
        entries: List<org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry>,
        policy: ExtractionPolicy
    ): List<PlannedEntry> {
        if (entries.size > policy.maxEntries) {
            throw ArchiveExtractionLimitException("7z entry count exceeds ${policy.maxEntries}", 0L)
        }

        val seenPaths = java.util.TreeMap<String, Boolean>()
        var declaredTotal = 0L
        val planned = ArrayList<PlannedEntry>(entries.size)

        for (entry in entries) {
            val name = entry.name ?: throw ArchiveExtractionUnsupportedFeatureException(
                "7z entries without names are not supported",
                0L
            )
            if (entry.isAntiItem) {
                throw ArchiveExtractionUnsupportedFeatureException("7z anti-items are not supported: $name", 0L)
            }
            if (entry.hasWindowsAttributes &&
                (entry.windowsAttributes and WINDOWS_FILE_ATTRIBUTE_REPARSE_POINT) != 0
            ) {
                throw ArchiveExtractionUnsupportedFeatureException("7z reparse-point entries are not supported: $name", 0L)
            }

            val relative = ArchiveExtractionFileSystem.sanitizePath(name, policy)
            val normalized = relative.replace(File.separatorChar, '/')
            val isDirectory = entry.isDirectory
            if (seenPaths.containsKey(normalized)) {
                throw ArchiveExtractionException("Duplicate normalized 7z path: $normalized", 0L)
            }
            val segments = normalized.split('/')
            for (index in 1 until segments.size) {
                val ancestor = segments.take(index).joinToString("/")
                if (seenPaths[ancestor] == false) {
                    throw ArchiveExtractionException("7z file path is also used as a parent: $ancestor", 0L)
                }
            }
            if (!isDirectory && seenPaths.ceilingKey("$normalized/")?.startsWith("$normalized/") == true) {
                throw ArchiveExtractionException("7z file path conflicts with an existing child: $normalized", 0L)
            }
            seenPaths[normalized] = isDirectory

            if (entry.size < 0L) {
                throw ArchiveExtractionIntegrityException("7z entry has a negative size: $name", 0L)
            }
            if (isDirectory && entry.size != 0L) {
                throw ArchiveExtractionIntegrityException("7z directory has a non-zero size: $name", 0L)
            }
            if (!isDirectory) {
                if (entry.size > policy.maxSingleFileBytes) {
                    throw ArchiveExtractionLimitException("7z entry exceeds per-file limit: $name", 0L)
                }
                if (entry.size > policy.maxTotalOutputBytes - declaredTotal) {
                    throw ArchiveExtractionLimitException("Declared 7z output exceeds total limit", 0L)
                }
                declaredTotal = safeAdd(declaredTotal, entry.size, declaredTotal)
            }
            planned.add(PlannedEntry(name, relative, isDirectory, entry.size))
        }
        return planned
    }

    private fun enforceExpansionRatio(
        outputBytes: Long,
        compressedBytes: Long,
        maximum: Double,
        totalBytes: Long
    ) {
        if (outputBytes > 0L && compressedBytes > 0L &&
            outputBytes.toDouble() / compressedBytes.toDouble() > maximum
        ) {
            throw ArchiveExtractionLimitException("7z expansion ratio exceeds configured limit", totalBytes)
        }
    }

    private fun safeAdd(current: Long, increment: Long, bytesWritten: Long): Long {
        if (increment > Long.MAX_VALUE - current) {
            throw ArchiveExtractionLimitException("7z output byte counter overflow", bytesWritten)
        }
        return current + increment
    }

    private fun checkCancelled(cancel: () -> Boolean, bytesWritten: Long) {
        if (cancel()) throw ArchiveExtractionCancelledException(bytesWritten)
    }
}
