package com.baiel.expressivefiles.model

import java.io.File

data class ArchiveProgress(
    // Blank by default: never a hardcoded English fallback in the model.
    // Display sites substitute progress_working for blank titles.
    val operation: String = "",
    val currentFileName: String = "",
    val filesProcessed: Int = 0,
    val totalFiles: Int = 0,
    val bytesProcessed: Long = 0L,
    val totalBytes: Long = 0L,
    val isIndeterminate: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null,
    val targetFile: File? = null,
    // File entries whose target already existed on disk and was therefore
    // left untouched by extraction (never destroyed user data). Surfaces use
    // it only at completion to report "N kept".
    val skippedExisting: Int = 0
) {
    val progressFloat: Float
        get() = when {
            totalFiles > 0 -> (filesProcessed.toFloat() / totalFiles.toFloat()).coerceIn(0f, 1f)
            totalBytes > 0 -> (bytesProcessed.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }
}

data class StorageStats(
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val imagesBytes: Long = 0L,
    val videosBytes: Long = 0L,
    val audioBytes: Long = 0L,
    val documentsBytes: Long = 0L,
    val archivesBytes: Long = 0L,
    val apkBytes: Long = 0L,
    val othersBytes: Long = 0L
)

enum class ClipboardAction {
    COPY,
    CUT
}

data class ClipboardState(
    val items: List<FileItem> = emptyList(),
    val action: ClipboardAction = ClipboardAction.COPY
)
