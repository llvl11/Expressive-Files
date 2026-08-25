package com.example.model

import java.io.File
import java.io.Serializable

enum class FileType {
    FOLDER,
    ARCHIVE,
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    CODE,
    APK,
    OTHER
}

enum class ArchiveType(val extension: String, val displayName: String, val canCreate: Boolean) {
    ZIP("zip", "ZIP Archive", true),
    SEVEN_Z("7z", "7-Zip Archive", true),
    TAR("tar", "TAR Archive", true),
    TAR_GZ("tar.gz", "GZipped TAR", true),
    RAR("rar", "RAR Archive", false),
    OTHER("", "Archive", false);

    companion object {
        fun fromFileName(name: String): ArchiveType {
            val lower = name.lowercase()
            return when {
                lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> TAR_GZ
                lower.endsWith(".zip") || lower.endsWith(".jar") || lower.endsWith(".cbz") -> ZIP
                lower.endsWith(".7z") || lower.endsWith(".cb7") -> SEVEN_Z
                lower.endsWith(".tar") || lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") || lower.endsWith(".tar.xz") -> TAR
                lower.endsWith(".rar") || lower.endsWith(".cbr") -> RAR
                else -> OTHER
            }
        }
    }
}

data class FileItem(
    val file: File,
    val name: String = file.name,
    val path: String = file.absolutePath,
    val isDirectory: Boolean = file.isDirectory,
    val size: Long = if (file.isDirectory) 0L else file.length(),
    val lastModified: Long = file.lastModified(),
    val isHidden: Boolean = file.name.startsWith("."),
    val extension: String = if (file.isDirectory) "" else file.extension.lowercase(),
    val fileType: FileType = determineFileType(file),
    val archiveType: ArchiveType? = if (determineFileType(file) == FileType.ARCHIVE) ArchiveType.fromFileName(file.name) else null,
    val childCount: Int = if (file.isDirectory) (file.list()?.size ?: 0) else 0,
    val isSelected: Boolean = false
) : Serializable

fun determineFileType(file: File): FileType {
    if (file.isDirectory) return FileType.FOLDER
    val name = file.name.lowercase()
    val ext = file.extension.lowercase()

    return when {
        name.endsWith(".tar.gz") || name.endsWith(".tgz") -> FileType.ARCHIVE
        ext in setOf("zip", "7z", "rar", "tar", "gz", "bz2", "xz", "zst", "cbz", "cbr", "cb7", "iso") -> FileType.ARCHIVE
        ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "heic", "avif") -> FileType.IMAGE
        ext in setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "wmv", "3gp", "m4v") -> FileType.VIDEO
        ext in setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma") -> FileType.AUDIO
        ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub", "md", "csv", "rtf") -> FileType.DOCUMENT
        ext in setOf("kt", "java", "py", "js", "ts", "html", "css", "xml", "json", "c", "cpp", "h", "sh", "rs", "go", "sql", "yaml", "yml", "gradle", "properties") -> FileType.CODE
        ext in setOf("apk", "aab", "xapk", "apks") -> FileType.APK
        else -> FileType.OTHER
    }
}

data class ArchiveEntryItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long,
    val lastModified: Long,
    val crc: Long = 0L,
    val comment: String = ""
) : Serializable

enum class SortMode(val title: String) {
    NAME("Name"),
    DATE("Date modified"),
    SIZE("Size"),
    TYPE("File type")
}

enum class SortOrder {
    ASCENDING,
    DESCENDING
}

enum class ViewMode {
    GRID,
    LIST,
    EXPRESSIVE_CARDS
}

enum class AppThemeMode(val title: String) {
    SYSTEM("System Default"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class AppColorPalette(val title: String) {
    VIBRANT("Vibrant Palette"),
    DYNAMIC("Dynamic Material You"),
    NEON_VIOLET("Neon Violet"),
    CYBER_TEAL("Cyber Teal"),
    SUNSET_CORAL("Sunset Coral"),
    EMERALD_MINT("Emerald Mint"),
    CITRUS_SUN("Citrus Gold")
}

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val colorPalette: AppColorPalette = AppColorPalette.VIBRANT,
    val showHiddenFiles: Boolean = false,
    val showStorageOverview: Boolean = true,
    val storageOverviewCollapsed: Boolean = false,
    val sortMode: SortMode = SortMode.NAME,
    val sortOrder: SortOrder = SortOrder.ASCENDING,
    val viewMode: ViewMode = ViewMode.LIST,
    val defaultArchiveType: ArchiveType = ArchiveType.ZIP,
    val autoCreateExtractFolder: Boolean = true,
    val gridColumnCount: Int = 2
)

