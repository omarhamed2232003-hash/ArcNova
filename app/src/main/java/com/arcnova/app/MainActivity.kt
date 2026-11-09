package com.arcnova.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var fileLabel: TextView
    private val requestPick = 1001

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
        }
        startActivityForResult(intent, requestPick)
    }

    @Deprecated("Platform document picker retained for broad compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestPick && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            fileLabel.text = "Selected:\n$uri\nRead-only selection; extraction is not available yet."
        }
    }
}
