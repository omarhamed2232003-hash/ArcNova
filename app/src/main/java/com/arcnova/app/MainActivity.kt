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
import com.arcnova.app.engine.ArchiveFormatDetector
import com.arcnova.app.engine.ArchiveSignatureDetector
import com.arcnova.app.engine.ArchiveSource
import com.arcnova.app.engine.ExtractionPolicy
import com.arcnova.app.engine.ZipExtractionEngine
import com.arcnova.app.engine.ZipInspectionEngine
import com.arcnova.app.engine.ZipExtractionProgress
import com.arcnova.app.engine.ZipExtractionCancelledException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
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
            selectedArchive = readArchiveSource(uri)
            val source = selectedArchive ?: run {
                fileLabel.text = "Could not inspect selected file"; return
            }
            val extension = ArchiveFormatDetector.detect(source.displayName)
            val signature = readPrefix(uri)?.let(ArchiveSignatureDetector::detect)
            val supported = extension.format == ArchiveFormat.ZIP || signature == ArchiveFormat.ZIP
            fileLabel.text = buildString {
                append("Selected: "); append(source.displayName ?: "Unknown")
                append("\nSize: "); append(source.declaredSizeBytes?.let(::formatBytes) ?: "Unknown")
                append("\nDetected extension: "); append(extension.format.label)
                append("\nHeader: "); append(signature?.label ?: "Unavailable")
                append("\nZIP extraction: "); append(if (supported) "Supported" else "Not supported yet")
            }
            extractButton.isEnabled = supported
            inspectButton.isEnabled = supported
            chooseFolderButton.isEnabled = supported
            statusLabel.text = "Choose a destination folder to continue."
        } else if (requestCode == requestFolder) {
            retainPermission(uri, data.flags,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            destinationTree = uri
            statusLabel.text = "Destination folder selected."
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
                    ZipInspectionEngine.inspect(it, cancellationRequested = { cancelRequested.get() })
                }
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
                        append("Unsafe paths: ${report.unsafePathCount}\nDuplicate names: ${report.duplicateNameCount}\n\n")
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
                    ZipExtractionEngine.extract(
                        it, staging, ExtractionPolicy(),
                        cancellationRequested = { cancelRequested.get() },
                        onProgress = { progress -> postProgress(progress) }
                    )
                }
                if (cancelRequested.get()) throw InterruptedException("Extraction cancelled")
                val copied = copyDirectoryToTree(staging, tree)
                mainHandler.post {
                    statusLabel.text = "Done: ${result.filesExtracted} files, ${formatBytes(result.bytesWritten)} extracted. Copied $copied items."
                    setBusy(false)
                }
            } catch (error: Exception) {
                mainHandler.post {
                    statusLabel.text = when (error) {
                        is InterruptedException, is ZipExtractionCancelledException -> "Extraction cancelled."
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

    private fun copyDirectoryToTree(source: File, treeUri: Uri): Int {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        return copyChildren(source, rootUri)
    }

    private fun copyChildren(sourceDir: File, parentUri: Uri): Int {
        var copied = 0
        sourceDir.listFiles()?.forEach { child ->
            if (cancelRequested.get()) throw InterruptedException("Copy cancelled")
            val mime = if (child.isDirectory) "vnd.android.document/directory" else "application/octet-stream"
            val created = DocumentsContract.createDocument(contentResolver, parentUri, mime, child.name)
                ?: throw IllegalStateException("Cannot create destination item: ${child.name}")
            if (child.isDirectory) {
                copied += copyChildren(child, created)
            } else {
                contentResolver.openOutputStream(created, "w")?.use { output ->
                    FileInputStream(child).use { input -> input.copyTo(output, 64 * 1024) }
                } ?: throw IllegalStateException("Cannot write destination file: ${child.name}")
                copied++
            }
        }
        return copied
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
