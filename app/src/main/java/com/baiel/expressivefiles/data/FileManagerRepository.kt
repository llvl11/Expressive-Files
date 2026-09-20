package com.baiel.expressivefiles.data

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.util.LruCache
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.baiel.expressivefiles.model.FileItem
import com.baiel.expressivefiles.model.FileType
import com.baiel.expressivefiles.model.SortMode
import com.baiel.expressivefiles.model.SortOrder
import com.baiel.expressivefiles.model.StorageStats
import com.baiel.expressivefiles.model.VIDEO_EXTENSIONS
import com.baiel.expressivefiles.model.determineFileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

/**
 * Name-level failures, typed so the create/rename dialog can explain WHY an
 * operation was rejected. Anonymous exceptions carried no reason up to the UI,
 * so every rejection (blank name, illegal characters, duplicate name) looked
 * identical: the dialog closed and nothing happened.
 */
class InvalidNameException : Exception("Invalid item name")
class NameConflictException : Exception("An item with this name already exists")

/**
 * All filesystem access funnels through this class.
 *
 * Design rules that keep it fast, correct and battery-friendly:
 *  - ONE NIO readdir primitive ([listEntries]) feeds listing, search, category
 *    scans and storage stats - no duplicate walkers to keep in sync.
 *  - Recursive work is serialized on a single limited dispatcher so heavy scans
 *    cannot saturate every IO thread and drain the battery; quick single-folder
 *    listings still use the normal IO pool for responsiveness.
 *  - Every mutation invalidates the directory cache; stale UI after delete or
 *    rename was historically THE most reported bug here.
 *  - Duration probing never runs inside a listing pass (native cost per file);
 *    the ViewModel enriches afterward via [resolveVideoDuration].
 */
class FileManagerRepository(private val context: Context) {

    private companion object {
        // Upper bound for recursive search/category scans so pathological
        // storage layouts cannot balloon the heap with FileItem instances.
        const val MAX_SCAN_RESULTS = 1_000

        // App-sandbox trees that are never useful in search/category results and
        // cost the most I/O to walk. Skipped at depth > 0.
        val SKIP_DIRS = setOf("data", "obb", "sandbox")

        // Never worth walking during storage analysis either.
        val ANALYSIS_SKIP_PREFIXES = setOf("Android", ".")

        // Copy chunk for stream operations.
        const val COPY_BUFFER = 64 * 1024

        // MediaMetadataRetriever costs tens of ms per file and allocates native
        // resources; process-wide cache keyed by path+mtime so a video's
        // duration is probed exactly once (survives ViewModel recreation).
        private val DURATION_CACHE = LruCache<String, Long>(600)
    }

    /**
     * One filesystem entry with attributes fetched in a single syscall
     * (NIO path) or three cheap ones (legacy fallback).
     */
    internal class Entry(
        @JvmField val file: File,
        @JvmField val isDir: Boolean,
        @JvmField val size: Long,
        @JvmField val mtime: Long
    )

    // Session-long directory cache so revisiting a folder (e.g. back navigation)
    // is instant and skips all disk I/O. Bounded by LruCache; stores UNSORTED
    // items so any sort mode can be applied from one cached listing.
    private val directoryCache = LruCache<String, List<FileItem>>(32)

    // Limited dispatcher to serialize deep scans without starving normal I/O;
    // also caps energy usage on big trees (one core max instead of all).
    @OptIn(ExperimentalCoroutinesApi::class)
    private val backgroundScanDispatcher = Dispatchers.IO.limitedParallelism(1)

    val rootStorageDirectory: File = try {
        val ext = Environment.getExternalStorageDirectory()
        if (ext != null && ext.exists() && ext.canRead()) ext else context.filesDir
    } catch (_: Exception) {
        context.filesDir
    }

    suspend fun getStorageCapacity(): StorageStats = withContext(Dispatchers.IO) {
        val root = rootStorageDirectory
        val total = root.totalSpace
        val free = root.freeSpace
        StorageStats(
            totalBytes = total,
            freeBytes = free,
            usedBytes = (total - free).coerceAtLeast(0L)
        )
    }

    // =====================================================================================
    // Listing primitives
    // =====================================================================================

    /**
     * Core readdir primitive: NIO returns type+size+mtime in one stat per entry
     * (java.io would need three calls per file). A transient FUSE error in the
     * middle of an iteration previously produced an empty folder until relaunch -
     * now a legacy pass retries before giving up.
     */
    private fun listEntries(dir: File, showHidden: Boolean): ArrayList<Entry>? {
        if (!dir.exists() || !dir.canRead()) return null

        val out = ArrayList<Entry>()
        var nioOk = false
        try {
            Files.newDirectoryStream(dir.toPath()).use { stream ->
                for (path in stream) {
                    val name = path.fileName.toString()
                    if (!showHidden && name.startsWith(".")) continue
                    val attrs = Files.readAttributes(path, BasicFileAttributes::class.java)
                    val isDir = attrs.isDirectory
                    out.add(
                        Entry(
                            file = path.toFile(),
                            isDir = isDir,
                            size = if (isDir) 0L else attrs.size(),
                            mtime = attrs.lastModifiedTime().toMillis()
                        )
                    )
                }
            }
            nioOk = true
        } catch (_: Exception) {
            // fall through
        }
        if (nioOk) return out

        // NIO failed mid-iteration: `out` may already hold a partial pass.
        // Restart the legacy fallback from a clean list so entries that were
        // read before the transient error are never duplicated.
        out.clear()
        try {
            val raw = dir.listFiles() ?: return null
            for (file in raw) {
                if (!showHidden && file.name.startsWith(".")) continue
                val isDir = file.isDirectory
                out.add(Entry(file, isDir, if (isDir) 0L else file.length(), file.lastModified()))
            }
        } catch (_: Exception) {
            return null
        }
        return out
    }

    /** Child count via name-only directory iteration (no attribute stats). */
    private fun countChildren(dir: File): Int {
        return try {
            var n = 0
            Files.newDirectoryStream(dir.toPath()).use { stream ->
                for (ignored in stream) n++
            }
            n
        } catch (_: Exception) {
            0
        }
    }

    /** Builds a display item from pre-fetched attributes (no extra syscalls). */
    private fun itemOf(e: Entry): FileItem =
        if (e.isDir) FileItem.fromAttrs(e.file, true, 0L, e.mtime, countChildren(e.file))
        else FileItem.fromAttrs(e.file, false, e.size, e.mtime)

    fun sortItems(
        items: List<FileItem>,
        sortMode: SortMode,
        sortOrder: SortOrder
    ): List<FileItem> {
        if (items.size < 2) return items

        // Partition once so directories stay pinned first regardless of order,
        // and each group sorts against a cheaper comparator.
        val dirs = ArrayList<FileItem>()
        val files = ArrayList<FileItem>(items.size)
        for (item in items) {
            (if (item.isDirectory) dirs else files).add(item)
        }

        val base: Comparator<FileItem> = when (sortMode) {
            // DEFAULT mirrors the platform's own ordering: name A-Z, pinned
            // folders first (see the partition above).
            SortMode.DEFAULT, SortMode.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER, FileItem::name)
            SortMode.DATE -> compareBy { it.lastModified ?: 0L }
            SortMode.SIZE -> compareBy { it.size ?: 0L }
        }
        val cmp = if (sortOrder == SortOrder.ASCENDING) base else base.reversed()

        dirs.sortWith(cmp)
        files.sortWith(cmp)
        return dirs + files
    }

    // =====================================================================================
    // Single generic depth-first walker used by ALL recursive features.
    // =====================================================================================

    /**
     * Depth-first traversal with central cancellation, budget and skip rules.
     *
     * @param job         owning coroutine Job; stops promptly when canceled.
     * @param maxDepth    how deep to descend from [root].
     * @param enter       decide whether to descend into a directory entry.
     * @param visitFile   receive each entry; only called while budget remains.
     * @param done        budget/stop predicate consulted between entries.
     */
    private inline fun walkTree(
        root: File,
        job: Job?,
        showHidden: Boolean,
        maxDepth: Int,
        crossinline done: () -> Boolean,
        crossinline enter: (dirName: String, depth: Int) -> Boolean,
        crossinline visitFileOrDir: (Entry) -> Unit
    ) {
        val stack = ArrayDeque<Pair<File, Int>>()
        stack.addLast(root to 0)

        while (stack.isNotEmpty()) {
            if (job != null && !job.isActive) return
            if (done()) return
            val (dir, depth) = stack.removeLast()

            val entries = listEntries(dir, showHidden) ?: continue
            for (e in entries) {
                if (job != null && !job.isActive || done()) return
                visitFileOrDir(e)

                if (e.isDir && depth < maxDepth && enter(e.file.name, depth)) {
                    stack.addLast(e.file to (depth + 1))
                }
            }
        }
    }

    // =====================================================================================
    // Public read APIs
    // =====================================================================================

    /**
     * List files in a directory with sorting and hidden file filtering.
     * Uses cache to return immediate results if available.
     */
    suspend fun listFiles(
        directory: File,
        showHidden: Boolean,
        sortMode: SortMode,
        sortOrder: SortOrder,
        useCache: Boolean = true
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val cacheKey = "${directory.absolutePath}:$showHidden"
        if (useCache) {
            directoryCache.get(cacheKey)?.let { cached ->
                return@withContext sortItems(cached, sortMode, sortOrder)
            }
        }

        val entries = listEntries(directory, showHidden) ?: return@withContext emptyList()

        // No MediaMetadataRetriever here by design - see class docs.
        val items = ArrayList<FileItem>(entries.size)
        for (e in entries) items.add(itemOf(e))

        directoryCache.put(cacheKey, items)
        sortItems(items, sortMode, sortOrder)
    }

    /**
     * Search files matching a query. Serialized on the background dispatcher;
     * stops promptly on cancellation and result-cap saturation.
     */
    suspend fun searchFiles(
        startDir: File,
        query: String,
        recursive: Boolean = true,
        showHidden: Boolean = false,
        sortMode: SortMode = SortMode.NAME,
        sortOrder: SortOrder = SortOrder.ASCENDING,
        maxResults: Int = MAX_SCAN_RESULTS
    ): List<FileItem> = withContext(backgroundScanDispatcher) {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) return@withContext emptyList()

        val results = ArrayList<FileItem>()
        walkTree(
            root = startDir,
            job = coroutineContext[Job],
            showHidden = showHidden,
            maxDepth = if (recursive) Int.MAX_VALUE else 0,
            done = { results.size >= maxResults },
            enter = { name, depth -> !shouldSkipDir(name, depth) },
            visitFileOrDir = { e ->
                // ignoreCase avoids a lowercase() allocation per visited entry
                // (thousands during a recursive search).
                if (e.file.name.contains(cleanQuery, ignoreCase = true)) results.add(itemOf(e))
            }
        )
        sortItems(results, sortMode, sortOrder)
    }

    /**
     * Filter files by specific category across the tree. Serialized too.
     */
    suspend fun getFilesByCategory(
        category: FileType,
        baseDir: File = rootStorageDirectory,
        showHidden: Boolean = false,
        sortMode: SortMode = SortMode.DATE,
        sortOrder: SortOrder = SortOrder.DESCENDING,
        maxResults: Int = MAX_SCAN_RESULTS
    ): List<FileItem> = withContext(backgroundScanDispatcher) {
        val results = ArrayList<FileItem>()
        walkTree(
            root = baseDir,
            job = coroutineContext[Job],
            showHidden = showHidden,
            maxDepth = Int.MAX_VALUE,
            done = { results.size >= maxResults },
            enter = { name, depth -> !shouldSkipDir(name, depth) },
            visitFileOrDir = { e ->
                if (!e.isDir && determineFileType(e.file.name, e.file.extension.lowercase(), false) == category) {
                    results.add(FileItem.fromAttrs(e.file, false, e.size, e.mtime))
                }
            }
        )
        sortItems(results, sortMode, sortOrder)
    }

    /**
     * Calculate storage statistics with a capped, cheap walk
     * (names+attributes only; no content reads => battery friendly).
     */
    suspend fun getStorageStats(
        maxDepth: Int = 2,
        maxEntries: Int = 1_200
    ): StorageStats = withContext(backgroundScanDispatcher) {
        try {
            val capacity = getStorageCapacity()
            var inspected = 0

            var images = 0L
            var videos = 0L
            var audio = 0L
            var docs = 0L
            var archives = 0L
            var apks = 0L
            var others = 0L

            walkTree(
                root = rootStorageDirectory,
                job = coroutineContext[Job],
                showHidden = false,
                maxDepth = maxDepth,
                done = { inspected >= maxEntries },
                enter = { name, depth ->
                    !shouldSkipDir(name, depth) &&
                            !ANALYSIS_SKIP_PREFIXES.any { prefix -> name.startsWith(prefix) }
                },
                visitFileOrDir = { e ->
                    inspected++
                    if (!e.isDir) {
                        when (
                            determineFileType(e.file.name, e.file.extension.lowercase(), false)
                        ) {
                            FileType.IMAGE -> images += e.size
                            FileType.VIDEO -> videos += e.size
                            FileType.AUDIO -> audio += e.size
                            FileType.DOCUMENT -> docs += e.size
                            FileType.ARCHIVE -> archives += e.size
                            FileType.APK -> apks += e.size
                            else -> others += e.size
                        }
                    }
                }
            )

            StorageStats(
                totalBytes = capacity.totalBytes,
                freeBytes = capacity.freeBytes,
                usedBytes = capacity.usedBytes,
                imagesBytes = images,
                videosBytes = videos,
                audioBytes = audio,
                documentsBytes = docs,
                archivesBytes = archives,
                apkBytes = apks,
                othersBytes = others
            )
        } catch (_: Exception) {
            StorageStats()
        }
    }

    private fun shouldSkipDir(name: String, depth: Int): Boolean =
        depth > 0 && name in SKIP_DIRS

    // =====================================================================================
    // Mutations
    // =====================================================================================

    /**
     * Drop all cached directory listings. Every mutation path must call this:
     * the UI re-lists immediately after an operation, and a stale cache entry
     * made deleted files linger on screen - the app's most confusing bug.
     */
    fun invalidateDirectoryCache() {
        directoryCache.evictAll()
    }

    private fun isValidItemName(name: String): Boolean =
        name.isNotEmpty() && name != "." && name != ".." &&
            name.none { it == '/' || it == '\\' || it == '\u0000' }

    /**
     * True when both paths point at the SAME file. Media filesystems are
     * case-insensitive, so "photo.jpg" and "Photo.jpg" resolve to one file and
     * a naive exists() check would reject a legitimate case-only rename.
     */
    private fun isSameFile(a: File, b: File): Boolean =
        try {
            a.canonicalPath.equals(b.canonicalPath, ignoreCase = true)
        } catch (_: Exception) {
            false
        }

    /**
     * Renames on disk, hopping through a temporary name when the direct rename
     * is refused (case-only changes: the destination "exists" because it is
     * the same file). The hop also makes the operation atomic-ish: if the
     * second step fails, the original name is restored instead of losing the
     * item to a half-applied rename.
     */
    private fun renameOnDisk(target: File, newFile: File): Boolean {
        if (target.renameTo(newFile)) return true
        val temp = File(newFile.parentFile, ".${newFile.name}.rename-tmp")
        if (!target.renameTo(temp)) return false
        return if (temp.renameTo(newFile)) true else {
            temp.renameTo(target)
            false
        }
    }

    suspend fun createDirectory(parentDir: File, folderName: String): Result<File> =
        withContext(Dispatchers.IO) {
            val cleanName = folderName.trim()
            try {
                val target = File(parentDir, cleanName)
                when {
                    !isValidItemName(cleanName) ->
                        Result.failure(InvalidNameException())
                    target.exists() ->
                        Result.failure(NameConflictException())
                    target.mkdirs() -> {
                        invalidateDirectoryCache()
                        Result.success(target)
                    }
                    else -> Result.failure(Exception("Failed to create folder"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun createNewFile(parentDir: File, fileName: String): Result<File> =
        withContext(Dispatchers.IO) {
            val cleanName = fileName.trim()
            try {
                val target = File(parentDir, cleanName)
                when {
                    !isValidItemName(cleanName) ->
                        Result.failure(InvalidNameException())
                    target.exists() ->
                        Result.failure(NameConflictException())
                    target.createNewFile() -> {
                        invalidateDirectoryCache()
                        Result.success(target)
                    }
                    else -> Result.failure(Exception("Failed to create file"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun rename(target: File, newName: String): Result<File> =
        withContext(Dispatchers.IO) {
            val cleanName = newName.trim()
            val parent = target.parentFile ?: return@withContext Result.failure(
                Exception("No parent directory")
            )
            try {
                when {
                    !isValidItemName(cleanName) ->
                        Result.failure(InvalidNameException())
                    // Confirming with the untouched name is a no-op, not an
                    // error: the dialog pre-fills the current name, so this is
                    // the common "opened it and pressed the button" case.
                    cleanName == target.name ->
                        Result.success(target)
                    else -> {
                        val newFile = File(parent, cleanName)
                        when {
                            newFile.exists() && !isSameFile(target, newFile) ->
                                Result.failure(NameConflictException())
                            renameOnDisk(target, newFile) -> {
                                invalidateDirectoryCache()
                                Result.success(newFile)
                            }
                            else -> Result.failure(Exception("Rename operation failed"))
                        }
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Raw entry names directly inside [dir] (hidden files included, no
     * sorting). The rename dialog snapshots these once at open time for live
     * conflict detection; the repository's own exists() check at rename time
     * remains the authority. A failed/unreadable listing yields an empty
     * list, which simply disables the live check.
     */
    suspend fun siblingNamesIn(dir: File): List<String> = withContext(Dispatchers.IO) {
        try {
            dir.list()?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun deleteFiles(files: List<File>): Boolean = withContext(Dispatchers.IO) {
        var allSuccess = true
        for (file in files) {
            if (file.isDirectory) {
                if (!file.deleteRecursively()) allSuccess = false
            } else {
                if (!file.delete()) allSuccess = false
            }
        }
        // Re-list from disk even after partial failures: the list must never
        // show ghosts, and missing invalidation caused exactly that.
        invalidateDirectoryCache()
        allSuccess
    }

    suspend fun copyFiles(sources: List<File>, destinationDir: File): Boolean =
        transfer(sources, destinationDir, move = false)

    suspend fun moveFiles(sources: List<File>, destinationDir: File): Boolean =
        transfer(sources, destinationDir, move = true)

    private suspend fun transfer(
        sources: List<File>,
        destinationDir: File,
        move: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Validate the entire batch before mutating anything. Copying a
            // folder into itself (including via an alias) recurses indefinitely.
            val destinationPath = destinationDir.canonicalFile.toPath()
            if (sources.any { source ->
                !source.exists() || (source.isDirectory &&
                    destinationPath.startsWith(source.canonicalFile.toPath()))
            }) return@withContext false
            if (!destinationDir.isDirectory && !destinationDir.mkdirs()) return@withContext false
            for (src in sources) {
                // A cut/paste into the same folder is a no-op, not a rename.
                if (move && src.parentFile?.canonicalFile == destinationDir.canonicalFile) continue
                val dest = getUniqueDestinationFile(destinationDir, src.name)
                if (move && src.renameTo(dest)) continue

                if (src.isDirectory) {
                    // copyRecursively signals failure by returning false (it
                    // does not throw on a partial copy): deleting the source
                    // after a failed copy would lose the user's data.
                    val copied = runCatching {
                        src.copyRecursively(dest, overwrite = false)
                    }.getOrDefault(false)
                    if (!copied) return@withContext false
                    if (move && !src.deleteRecursively()) return@withContext false
                } else {
                    src.copyTo(dest, overwrite = false, bufferSize = COPY_BUFFER)
                    if (move && !src.delete()) return@withContext false
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            // Earlier files in a batch may have succeeded even if a later one
            // failed. Never leave stale cached listings after partial changes.
            invalidateDirectoryCache()
        }
    }

    // =====================================================================================
    // Background metadata probes
    // =====================================================================================

    /**
     * Probe a video's duration with session-wide caching. Called from the
     * ViewModel's background enrichment (never from the hot listing path)
     * because a cold probe can take tens of milliseconds of native work.
     */
    suspend fun resolveVideoDuration(file: File, mtime: Long): Long? = withContext(Dispatchers.IO) {
        val key = "${file.absolutePath}:$mtime"
        DURATION_CACHE.get(key)?.let { return@withContext it }
        getVideoDuration(file)?.takeIf { it > 0 }?.also { DURATION_CACHE.put(key, it) }
    }

    private fun getVideoDuration(file: File): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLong()?.times(1000) // Convert ms to micros
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun isVideoExtension(extension: String): Boolean = extension in VIDEO_EXTENSIONS

    // =====================================================================================
    // Opening / sharing
    // =====================================================================================

    /** Opens file with chooser so user always sees the resolver.
     *  Only true APKs go through the system installer with unknown-sources
     *  handling; split/bundle formats (xapk/apks/aab) are zip-based and are
     *  offered to archive handlers via their MIME type below.
     *  Fallback path only; normal opening flows through [resolveCompatibleApps]. */
    fun openFile(file: File) {
        try {
            val ext = file.extension.lowercase()
            if (ext == "apk" && !ensureApkInstallPermissionAllowed()) return

            val mime = getMimeType(file)
            val uri = getFileUri(file)
            val baseIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                // Plain ACTION_VIEW lets the system open the user's DEFAULT app
                // and shows its once/always resolver when multiple apps qualify.
                context.startActivity(baseIntent)
            } catch (_: android.content.ActivityNotFoundException) {
                // No default for this type: fall back to a full chooser so every
                // capable app is offered to pick from once, per use.
                try {
                    context.startActivity(
                        Intent.createChooser(
                            baseIntent,
                            context.getString(com.baiel.expressivefiles.R.string.chooser_title)
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                } catch (_: Exception) {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(com.baiel.expressivefiles.R.string.apk_no_handler_toast),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Opens file with a system chooser every time so the user can pick any
     *  capable app, even when a default app for this type is already set. */
    fun openFileWithChooser(file: File) {
        try {
            if (file.extension.lowercase() == "apk" && !ensureApkInstallPermissionAllowed()) return

            val mime = getMimeType(file)
            val uri = getFileUri(file)
            val baseIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(
                    Intent.createChooser(
                        baseIntent,
                        context.getString(com.baiel.expressivefiles.R.string.chooser_title)
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (_: android.content.ActivityNotFoundException) {
                android.widget.Toast.makeText(
                    context,
                    context.getString(com.baiel.expressivefiles.R.string.apk_no_handler_toast),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * APK installation is blocked until "install unknown apps" is granted for
     * this source. Directs the user to the grant page exactly when needed.
     * @return true when installation may proceed.
     */
    private fun ensureApkInstallPermissionAllowed(): Boolean {
        val pm = context.packageManager
        if (pm.canRequestPackageInstalls()) return true
        return try {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = "package:${context.packageName}".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            android.widget.Toast.makeText(
                context, context.getString(com.baiel.expressivefiles.R.string.apk_unknown_sources_toast),
                android.widget.Toast.LENGTH_LONG
            ).show()
            false
        } catch (_: Exception) {
            false
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
            context.startActivity(Intent.createChooser(
                intent,
                context.getString(com.baiel.expressivefiles.R.string.chooser_title)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getFileUri(file: File): Uri {
        return try {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (_: Exception) {
            Uri.fromFile(file)
        }
    }

    /**
     * Precise per-extension MIME types. Accuracy matters for "Open with":
     * handler resolution filters on this value, so anything generic (a full
     * wildcard MIME) degrades to listing every installed app. Unmapped
     * binaries fall back to the full wildcard only as a last resort, after
     * the viewable categories below.
     */
    fun getMimeType(file: File): String {
        return when (val ext = file.extension.lowercase()) {
            // Images
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "bmp" -> "image/bmp"
            "heic", "heif" -> "image/heif"
            "avif" -> "image/avif"
            // Video
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "flv" -> "video/x-flv"
            "wmv" -> "video/x-ms-wmv"
            "3gp" -> "video/3gpp"
            "mpg", "mpeg" -> "video/mpeg"
            "mts", "m2ts" -> "video/mp2t"
            // Audio
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg", "opus" -> "audio/ogg"
            "wma" -> "audio/x-ms-wma"
            "m3u", "m3u8" -> "audio/x-mpegurl"
            // Documents
            "pdf" -> "application/pdf"
            "txt", "log", "md", "csv" -> "text/plain"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "epub" -> "application/epub+zip"
            "rtf" -> "application/rtf"
            // Structured / markup / source code: text editors handle all of these
            "json" -> "application/json"
            "xml", "html", "htm" -> "text/html"
            "kt", "kts", "java", "py", "js", "mjs", "ts", "css", "sh", "rs", "go",
            "sql", "yaml", "yml", "toml", "ini", "properties", "gradle", "bat",
            "c", "cpp", "h", "hpp", "cs", "php", "rb", "swift", "dart",
            "asm", "s" -> "text/plain"
            // Archives
            "zip", "jar", "cbz" -> "application/zip"
            "7z", "cb7" -> "application/x-7z-compressed"
            "rar", "cbr" -> "application/vnd.rar"
            "tar" -> "application/x-tar"
            "gz", "tgz" -> "application/gzip"
            "bz2", "tbz2" -> "application/x-bzip2"
            "xz" -> "application/x-xz"
            "zst" -> "application/zstd"
            "iso" -> "application/x-iso9660-image"
            // Installers: only plain APKs install via the package installer.
            // Split/bundle formats are zip containers - hand them to archive
            // handlers instead of failing in the installer.
            "apk" -> "application/vnd.android.package-archive"
            "xapk", "apks", "aab" -> "application/zip"
            else -> if (isVideoExtension(ext)) "video/*" else "*/*"
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
