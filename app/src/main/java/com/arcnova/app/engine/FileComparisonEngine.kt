package com.arcnova.app.engine

import java.io.InputStream

/**
 * Compares two streams without loading either file into memory.
 * Input streams remain caller-owned, matching FileAnalysisAnalyzer's contract.
 */
object FileComparisonEngine {
    fun compare(
        leftName: String,
        left: InputStream,
        rightName: String,
        right: InputStream
    ): FileComparisonReport {
        val leftReport = FileAnalysisAnalyzer.analyze(leftName, left)
        val rightReport = FileAnalysisAnalyzer.analyze(rightName, right)
        return FileComparisonReport(
            left = leftReport,
            right = rightReport,
            sameSize = leftReport.sizeBytes == rightReport.sizeBytes,
            sameContent = leftReport.sizeBytes == rightReport.sizeBytes &&
                leftReport.sha256.equals(rightReport.sha256, ignoreCase = true)
        )
    }
}

data class FileComparisonReport(
    val left: FileAnalysisReport,
    val right: FileAnalysisReport,
    val sameSize: Boolean,
    val sameContent: Boolean
)
