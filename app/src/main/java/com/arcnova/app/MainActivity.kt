package com.arcnova.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.arcnova.app.engine.ArchiveFormatDetector
import com.arcnova.app.engine.ArchiveSource

class MainActivity : Activity() {
    private lateinit var fileLabel: TextView
    private val requestPick = 1001
    private var selectedArchive: ArchiveSource? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 48, 28, 24)
        }
        root.addView(TextView(this).apply { text = "ArcNova"; textSize = 32f; gravity = Gravity.CENTER })
        root.addView(TextView(this).apply {
            text = "Android archive research"; textSize = 16f
            gravity = Gravity.CENTER; setPadding(0, 8, 0, 28)
        })
        root.addView(Button(this).apply {
            text = "Choose archive part"
            setOnClickListener { pickFile() }
        })
        fileLabel = TextView(this).apply {
            text = "No file selected"; textSize = 14f; setPadding(0, 20, 0, 20)
        }
        root.addView(fileLabel)
        root.addView(TextView(this).apply {
            text = "Engine status\nSREP: not implemented\nLOLZ: not implemented\nExtraction: unavailable in this build"
            textSize = 14f
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

    @Deprecated("Platform document picker retained for broad compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != requestPick) return
        if (resultCode != RESULT_OK) { fileLabel.text = "No file selected"; return }
        val uri = data?.data ?: run {
            fileLabel.text = "Could not read the selected file reference."
            return
        }
        retainReadPermission(uri, data.flags)
        selectedArchive = readArchiveSource(uri)
        val source = selectedArchive ?: run {
            fileLabel.text = "Selected file could not be inspected."
            return
        }
        val detection = ArchiveFormatDetector.detect(source.displayName)
        fileLabel.text = buildString {
            append("Selected: "); append(source.displayName ?: "Unknown name")
            append("\nSize: "); append(source.declaredSizeBytes?.let(::formatBytes) ?: "Unknown")
            append("\nType: "); append(contentResolver.getType(uri) ?: "Unknown")
            append("\nDetected format: "); append(detection.format.label)
            if (detection.isMultipart) { append(" (part "); append(detection.partNumber ?: "?"); append(")") }
            append("\nDetection: filename hint only")
            append("\nDecoder: not connected yet")
            append("\nExtraction is not available yet.")
        }
    }

    private fun retainReadPermission(uri: Uri, resultFlags: Int) {
        val readGranted = resultFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
        val persistableGranted = resultFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
        if (readGranted && persistableGranted) runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun readArchiveSource(uri: Uri): ArchiveSource? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return runCatching {
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
            ArchiveSource(uri = uri, displayName = name, declaredSizeBytes = size)
        }.getOrNull()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = -1
        do { value /= 1024.0; unitIndex++ } while (value >= 1024.0 && unitIndex < units.lastIndex)
        return String.format(java.util.Locale.US, "%.2f %s", value, units[unitIndex])
    }
}
