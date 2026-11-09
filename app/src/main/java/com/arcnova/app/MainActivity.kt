package com.arcnova.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
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
import com.arcnova.app.engine.ArchiveFormat
import com.arcnova.app.engine.ArchiveRiskAssessment
import com.arcnova.app.engine.ArchiveFormatDetector
import com.arcnova.app.engine.ArchiveSignatureDetector
import com.arcnova.app.engine.ArchiveSource
import com.arcnova.app.engine.ExtractionPolicy
import com.arcnova.app.engine.ZipExtractionEngine
import com.arcnova.app.engine.ZipInspectionEngine
import com.arcnova.app.engine.ZipExtractionProgress
import com.arcnova.app.engine.ZipExtractionCancelledException
import com.arcnova.app.engine.TreeDocumentStore
import com.arcnova.app.engine.TreeCopyCancelledException
import com.arcnova.app.engine.TransactionalTreeCopy
import com.arcnova.app.engine.TreeCopyResult
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
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
    private val requestPick = 1001
    private val requestFolder = 1002
    private var selectedArchive: ArchiveSource? = null
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
            text = "ZIP extraction is available. Other formats are detected for future engine support."
            textSize = 12f; setPadding(0, 24, 0, 0)
        })
        setContentView(root)
    }

    private fun pickFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, requestPick)
    }

    private fun pickDestination() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, requestFolder)
    }

    @Deprecated("Platform document picker retained for broad compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        if (requestCode == requestPick) {
            retainPermission(uri, data.flags, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            selectedArchive = null
            extractButton.isEnabled = false
            inspectButton.isEnabled = false
            chooseFolderButton.isEnabled = false
            fileLabel.text = "Checking selected file…"
            statusLabel.text = "Opening file provider…"
            // SAF providers may be cloud-backed or slow. Never query metadata/read headers on the UI thread.
            worker.execute {
                try {
                    val source = readArchiveSource(uri)
                        ?: throw IllegalStateException("The selected provider did not return readable file information")
                    contentResolver.openInputStream(uri)?.use { input ->
                        if (input.read() < 0) throw IllegalStateException("The selected file is empty or unreadable")
                    } ?: throw IllegalStateException("The selected provider could not open this file")
                    val signature = readPrefix(uri)?.let(ArchiveSignatureDetector::detect)
                    val extension = ArchiveFormatDetector.detect(source.displayName)
                    val supported = extension.format == ArchiveFormat.ZIP || signature == ArchiveFormat.ZIP
                    mainHandler.post {
                        selectedArchive = source
                        fileLabel.text = buildString {
                            append("Selected: "); append(source.displayName ?: "Unknown")
                            append("\\nSize: "); append(source.declaredSizeBytes?.let(::formatBytes) ?: "Unknown")
                            append("\\nDetected extension: "); append(extension.format.label)
                            append("\\nHeader: "); append(signature?.label ?: "Unavailable")
                            append("\\nZIP extraction: "); append(if (supported) "Supported" else "Not supported yet")
                        }
                        extractButton.isEnabled = supported
                        inspectButton.isEnabled = supported
                        chooseFolderButton.isEnabled = supported
                        statusLabel.text = if (supported) "Choose a destination folder to continue."
                            else "File is readable, but its format is not supported yet."
                    }
                } catch (error: Exception) {
                    mainHandler.post {
                        selectedArchive = null
                        fileLabel.text = "Could not read selected file"
                        statusLabel.text = "File access failed: ${error.message ?: error.javaClass.simpleName}"
                    }
                }
            }
        } else if (requestCode == requestFolder) {
            retainPermission(uri, data.flags,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            try {
                // Validate that the returned URI is a usable document-tree URI before enabling extraction.
                DocumentsContract.getTreeDocumentId(uri)
                destinationTree = uri
                statusLabel.text = "Destination folder selected."
                extractButton.isEnabled = selectedArchive != null
            } catch (error: Exception) {
                destinationTree = null
                statusLabel.text = "Could not access selected folder: ${error.message ?: error.javaClass.simpleName}"
            }
        }
    }

    private fun startInspection() {
        val source = selectedArchive ?: return
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
                    statusLabel.text = if (error is com.arcnova.app.engine.ZipInspectionCancelledException) "Inspection cancelled."
                        else "Inspection failed: ${error.message ?: error.javaClass.simpleName}"
                    setBusy(false)
                }
            }
        }
    }

    private fun startExtraction() {
        val source = selectedArchive ?: return
        val tree = destinationTree ?: run {
            statusLabel.text = "Choose an extraction folder first."; return
        }
        setBusy(true)
        cancelRequested.set(false)
        statusLabel.text = "Preparing ZIP extraction…"
        worker.execute {
            val staging = File(cacheDir, "arcnova-extract-${System.currentTimeMillis()}")
            try {
                val input = contentResolver.openInputStream(source.uri)
                    ?: throw IllegalStateException("Cannot open selected archive")
                val result = input.use {
                    ZipExtractionEngine.extractCentralDirectory(
                        input = it,
                        destination = staging,
                        cacheDirectory = cacheDir,
                        // Bound the seekable archive spool independently from extracted-output limits.
                        maxArchiveBytes = MAX_ARCHIVE_INPUT_BYTES,
                        policy = ExtractionPolicy(),
                        cancellationRequested = { cancelRequested.get() },
                        onProgress = { progress -> postProgress(progress) }
                    )
                }
                if (cancelRequested.get()) throw InterruptedException("Extraction cancelled")
                val copied = copyDirectoryToTree(staging, tree)
                mainHandler.post {
                    statusLabel.text = "Done: ${result.filesExtracted} files, ${formatBytes(result.bytesWritten)} extracted. Copied ${copied.filesCopied} files and ${copied.directoriesCreated} folders."
                    setBusy(false)
                }
            } catch (error: Exception) {
                mainHandler.post {
                    statusLabel.text = when (error) {
                        is InterruptedException, is ZipExtractionCancelledException, is TreeCopyCancelledException -> "Extraction cancelled."
                        else -> "Extraction failed: ${error.message ?: error.javaClass.simpleName}"
                    }
                    setBusy(false)
                }
            } finally {
                staging.deleteRecursively()
            }
        }
    }

    private fun postProgress(progress: ZipExtractionProgress) {
        mainHandler.post {
            statusLabel.text = "Extracting: ${progress.currentEntry}\nFiles: ${progress.filesExtracted} • ${formatBytes(progress.bytesWritten)}"
        }
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
        inspectButton.isEnabled = !busy && selectedArchive != null
        chooseFolderButton.isEnabled = !busy && selectedArchive != null
        cancelButton.isEnabled = busy
    }

    private fun retainPermission(uri: Uri, flags: Int, requested: Int) {
        val granted = flags and requested
        val persistable = flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
        if (persistable && granted != 0) runCatching {
            contentResolver.takePersistableUriPermission(uri, granted)
        }
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
