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
                lower.endsWith(".tar") || lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") ||
                    lower.endsWith(".tar.xz") || lower.endsWith(".txz") -> TAR
                lower.endsWith(".rar") || lower.endsWith(".cbr") -> RAR
                else -> OTHER
            }
        }
    }
}

/**
 * Badge/pill label for an archive format. Explicit mapping - the old blanket
 * `name.replace('_', '.')` leaked raw enum names into the UI: SEVEN_Z rendered
 * as "SEVEN.7Z" on every 7z pill/badge, OTHER as an English "OTHER" badge on
 * a Russian-localized .gz/.iso card. OTHER is not a format name at all, so it
 * returns "" and callers skip the badge. Lives in the model layer (not the UI)
 * so the ViewModel can show the same spelling in progress operations.
 */
val ArchiveType.label: String
    get() = when (this) {
        ArchiveType.ZIP -> "ZIP"
        ArchiveType.SEVEN_Z -> "7Z"
        ArchiveType.TAR -> "TAR"
        ArchiveType.TAR_GZ -> "TAR.GZ"
        ArchiveType.RAR -> "RAR"
        ArchiveType.OTHER -> ""
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

// Kept in sync with ArchiveType.fromFileName: "jar" (a ZIP container the engine
// already opens) and "tbz2" were typed OTHER, so they never reached the archive
// viewer or the extract action even though the engine supports them. "txz" was
// the same gap - the engine decompresses .tar.xz (ArchiveEngine.createTarInputStream).
private val archiveExtensions = setOf("zip", "7z", "rar", "tar", "gz", "bz2", "xz", "zst", "cbz", "cbr", "cb7", "iso", "jar", "tbz2", "txz")
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

/**
 * Type of an entry INSIDE an archive. Same rules as for real files, but the
 * extension is lowercased here: archive entries never pass through
 * [FileItem.fromAttrs] (which lowercases), so "PHOTO.JPG" used to miss every
 * lowercase extension set and render the generic file icon.
 */
fun archiveEntryFileType(name: String, isDirectory: Boolean): FileType =
    determineFileType(name, name.substringAfterLast('.', "").lowercase(), isDirectory)

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
    /**
     * No explicit choice: the platform default ordering (name, A to Z), the
     * same order Android's own file picker uses. Selected metrics are never
     * highlighted in this state - the sort strip shows nothing chosen.
     */
    DEFAULT,
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

/**
 * Accent palette of the app. DYNAMIC (Material You) leads the list so the
 * settings screen offers wallpaper-derived colors first.
 */
enum class AppColorPalette(val title: String) {
    DYNAMIC("Dynamic Material You"),
    VIBRANT("Vibrant Palette"),
    NEON_VIOLET("Neon Violet"),
    CYBER_TEAL("Cyber Teal"),
    SUNSET_CORAL("Sunset Coral"),
    EMERALD_MINT("Emerald Mint"),
    CITRUS_SUN("Citrus Gold")
}

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val colorPalette: AppColorPalette = AppColorPalette.DYNAMIC,
    val pitchBlack: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val showStorageOverview: Boolean = true,
    val storageOverviewCollapsed: Boolean = false,
    val sortMode: SortMode = SortMode.DEFAULT,
    val sortOrder: SortOrder = SortOrder.ASCENDING,
    val viewMode: ViewMode = ViewMode.LIST,
    val defaultArchiveType: ArchiveType = ArchiveType.ZIP,
    val autoCreateExtractFolder: Boolean = true,
    /** Reactive mirror of the persisted UI language (default Russian). */
    val appLanguage: AppLanguage = AppLanguage.RUSSIAN
)

