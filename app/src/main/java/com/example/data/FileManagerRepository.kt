package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.archive.ArchiveEngine
import com.example.model.ArchiveType
import com.example.model.FileItem
import com.example.model.FileType
import com.example.model.SortMode
import com.example.model.SortOrder
import com.example.model.StorageStats
import com.example.model.determineFileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileManagerRepository(private val context: Context) {

    val rootStorageDirectory: File
        get() = try {
            val ext = Environment.getExternalStorageDirectory()
            if (ext != null && ext.exists() && ext.canRead()) ext else context.filesDir
        } catch (e: Exception) {
            context.filesDir
        }

    val appStorageDirectory: File
        get() = context.getExternalFilesDir(null) ?: context.filesDir

    val downloadsDirectory: File
        get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).let {
            if (it.exists()) it else File(appStorageDirectory, "Downloads").apply { mkdirs() }
        }

    val documentsDirectory: File
        get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).let {
            if (it.exists()) it else File(appStorageDirectory, "Documents").apply { mkdirs() }
        }

    val picturesDirectory: File
        get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).let {
            if (it.exists()) it else File(appStorageDirectory, "Pictures").apply { mkdirs() }
        }

    val musicDirectory: File
        get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).let {
            if (it.exists()) it else File(appStorageDirectory, "Music").apply { mkdirs() }
        }

    val moviesDirectory: File
        get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).let {
            if (it.exists()) it else File(appStorageDirectory, "Movies").apply { mkdirs() }
        }

    /**
     * Initializes demo data in app files directory with sample files and sample archives (.zip, .7z, .tar).
     */
    suspend fun initializeDemoFilesIfNeeded() = withContext(Dispatchers.IO) {
        try {
            val demoRoot = File(appStorageDirectory, "ExpressiveFilesDemo")
            if (!demoRoot.exists()) {
                demoRoot.mkdirs()

                // Create subfolders
                val archivesDir = File(demoRoot, "Archives").apply { mkdirs() }
                val docsDir = File(demoRoot, "Documents").apply { mkdirs() }
                val devDir = File(demoRoot, "SourceCode").apply { mkdirs() }
                val mediaDir = File(demoRoot, "Media").apply { mkdirs() }

                // Create sample docs
                File(docsDir, "Material3_Expressive_Guide.md").writeText(
                    "# Material 3 Expressive Design\n\n" +
                    "Welcome to Expressive Files! Features bold typography, cookie shapes, and complete archive support.\n\n" +
                    "- Compression: 7z, Tar, Zip, Tar.gz, Rar\n" +
                    "- Dynamic Material You Theming\n" +
                    "- Animated cookie buttons\n" +
                    "- Fast native I/O\n"
                )
                File(docsDir, "Project_Roadmap.txt").writeText(
                    "Q1: Native 7z & RAR decompression\nQ2: High-speed TAR streaming\nQ3: Expressive animations & Cookie cards\n"
                )

                // Create sample code
                File(devDir, "FileManager.kt").writeText(
                    "// Expressive Files Engine\npackage com.example.files\n\nclass ArchiveWorker {\n    fun compress() = println(\"Compressing in 7z, Tar, Zip!\")\n}\n"
                )
                File(devDir, "app_config.json").writeText(
                    "{\n  \"appName\": \"Expressive Files\",\n  \"version\": \"1.0.0\",\n  \"expressiveTheme\": true,\n  \"cookieCorners\": 28\n}"
                )

                // Create sample text in media
                File(mediaDir, "playlist_favorites.m3u").writeText(
                    "#EXTM3U\n#EXTINF:240,Expressive Beats - Synthwave Loop\nmusic/track01.mp3\n"
                )

                // Create sample Archives (.zip, .7z, .tar, .tar.gz)
                val sampleSources = listOf(
                    File(docsDir, "Material3_Expressive_Guide.md"),
                    File(docsDir, "Project_Roadmap.txt"),
                    File(devDir, "app_config.json")
                )

                ArchiveEngine.createArchive(
                    sourceFiles = sampleSources,
                    destinationArchive = File(archivesDir, "Demo_Documents.zip"),
                    format = ArchiveType.ZIP,
                    onProgress = {}
                )

                ArchiveEngine.createArchive(
                    sourceFiles = listOf(devDir),
                    destinationArchive = File(archivesDir, "SourceCode_Backup.7z"),
                    format = ArchiveType.SEVEN_Z,
                    onProgress = {}
                )

                ArchiveEngine.createArchive(
                    sourceFiles = sampleSources,
                    destinationArchive = File(archivesDir, "Project_Assets.tar.gz"),
                    format = ArchiveType.TAR_GZ,
                    onProgress = {}
                )

                ArchiveEngine.createArchive(
                    sourceFiles = listOf(docsDir),
                    destinationArchive = File(archivesDir, "Archive_Test.tar"),
                    format = ArchiveType.TAR,
                    onProgress = {}
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sortItems(
        items: List<FileItem>,
        sortMode: SortMode,
        sortOrder: SortOrder
    ): List<FileItem> {
        return items.sortedWith { a, b ->
            // Directories always first
            if (a.isDirectory && !b.isDirectory) return@sortedWith -1
            if (!a.isDirectory && b.isDirectory) return@sortedWith 1

            val comparison = when (sortMode) {
                SortMode.NAME -> a.name.compareTo(b.name, ignoreCase = true)
                SortMode.DATE -> a.lastModified.compareTo(b.lastModified)
                SortMode.SIZE -> a.size.compareTo(b.size)
                SortMode.TYPE -> a.fileType.name.compareTo(b.fileType.name)
            }

            if (sortOrder == SortOrder.ASCENDING) comparison else -comparison
        }
    }

    /**
     * List files in a directory with sorting and hidden file filtering.
     */
    suspend fun listFiles(
        directory: File,
        showHidden: Boolean,
        sortMode: SortMode,
        sortOrder: SortOrder
    ): List<FileItem> = withContext(Dispatchers.IO) {
        if (!directory.exists() || !directory.canRead()) {
            return@withContext emptyList()
        }

        val rawFiles = directory.listFiles() ?: return@withContext emptyList()

        val items = rawFiles
            .filter { file -> showHidden || !file.name.startsWith(".") }
            .map { file ->
                FileItem(file = file)
            }

        sortItems(items, sortMode, sortOrder)
    }

    /**
     * Search files matching a query.
     */
    suspend fun searchFiles(
        startDir: File,
        query: String,
        recursive: Boolean = true,
        showHidden: Boolean = false,
        sortMode: SortMode = SortMode.NAME,
        sortOrder: SortOrder = SortOrder.ASCENDING
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileItem>()
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) return@withContext emptyList()

        fun searchIn(dir: File, depth: Int) {
            if (depth > 6) return
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (!showHidden && file.name.startsWith(".")) continue
                if (file.name.lowercase().contains(cleanQuery)) {
                    results.add(FileItem(file))
                }
                if (recursive && file.isDirectory && file.canRead()) {
                    searchIn(file, depth + 1)
                }
            }
        }

        searchIn(startDir, 0)
        sortItems(results, sortMode, sortOrder)
    }

    /**
     * Filter files by specific category from a base directory.
     */
    suspend fun getFilesByCategory(
        category: FileType,
        baseDir: File = rootStorageDirectory,
        showHidden: Boolean = false,
        sortMode: SortMode = SortMode.DATE,
        sortOrder: SortOrder = SortOrder.DESCENDING
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileItem>()

        fun scan(dir: File, depth: Int) {
            if (depth > 5) return
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (!showHidden && file.name.startsWith(".")) continue
                if (file.isDirectory && file.canRead()) {
                    // Skip hidden android system dirs
                    if (!file.name.startsWith("Android") || depth == 0) {
                        scan(file, depth + 1)
                    }
                } else if (file.isFile) {
                    val item = FileItem(file)
                    if (item.fileType == category) {
                        results.add(item)
                    }
                }
            }
        }

        scan(baseDir, 0)
        sortItems(results, sortMode, sortOrder)
    }

    /**
     * Create a new folder.
     */
    suspend fun createDirectory(parentDir: File, folderName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val cleanName = folderName.trim()
            if (cleanName.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Name cannot be empty"))
            val target = File(parentDir, cleanName)
            if (target.exists()) return@withContext Result.failure(IllegalStateException("Folder already exists"))
            if (target.mkdirs()) {
                Result.success(target)
            } else {
                Result.failure(Exception("Failed to create folder"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a new empty file.
     */
    suspend fun createNewFile(parentDir: File, fileName: String, initialContent: String = ""): Result<File> = withContext(Dispatchers.IO) {
        try {
            val cleanName = fileName.trim()
            if (cleanName.isEmpty()) return@withContext Result.failure(IllegalArgumentException("File name cannot be empty"))
            val target = File(parentDir, cleanName)
            if (target.exists()) return@withContext Result.failure(IllegalStateException("File already exists"))
            if (target.createNewFile()) {
                if (initialContent.isNotEmpty()) {
                    target.writeText(initialContent)
                }
                Result.success(target)
            } else {
                Result.failure(Exception("Failed to create file"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Rename a file or folder.
     */
    suspend fun rename(target: File, newName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val cleanName = newName.trim()
            if (cleanName.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Name cannot be empty"))
            val newFile = File(target.parentFile, cleanName)
            if (newFile.exists()) return@withContext Result.failure(IllegalStateException("Target name already exists"))
            if (target.renameTo(newFile)) {
                Result.success(newFile)
            } else {
                Result.failure(Exception("Rename operation failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete files or folders recursively.
     */
    suspend fun deleteFiles(files: List<File>): Boolean = withContext(Dispatchers.IO) {
        var allSuccess = true
        for (file in files) {
            if (file.isDirectory) {
                if (!file.deleteRecursively()) allSuccess = false
            } else {
                if (!file.delete()) allSuccess = false
            }
        }
        allSuccess
    }

    /**
     * Copy files or folders to destination directory.
     */
    suspend fun copyFiles(sources: List<File>, destinationDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            destinationDir.mkdirs()
            for (src in sources) {
                val dest = getUniqueDestinationFile(destinationDir, src.name)
                if (src.isDirectory) {
                    src.copyRecursively(dest, overwrite = true)
                } else {
                    src.copyTo(dest, overwrite = true)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Move files or folders to destination directory.
     */
    suspend fun moveFiles(sources: List<File>, destinationDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            destinationDir.mkdirs()
            for (src in sources) {
                val dest = getUniqueDestinationFile(destinationDir, src.name)
                if (!src.renameTo(dest)) {
                    // Fallback to copy and delete
                    if (src.isDirectory) {
                        src.copyRecursively(dest, overwrite = true)
                        src.deleteRecursively()
                    } else {
                        src.copyTo(dest, overwrite = true)
                        src.delete()
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Duplicate a file or folder.
     */
    suspend fun duplicate(file: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val parent = file.parentFile ?: return@withContext Result.failure(Exception("No parent directory"))
            val name = file.nameWithoutExtension
            val ext = if (file.isDirectory || file.extension.isEmpty()) "" else ".${file.extension}"
            val duplicateName = "${name}_copy$ext"
            val dest = getUniqueDestinationFile(parent, duplicateName)

            if (file.isDirectory) {
                file.copyRecursively(dest, overwrite = false)
            } else {
                file.copyTo(dest, overwrite = false)
            }
            Result.success(dest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Calculate storage statistics.
     */
    suspend fun getStorageStats(): StorageStats = withContext(Dispatchers.IO) {
        try {
            val root = rootStorageDirectory
            val total = root.totalSpace
            val free = root.freeSpace
            val used = (total - free).coerceAtLeast(0L)

            var images = 0L
            var videos = 0L
            var audio = 0L
            var docs = 0L
            var archives = 0L
            var apks = 0L
            var others = 0L

            fun inspect(dir: File, depth: Int) {
                if (depth > 4) return
                val files = dir.listFiles() ?: return
                for (file in files) {
                    if (file.isDirectory) {
                        if (!file.name.startsWith("Android")) {
                            inspect(file, depth + 1)
                        }
                    } else {
                        val len = file.length()
                        when (determineFileType(file)) {
                            FileType.IMAGE -> images += len
                            FileType.VIDEO -> videos += len
                            FileType.AUDIO -> audio += len
                            FileType.DOCUMENT -> docs += len
                            FileType.ARCHIVE -> archives += len
                            FileType.APK -> apks += len
                            else -> others += len
                        }
                    }
                }
            }

            inspect(root, 0)

            StorageStats(
                totalBytes = total,
                freeBytes = free,
                usedBytes = used,
                imagesBytes = images,
                videosBytes = videos,
                audioBytes = audio,
                documentsBytes = docs,
                archivesBytes = archives,
                apkBytes = apks,
                othersBytes = others
            )
        } catch (e: Exception) {
            StorageStats()
        }
    }

    /**
     * Open file with external viewer or share.
     */
    fun openFile(file: File) {
        try {
            val uri = getFileUri(file)
            val mimeType = getMimeType(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun shareFiles(files: List<File>) {
        try {
            if (files.isEmpty()) return
            val uris = files.map { getFileUri(it) }
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = getMimeType(files.first())
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            context.startActivity(Intent.createChooser(intent, "Share via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getFileUri(file: File): Uri {
        return try {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            Uri.fromFile(file)
        }
    }

    private fun getMimeType(file: File): String {
        val ext = file.extension.lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "pdf" -> "application/pdf"
            "txt", "log", "md", "csv" -> "text/plain"
            "json" -> "application/json"
            "xml", "html", "htm" -> "text/html"
            "zip" -> "application/zip"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "rar" -> "application/vnd.rar"
            "apk" -> "application/vnd.android.package-archive"
            else -> "*/*"
        }
    }

    private fun getUniqueDestinationFile(parent: File, originalName: String): File {
        var file = File(parent, originalName)
        if (!file.exists()) return file

        val nameWithoutExt = file.nameWithoutExtension
        val ext = if (file.extension.isNotEmpty()) ".${file.extension}" else ""
        var count = 1

        while (file.exists()) {
            file = File(parent, "${nameWithoutExt}_$count$ext")
            count++
        }
        return file
    }
}
