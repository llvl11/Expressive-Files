package com.baiel.expressivefiles.model

import androidx.compose.runtime.Immutable
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

enum class ArchiveType(val extension: String, val canCreate: Boolean) {
    ZIP("zip", true),
    SEVEN_Z("7z", true),
    TAR("tar", true),
    TAR_GZ("tar.gz", true),
    RAR("rar", false),
    OTHER("", false);

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

// Immutable + stable to the Compose compiler: without this, java.io.File makes
// every FileItem unstable, so file cards can never skip recomposition and each
// state change rebuilds the whole list subtree (SemanticsNode/Matrix churn).
@Immutable
data class FileItem(
    val file: File,
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long? = null,
    val lastModified: Long? = null,
    val isHidden: Boolean,
    val extension: String,
    val fileType: FileType,
    val archiveType: ArchiveType? = null,
    val childCount: Int? = null,
    val duration: Long? = null
) : Serializable {
    companion object {

        /**
         * Builds a FileItem from attributes fetched in bulk (one stat per file).
         * Avoids the 3 extra syscalls per item that fromFile's individual
         * isDirectory()/length()/lastModified() calls would cost.
         */
        fun fromAttrs(
            file: File,
            isDir: Boolean,
            size: Long,
            mtime: Long,
            childCount: Int? = null,
            duration: Long? = null
        ): FileItem {
            val name = file.name
            val extension = if (isDir) "" else file.extension.lowercase()
            val type = determineFileType(name, extension, isDir)

            return FileItem(
                file = file,
                name = name,
                path = file.absolutePath,
                isDirectory = isDir,
                size = if (isDir) null else size,
                lastModified = mtime,
                isHidden = name.startsWith("."),
                extension = extension,
                fileType = type,
                archiveType = if (type == FileType.ARCHIVE) ArchiveType.fromFileName(name) else null,
                childCount = childCount,
                duration = duration
            )
        }
    }
}

private val archiveExtensions = setOf("zip", "7z", "rar", "tar", "gz", "bz2", "xz", "zst", "cbz", "cbr", "cb7", "iso")
private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "heic", "avif")

// Single source of truth for every component that treats an entry as video
// (repo scans, Coil decoding, filters). Previously duplicated in 5 places,
// causing drift such as mkv thumbnails silently falling back to icons.
val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "wmv", "3gp", "m4v", "mpg", "mpeg", "mts", "m2ts")

private val audioExtensions = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma")
private val documentExtensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub", "md", "csv", "rtf")
private val codeExtensions = setOf("kt", "java", "py", "js", "ts", "html", "css", "xml", "json", "c", "cpp", "h", "sh", "rs", "go", "sql", "yaml", "yml", "gradle", "properties")
private val apkExtensions = setOf("apk", "aab", "xapk", "apks")

fun determineFileType(name: String, extension: String, isDirectory: Boolean): FileType {
    if (isDirectory) return FileType.FOLDER
    val lowerName = name.lowercase()

    return when {
        lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz") -> FileType.ARCHIVE
        extension in archiveExtensions -> FileType.ARCHIVE
        extension in imageExtensions -> FileType.IMAGE
        extension in VIDEO_EXTENSIONS -> FileType.VIDEO
        extension in audioExtensions -> FileType.AUDIO
        extension in documentExtensions -> FileType.DOCUMENT
        extension in codeExtensions -> FileType.CODE
        extension in apkExtensions -> FileType.APK
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
    val crc: Long = 0L
) : Serializable

enum class SortMode {
    NAME,
    DATE,
    SIZE
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

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
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

/**
 * OS skin of the app: picks the color scheme flavor and the icon set.
 * PIXEL is the stock Android look; MAGIC_OS mimics Honor's MagicOS
 * (blue accent, white/black backgrounds, MagicOS-style glyphs).
 */
enum class OsType(val title: String) {
    PIXEL("Pixel"),
    MAGIC_OS("MagicOS")
}

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val colorPalette: AppColorPalette = AppColorPalette.VIBRANT,
    val osType: OsType = OsType.PIXEL,
    val pitchBlack: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val showStorageOverview: Boolean = true,
    val storageOverviewCollapsed: Boolean = false,
    val sortMode: SortMode = SortMode.NAME,
    val sortOrder: SortOrder = SortOrder.ASCENDING,
    val viewMode: ViewMode = ViewMode.LIST,
    val defaultArchiveType: ArchiveType = ArchiveType.ZIP,
    val autoCreateExtractFolder: Boolean = true,
    /** Reactive mirror of the persisted UI language (default Russian). */
    val appLanguage: AppLanguage = AppLanguage.RUSSIAN
)

