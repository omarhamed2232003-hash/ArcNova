package com.arcnova.app.engine

/** Heuristic installer identification. A positive EXE result is a hint, not proof. */
object WindowsInstallerDetector {
    enum class Kind { MSI_PACKAGE, INNO_SETUP, NSIS, INSTALLSHIELD, WIX_BUNDLE, PE_EXECUTABLE_UNKNOWN, NOT_INSTALLER, UNKNOWN }

    private val msi = byteArrayOf(0xd0.toByte(), 0xcf.toByte(), 0x11, 0xe0.toByte(), 0xa1.toByte(), 0xb1.toByte(), 0x1a, 0xe1.toByte())
    private val markers = listOf(
        "Inno Setup Setup Data" to Kind.INNO_SETUP,
        "NullsoftInst" to Kind.NSIS,
        "InstallShield" to Kind.INSTALLSHIELD,
        "WixBundle" to Kind.WIX_BUNDLE
    )

    fun detect(fileName: String?, prefix: ByteArray?): Kind {
        if (prefix == null) return Kind.UNKNOWN
        if (startsWith(prefix, msi)) return Kind.MSI_PACKAGE
        val pe = WindowsExecutableDetector.inspect(prefix)
        if (pe.result != WindowsExecutableDetector.Result.PE_EXECUTABLE) return Kind.NOT_INSTALLER
        val text = prefix.toString(Charsets.ISO_8859_1)
        markers.firstOrNull { text.contains(it.first, ignoreCase = true) }?.let { return it.second }
        val name = fileName.orEmpty().lowercase()
        return if (name.endsWith(".exe")) Kind.PE_EXECUTABLE_UNKNOWN else Kind.NOT_INSTALLER
    }

    private fun startsWith(bytes: ByteArray, signature: ByteArray): Boolean {
        if (bytes.size < signature.size) return false
        return signature.indices.all { bytes[it] == signature[it] }
    }
}
