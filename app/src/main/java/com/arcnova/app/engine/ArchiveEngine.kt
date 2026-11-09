package com.arcnova.app.engine

import android.net.Uri

/** Adapter contract for a future, license-reviewed archive backend. */
interface ArchiveEngine {
    val id: String
    val displayName: String
    fun canHandle(parts: List<Uri>): Boolean
}
