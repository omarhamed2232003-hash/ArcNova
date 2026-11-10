package com.arcnova.app

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.arcnova.app.engine.ArchiveFormat
import com.arcnova.app.engine.ArchiveRiskAssessment
import com.arcnova.app.engine.ArchiveFormatDetector
import com.arcnova.app.engine.ArchiveSignatureDetector
import com.arcnova.app.engine.ArchiveSource
import com.arcnova.app.engine.ExtractionStagingCleanup
import com.arcnova.app.engine.ExtractionPolicy
import com.arcnova.app.engine.ZipExtractionEngine
import com.arcnova.app.engine.ZipInspectionEngine
import com.arcnova.app.engine.ZipExtractionProgress
import com.arcnova.app.engine.ArchiveExtractionProgress
import com.arcnova.app.engine.TarGzipExtractionEngine
import com.arcnova.app.engine.SevenZExtractionEngine
import com.arcnova.app.engine.ArchiveExtractionCancelledException
import com.arcnova.app.engine.ZipExtractionCancelledException
import com.arcnova.app.engine.TreeDocumentStore
import com.arcnova.app.engine.TreeCopyCancelledException
import com.arcnova.app.engine.TransactionalTreeCopy
import com.arcnova.app.engine.TreeCopyResult
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    companion object {
        private const val MAX_ARCHIVE_INPUT_BYTES = 2L * 1024L * 1024L * 1024L
    }

    private lateinit var fileLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var chooseFolderButton: Button
    private lateinit var extractButton: Button
    private lateinit var inspectButton: Button
    private lateinit var cancelButton: Button
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val cancelRequested = AtomicBoolean(false)
    private val openArchiveDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(::handlePickedArchive) }
    private val openDestinationTree = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let(::handlePickedDestination) }
    private var selectedArchive: ArchiveSource? = null
    private var selectedFormat: ArchiveFormat? = null
    private var destinationTree: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 48, 28, 24)
        }
        root.addView(TextView(this).apply {
            text = "ArcNova"; textSize = 32f; gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Universal file & software toolkit • Android"; textSize = 16f
            gravity = Gravity.CENTER; setPadding(0, 8, 0, 28)
        })
        root.addView(Button(this).apply {
            text = "Choose archive"; setOnClickListener { pickFile() }
        })
        fileLabel = TextView(this).apply {
            text = "No file selected"; textSize = 14f; setPadding(0, 18, 0, 12)
        }
        root.addView(fileLabel)
        chooseFolderButton = Button(this).apply {
            text = "Choose extraction folder"
            isEnabled = false
            setOnClickListener { pickDestination() }
        }
        root.addView(chooseFolderButton)
        extractButton = Button(this).apply {
            text = "Extract ZIP"; isEnabled = false
            setOnClickListener { startExtraction() }
        }
        root.addView(extractButton)
        inspectButton = Button(this).apply {
            text = "Inspect ZIP contents"
            isEnabled = false
            setOnClickListener { startInspection() }
        }
        root.addView(inspectButton)
        cancelButton = Button(this).apply {
            text = "Cancel"; isEnabled = false
            setOnClickListener { cancelRequested.set(true); statusLabel.text = "Cancelling…" }
        }
        root.addView(cancelButton)
        statusLabel = TextView(this).apply {
            text = "Ready"; textSize = 14f; setPadding(0, 18, 0, 0)
        }
        root.addView(statusLabel)
        root.addView(TextView(this).apply {
            text = "ZIP and TAR extraction plus GZIP decompression are available in this engineering build. ZIP inspection remains ZIP-only."
            textSize = 12f; setPadding(0, 24, 0, 0)
        })
        setContentView(root)
    }

    private fun pickFile() {
        openArchiveDocument.launch(arrayOf("*/*"))
    }

    private fun pickDestination() {
        openDestinationTree.launch(null)
    }

    private fun handlePickedArchive(uri: Uri) {
        // OpenDocument is the lifecycle-aware AndroidX contract for ACTION_OPEN_DOCUMENT.
        // Persist the grant when the provider supports it; the active URI remains usable either way.
        runCatching {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        selectedArchive = null
        selectedFormat = null
        extractButton.isEnabled = false
        inspectButton.isEnabled = false
        chooseFolderButton.isEnabled = false
        fileLabel.text = "Checking selected file…"
        statusLabel.text = "Opening file provider…"
        worker.execute {
            try {
                val source = readArchiveSource(uri)
                    ?: throw IllegalStateException("The selected provider did not return readable file information")
                contentResolver.openInputStream(uri)?.use { input ->
                    if (input.read() < 0) throw IllegalStateException("The selected file is empty or unreadable")
                } ?: throw IllegalStateException("The selected provider could not open this file")
                val signature = readPrefix(uri)?.let(ArchiveSignatureDetector::detect)
                val extension = ArchiveFormatDetector.detect(source.displayName)
                val supportedFormat = extension.supportedExtractionFormat(signature)
                val supported = supportedFormat != null
                val inspectionSupported = supportedFormat == ArchiveFormat.ZIP
                mainHandler.post {
                    // Keep unsupported files visible, but never retain them as an actionable archive.
                    selectedArchive = source.takeIf { supported }
                    selectedFormat = supportedFormat
                    fileLabel.text = buildString {
                        append("Selected: "); append(source.displayName ?: "Unknown")
                        append("\nSize: "); append(source.declaredSizeBytes?.let(::formatBytes) ?: "Unknown")
                        append("\nDetected extension: "); append(extension.format.label)
                        append("\nHeader: "); append(signature?.label ?: "Unavailable")
                        append("\nExtraction: "); append(supportedFormat?.label ?: "Not supported yet")
                    }
                    extractButton.text = supportedFormat?.let { "Extract ${it.label}" } ?: "Extract archive"
                    extractButton.isEnabled = supported
                    inspectButton.isEnabled = inspectionSupported
                    chooseFolderButton.isEnabled = supported
                    statusLabel.text = when {
                        supported -> if (inspectionSupported) {
                            "ZIP extraction and inspection are available. Choose a destination folder."
                        } else {
                            "Extraction is available. Choose a destination folder to continue."
                        }
                        extension.isMultipart && extension.format == ArchiveFormat.ZIP ->
                            "Multipart ZIP extraction is not supported yet."
                        extension.format != ArchiveFormat.UNKNOWN &&
                            signature != null && signature != ArchiveFormat.UNKNOWN &&
                            extension.format != signature ->
                            "File extension and header do not match. Extraction is disabled."
                        else -> "File is readable, but its format is not supported yet."
                    }
                }
            } catch (error: Exception) {
                mainHandler.post {
                    selectedArchive = null
                    fileLabel.text = "Could not read selected file"
                    statusLabel.text = "File access failed: ${error.message ?: error.javaClass.simpleName}"
                }
            }
        }
    }

    private fun handlePickedDestination(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        try {
            DocumentsContract.getTreeDocumentId(uri)
            destinationTree = uri
            statusLabel.text = "Destination folder selected."
            extractButton.isEnabled = selectedArchive != null
        } catch (error: Exception) {
            destinationTree = null
            statusLabel.text = "Could not access selected folder: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    private fun startInspection() {
        val source = selectedArchive ?: return
        if (selectedFormat != ArchiveFormat.ZIP) return
        setBusy(true)
        cancelRequested.set(false)
        statusLabel.text = "Inspecting ZIP contents…"
        worker.execute {
            try {
                val input = contentResolver.openInputStream(source.uri)
                    ?: throw IllegalStateException("Cannot open selected archive")
                val report = input.use {
                    ZipInspectionEngine.inspectCentralDirectory(
                        input = it,
                        cacheDirectory = cacheDir,
                        maxArchiveBytes = MAX_ARCHIVE_INPUT_BYTES,
                        cancellationRequested = { cancelRequested.get() }
                    )
                }
                val risk = ArchiveRiskAssessment.assess(report)
                val preview = report.entries.take(40).joinToString("\n") { entry ->
                    val marker = when {
                        entry.hasUnsafePath -> "⚠ "
                        entry.isDuplicateName -> "↻ "
                        entry.isDirectory -> "📁 "
                        else -> "📄 "
                    }
                    "$marker${entry.name}  •  ${formatBytes(entry.actualSizeBytes)}"
                }
                mainHandler.post {
                    statusLabel.text = "Inspection complete: ${report.fileCount} files, ${report.directoryCount} folders"
                    val summary = buildString {
                        append("Files: ${report.fileCount}\nFolders: ${report.directoryCount}\n")
                        append("Uncompressed: ${formatBytes(report.totalUncompressedBytes)}\n")
                        append("Unsafe paths: ${report.unsafePathCount}\nDuplicate names: ${report.duplicateNameCount}\n")
                        append("Risk findings: ${risk.findings.size} • Affected entries: ${risk.totalAffectedEntries}\n")
                        if (risk.findings.isNotEmpty()) {
                            risk.findings.forEach { finding ->
                                append("• [${finding.severity}] ${finding.id}: ${finding.affectedEntries}\n")
                            }
                        } else {
                            append("No heuristic risk indicators found. This is not a malware scan.\n")
                        }
                        append("\n")
                        append(preview.ifBlank { "No entries found." })
                        if (report.entries.size > 40) append("\n… and ${report.entries.size - 40} more")
                    }
                    AlertDialog.Builder(this).setTitle("ZIP contents")
                        .setMessage(summary).setPositiveButton("Close", null).show()
                    setBusy(false)
                }
            } catch (error: Exception) {
                mainHandler.post {
                    statusLabel.text = when (error) {
                        is com.arcnova.app.engine.ZipInspectionCancelledException -> "Inspection cancelled."
                        is com.arcnova.app.engine.ZipInspectionUnsafePathException ->
                            "Inspection rejected an unsafe ZIP path. No risk report was produced."
                        is com.arcnova.app.engine.ZipInspectionDuplicatePathException ->
                            "Inspection rejected duplicate ZIP paths. No risk report was produced."
                        is com.arcnova.app.engine.ZipInspectionUnsupportedFeatureException ->
                            "This ZIP uses encryption or a compression method ArcNova cannot read. No files were written."
                        else -> "Inspection failed: ${error.message ?: error.javaClass.simpleName}"
                    }
                    setBusy(false)
                }
            }
        }
    }

    private fun startExtraction() {
        val source = selectedArchive ?: return
        val format = selectedFormat ?: return
        val tree = destinationTree ?: run {
            statusLabel.text = "Choose an extraction folder first."; return
        }
        setBusy(true)
        cancelRequested.set(false)
        statusLabel.text = "Preparing ${format.label} extraction…"
        worker.execute {
            val staging = File(cacheDir, "arcnova-extract-${System.currentTimeMillis()}")
            var operationFailure: Exception? = null
            try {
                val input = contentResolver.openInputStream(source.uri)
                    ?: throw IllegalStateException("Cannot open selected archive")
                val result = input.use { stream ->
                    when (format) {
                        ArchiveFormat.ZIP -> {
                            val zip = ZipExtractionEngine.extractCentralDirectory(
                                input = stream,
                                destination = staging,
                                cacheDirectory = cacheDir,
                                maxArchiveBytes = MAX_ARCHIVE_INPUT_BYTES,
                                policy = ExtractionPolicy(),
                                cancellationRequested = { cancelRequested.get() },
                                onProgress = { progress -> postProgress(progress) }
                            )
                            zip.filesExtracted to zip.bytesWritten
                        }
                        ArchiveFormat.SEVEN_ZIP -> {
                            val sevenZip = SevenZExtractionEngine.extract(
                                input = stream,
                                cacheDirectory = cacheDir,
                                destination = staging,
                                maxArchiveBytes = MAX_ARCHIVE_INPUT_BYTES,
                                policy = ExtractionPolicy(),
                                cancellationRequested = { cancelRequested.get() },
                                onProgress = { progress -> postProgress(progress) }
                            )
                            sevenZip.filesExtracted to sevenZip.bytesWritten
                        }
                        ArchiveFormat.TAR -> {
                            val tar = TarGzipExtractionEngine.extractTar(
                                input = stream,
                                destination = staging,
                                maxArchiveBytes = MAX_ARCHIVE_INPUT_BYTES,
                                policy = ExtractionPolicy(),
                                cancellationRequested = { cancelRequested.get() },
                                onProgress = { progress -> postProgress(progress) }
                            )
                            tar.filesExtracted to tar.bytesWritten
                        }
                        ArchiveFormat.GZIP -> {
                            val gzip = TarGzipExtractionEngine.decompressGzip(
                                input = stream,
                                destination = staging,
                                outputFileName = gzipOutputName(source.displayName),
                                maxInputBytes = MAX_ARCHIVE_INPUT_BYTES,
                                policy = ExtractionPolicy(),
                                cancellationRequested = { cancelRequested.get() },
                                onProgress = { progress -> postProgress(progress) }
                            )
                            gzip.filesExtracted to gzip.bytesWritten
                        }
                        else -> throw IllegalStateException("No extraction engine is registered for ${format.label}")
                    }
                }
                if (cancelRequested.get()) throw InterruptedException("Extraction cancelled")
                val copied = copyDirectoryToTree(staging, tree)
                mainHandler.post {
                    statusLabel.text = "Done: ${result.first} files, ${formatBytes(result.second)} extracted. Copied ${copied.filesCopied} files and ${copied.directoriesCreated} folders."
                    setBusy(false)
                }
            } catch (error: Exception) {
                operationFailure = error
                mainHandler.post {
                    statusLabel.text = when (error) {
                        is InterruptedException, is ZipExtractionCancelledException,
                        is ArchiveExtractionCancelledException, is TreeCopyCancelledException -> "Extraction cancelled."
                        is com.arcnova.app.engine.ZipExtractionUnsupportedFeatureException ->
                            "Cannot extract this ZIP: it is encrypted or uses an unsupported compression method. No files were committed."
                        else -> "Extraction failed: ${error.message ?: error.javaClass.simpleName}"
                    }
                    setBusy(false)
                }
            } finally {
                val cleanupFailure = ExtractionStagingCleanup.cleanup(staging, operationFailure)
                if (cleanupFailure != null) {
                    mainHandler.post {
                        statusLabel.text = if (operationFailure == null) {
                            "Extraction completed, but temporary staging data could not be removed from app cache."
                        } else {
                            "${statusLabel.text}\nCleanup warning: temporary staging data could not be removed from app cache."
                        }
                    }
                }
            }
        }
    }

    private fun postProgress(progress: ZipExtractionProgress) {
        mainHandler.post {
            statusLabel.text = "Extracting: ${progress.currentEntry}\nFiles: ${progress.filesExtracted} • ${formatBytes(progress.bytesWritten)}"
        }
    }

    private fun postProgress(progress: ArchiveExtractionProgress) {
        mainHandler.post {
            statusLabel.text = "Extracting: ${progress.currentEntry}\nFiles: ${progress.filesExtracted} • ${formatBytes(progress.bytesWritten)}"
        }
    }

    private fun gzipOutputName(displayName: String?): String {
        val name = displayName?.substringAfterLast('/')?.substringAfterLast('\\')?.trim().orEmpty()
        val withoutSuffix = when {
            name.endsWith(".gzip", ignoreCase = true) -> name.dropLast(5)
            name.endsWith(".gz", ignoreCase = true) -> name.dropLast(3)
            else -> name
        }
        return withoutSuffix.ifBlank { "decompressed.bin" }
    }

    private fun copyDirectoryToTree(source: File, treeUri: Uri): TreeCopyResult {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val store = object : TreeDocumentStore {
            override fun childNames(parentDocumentId: String): Set<String> {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
                val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val names = linkedSetOf<String>()
                val cursor = contentResolver.query(childrenUri, projection, null, null, null)
                    ?: throw IllegalStateException("Cannot list destination folder contents")
                cursor.use {
                    val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (nameIndex < 0) throw IllegalStateException("Destination provider did not return item names")
                    while (it.moveToNext()) {
                        if (!it.isNull(nameIndex)) names.add(it.getString(nameIndex))
                    }
                }
                return names
            }

            override fun createDirectory(parentDocumentId: String, name: String): String {
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocumentId)
                val created = DocumentsContract.createDocument(
                    contentResolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, name
                ) ?: throw IllegalStateException("Cannot create destination folder: $name")
                return DocumentsContract.getDocumentId(created)
            }

            override fun createFile(parentDocumentId: String, name: String, mimeType: String): String {
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocumentId)
                val created = DocumentsContract.createDocument(contentResolver, parentUri, mimeType, name)
                    ?: throw IllegalStateException("Cannot create destination file: $name")
                return DocumentsContract.getDocumentId(created)
            }

            override fun openOutput(documentId: String) =
                contentResolver.openOutputStream(
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId), "w"
                ) ?: throw IllegalStateException("Cannot open destination item for writing")

            override fun delete(documentId: String): Boolean =
                DocumentsContract.deleteDocument(
                    contentResolver, DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                )
        }

        return TransactionalTreeCopy.copy(
            source,
            rootId,
            store,
            cancellationRequested = { cancelRequested.get() },
            onProgress = { progress ->
                mainHandler.post {
                    statusLabel.text = "Copying: ${progress.currentPath}\nFiles: ${progress.filesCopied} • ${formatBytes(progress.bytesCopied)}"
                }
            }
        )
    }

    private fun setBusy(busy: Boolean) {
        extractButton.isEnabled = !busy && selectedArchive != null && destinationTree != null
        inspectButton.isEnabled = !busy && selectedArchive != null && selectedFormat == ArchiveFormat.ZIP
        chooseFolderButton.isEnabled = !busy && selectedArchive != null
        cancelButton.isEnabled = busy
    }

    private fun readPrefix(uri: Uri): ByteArray? = runCatching {
        contentResolver.openInputStream(uri)?.use { input ->
            val bytes = ByteArray(65536)
            var count = 0
            while (count < bytes.size) {
                val read = input.read(bytes, count, bytes.size - count)
                if (read < 0) break
                if (read > 0) count += read
            }
            bytes.copyOf(count)
        }
    }.getOrNull()

    private fun readArchiveSource(uri: Uri): ArchiveSource? = runCatching {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        var name: String? = null
        var size: Long? = null
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex).takeIf { it >= 0L }
            }
        }
        ArchiveSource(uri, name, size)
    }.getOrNull()

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = -1
        do { value /= 1024.0; index++ } while (value >= 1024.0 && index < units.lastIndex)
        return String.format(java.util.Locale.US, "%.2f %s", value, units[index])
    }

    override fun onDestroy() {
        cancelRequested.set(true)
        worker.shutdownNow()
        super.onDestroy()
    }
}
