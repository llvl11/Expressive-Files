package com.baiel.expressivefiles.viewmodel

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baiel.expressivefiles.ArchiveActionReceiver
import com.baiel.expressivefiles.R
import com.baiel.expressivefiles.archive.ArchiveEngine
import com.baiel.expressivefiles.data.FileManagerRepository
import com.baiel.expressivefiles.data.InvalidNameException
import com.baiel.expressivefiles.data.NameConflictException
import com.baiel.expressivefiles.data.SettingsRepository
import com.baiel.expressivefiles.model.ArchiveProgress
import com.baiel.expressivefiles.model.ArchiveType
import com.baiel.expressivefiles.model.AppColorPalette
import com.baiel.expressivefiles.model.AppSettings
import com.baiel.expressivefiles.model.AppThemeMode
import com.baiel.expressivefiles.model.ClipboardAction
import com.baiel.expressivefiles.model.ClipboardState
import com.baiel.expressivefiles.model.FileItem
import com.baiel.expressivefiles.model.FileType
import com.baiel.expressivefiles.model.label
import com.baiel.expressivefiles.model.SortMode
import com.baiel.expressivefiles.model.SortOrder
import com.baiel.expressivefiles.model.StorageStats
import com.baiel.expressivefiles.model.ViewMode
import com.baiel.expressivefiles.ui.components.DialogActionType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * Reason a create/rename name was rejected, resolved to a localized message by
 * the dialog. EMPTY is checked before dispatch; the rest map the repository's
 * typed failures.
 */
enum class NameError { EMPTY, INVALID, EXISTS, FAILED }

/**
 * Single source of truth for every piece of app state that survives screen
 * navigation. UI screens may only read StateFlows and call intent-style
 * methods - they never reach inside to poke mutable state directly, which
 * eliminated a whole class of ordering bugs (dialog ghosts, stale deletes).
 */
/**
 * The engine (no Android context) emits fixed English operation anchors while
 * streaming; map them so the progress popup and the notification always speak
 * the UI language. Unknown/already-localized strings pass through untouched.
 */
internal fun localizedProgressOperation(context: android.content.Context, raw: String): String {
    // "Extraction"/"Compression" already cover their " ... Failed" siblings,
    // so no separate failure-prefix arm is needed (a "Compression F" check
    // here was unreachable dead code).
    if (!raw.startsWith("Extracting") && !raw.startsWith("Compression") &&
        !raw.startsWith("Creating") && !raw.startsWith("Extraction")
    ) {
        return raw
    }
    val res = context.resources
    return when (raw.lowercase()) {
        "extracting zip" -> res.getString(R.string.op_extracting_zip)
        "extracting 7z" -> res.getString(R.string.op_extracting_7z)
        "extracting tar" -> res.getString(R.string.op_extracting_tar)
        "extracting rar" -> res.getString(R.string.op_extracting_rar)
        "extracting archive" -> res.getString(R.string.op_extracting_archive)
        "extraction complete" -> res.getString(R.string.op_extraction_complete)
        "compression complete" -> res.getString(R.string.op_compression_complete)
        // The compress branch emits "Creating ZIP/7Z/TAR/TAR.GZ" and the failure
        // anchors arrive with no localizable counterpart at all - without these
        // arms the popup and the notification kept showing English mid-operation.
        "creating zip" -> res.getString(R.string.op_creating, "ZIP")
        "creating 7z" -> res.getString(R.string.op_creating, "7Z")
        "creating tar" -> res.getString(R.string.op_creating, "TAR")
        "creating tar.gz" -> res.getString(R.string.op_creating, "TAR.GZ")
        "extraction failed" -> res.getString(R.string.op_extraction_failed)
        "compression failed" -> res.getString(R.string.op_compression_failed)
        else -> raw
    }
}

/**
 * The engine's two fixed fallback errors are English-only constants we own;
 * map them to localized text. Exception messages (localizedMessage) pass
 * through untouched - they are third-party text we cannot translate.
 */
internal fun localizedArchiveError(context: android.content.Context, raw: String): String =
    when (raw) {
        "Unknown error while extracting",
        "Unknown compression error" -> context.getString(R.string.archive_error_unknown)
        else -> raw
    }

class FileViewModel(application: Application) : AndroidViewModel(application) {

    companion object ArchiveSession {
        const val NOTIF_ID = 4242
        const val CHANNEL_ID = "archive_operations"

        /**
         * Job of the currently running archive operation. Exposed statically
         * so the notification's Cancel action (a BroadcastReceiver, which has
         * no access to the ViewModel instance) can interrupt the coroutine;
         * the unwind handler inside the coroutine then performs the usual
         * toast + state cleanup.
         */
        @Volatile
        private var activeArchiveJob: Job? = null

        fun cancelActiveArchive() {
            activeArchiveJob?.cancel()
            activeArchiveJob = null
        }
    }

    private val repository = FileManagerRepository(application)
    private val settingsRepository = SettingsRepository(application)

    // ----- Settings ------------------------------------------------------
    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(
            viewModelScope, SharingStarted.Eagerly, settingsRepository.settings.value
        )

    // ----- Location stack -------------------------------------------------
    val rootDirectory: File = Environment.getExternalStorageDirectory()

    private val _currentDirectory = MutableStateFlow(rootDirectory)
    val currentDirectory: StateFlow<File> = _currentDirectory.asStateFlow()

    private val directoryBackstack = mutableListOf<File>()

    /**
     * True while [navigateBack] would consume the press internally because the
     * stack still holds ancestors to return to. The UI's "root state" decision
     * must factor this in: after a breadcrumb jump back to the root the current
     * directory IS the root while folders remain queued behind it - treating
     * that as "nowhere to go" showed the exit toast and finished the activity.
     */
    val hasBackStack: Boolean
        get() = directoryBackstack.isNotEmpty()

    // ----- Listings -------------------------------------------------------
    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private var directoryLoadJob: Job? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<FileType?>(null)
    val selectedCategory: StateFlow<FileType?> = _selectedCategory.asStateFlow()

    // Per-category sort choices ("for each filter"): NAME/SIZE/DATE x ASC/DESC,
    // remembered across sessions. Falls back to the global sort until changed.
    private val _categorySorts = MutableStateFlow<Map<String, Pair<SortMode, SortOrder>>>(emptyMap())
    val categorySorts: StateFlow<Map<String, Pair<SortMode, SortOrder>>> =
        _categorySorts.asStateFlow()

    /** The category whose drill-down screen is currently visible (if any). */
    private var activeCategoryDrill: FileType? = null

    // Background duration fill-in (see resolveVideoDuration docs).
    private var videoMetaJob: Job? = null
    private var categoryVideoMetaJob: Job? = null
    private var renameSiblingsJob: Job? = null

    // ----- Selection ------------------------------------------------------
    private val _selectedPaths = MutableStateFlow<Set<String>>(emptySet())
    val selectedPaths: StateFlow<Set<String>> = _selectedPaths.asStateFlow()

    val isSelectionMode: Boolean
        get() = _selectedPaths.value.isNotEmpty()

    private val _clipboard = MutableStateFlow<ClipboardState?>(null)
    val clipboard: StateFlow<ClipboardState?> = _clipboard.asStateFlow()

    // ----- Storage stats ---------------------------------------------------
    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> =
        _storageStats.asStateFlow()
    private var storageStatsJob: Job? = null

    // ----- Category listing (Analysis drill-downs) -------------------------
    private val _categoryFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val categoryFiles: StateFlow<List<FileItem>> = _categoryFiles.asStateFlow()

    // Which directory the [_files] listing actually belongs to. Lets the UI
    // tell a same-folder refresh (stale rows are fine to keep) apart from a
    // folder switch (foreign rows must never be shown).
    private val _filesDirectory = MutableStateFlow<File?>(null)
    val filesDirectory: StateFlow<File?> = _filesDirectory.asStateFlow()

    // True when the listing was cleared because the folder could not be READ
    // (mounted volume without permission, probe failed) - DirectoryPage then
    // says "no access" instead of the misleading "Folder is empty".
    private val _unreadableFolder = MutableStateFlow(false)
    val unreadableFolder: StateFlow<Boolean> = _unreadableFolder.asStateFlow()

    private val _isCategoryLoading = MutableStateFlow(false)
    val isCategoryLoading: StateFlow<Boolean> = _isCategoryLoading.asStateFlow()
    private var categoryLoadJob: Job? = null

    // ----- Overlay/dialog state (READ from UI, mutated via intents only) ---
    private val _showCreateArchiveDialog = MutableStateFlow(false)
    val showCreateArchiveDialog: StateFlow<Boolean> = _showCreateArchiveDialog.asStateFlow()

    private val _showNewItemDialog = MutableStateFlow<DialogActionType?>(null)
    val showNewItemDialog: StateFlow<DialogActionType?> = _showNewItemDialog.asStateFlow()

    private val _activeActionSheetItem = MutableStateFlow<FileItem?>(null)
    val activeActionSheetItem: StateFlow<FileItem?> = _activeActionSheetItem.asStateFlow()

    private val _activeArchiveInspectItem = MutableStateFlow<FileItem?>(null)
    val activeArchiveInspectItem: StateFlow<FileItem?> = _activeArchiveInspectItem.asStateFlow()

    private val _renameTargetItem = MutableStateFlow<FileItem?>(null)
    val renameTargetItem: StateFlow<FileItem?> = _renameTargetItem.asStateFlow()

    /**
     * Lowercased names of the rename target's directory siblings (the target
     * itself excluded), snapshotted once when the dialog opens. Powers the
     * dialog's live conflict detection; empty until the background listing
     * lands, in which case the repository's own check still guards the confirm.
     */
    private val _renameSiblingNames = MutableStateFlow<Set<String>>(emptySet())
    val renameSiblingNames: StateFlow<Set<String>> = _renameSiblingNames.asStateFlow()

    private val _pendingDeleteItems = MutableStateFlow<List<FileItem>>(emptyList())
    val pendingDeleteItems: StateFlow<List<FileItem>> = _pendingDeleteItems.asStateFlow()

    /**
     * Why the last create/rename attempt was rejected (null = clean). The
     * dialog keeps its text, shows the matching message and stays open, so a
     * rejected name is never a silent no-op.
     */
    private val _nameError = MutableStateFlow<NameError?>(null)
    val nameError: StateFlow<NameError?> = _nameError.asStateFlow()

    // ----- Archive progress -------------------------------------------------
    private val _archiveProgress = MutableStateFlow<ArchiveProgress?>(null)
    val archiveProgress: StateFlow<ArchiveProgress?> = _archiveProgress.asStateFlow()

    /** True while the blocking-ish operation popup should be on screen. */
    private val _showArchiveDialog = MutableStateFlow(false)
    val showArchiveDialog: StateFlow<Boolean> = _showArchiveDialog.asStateFlow()

    /** Handle used by the Cancel button to interrupt the running operation. */
    private var archiveJob: Job? = null

    // ----- Permission --------------------------------------------------------
    private val _hasStoragePermission = MutableStateFlow(checkStoragePermission())
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    init {
        _categorySorts.value = settingsRepository.getCategorySorts()
        loadCurrentDirectory()
        loadStorageStats()
    }

    fun categorySortFor(category: FileType?): Pair<SortMode, SortOrder> {
        val fallback = settings.value.sortMode to settings.value.sortOrder
        return category?.let { _categorySorts.value[it.name] } ?: fallback
    }

    /**
     * Sets the ordering for one category filter and refreshes whichever
     * surface is showing it (home chips listing and/or the analysis drill-down).
     */
    fun setCategorySort(category: FileType, mode: SortMode, order: SortOrder) {
        val next = _categorySorts.value.toMutableMap()
        next[category.name] = mode to order
        _categorySorts.value = next
        settingsRepository.saveCategorySorts(next)

        if (_selectedCategory.value == category) loadCurrentDirectory()
        if (activeCategoryDrill == category) loadCategoryFiles(category)
    }

    fun markCategoryDrillActive(category: FileType?) {
        activeCategoryDrill = category
    }

    fun checkStoragePermission(): Boolean {
        // The platform probe indexes the mounted-volume list and can throw when
        // there is no managed volume at all (test harnesses, some ROMs). A
        // permission question must degrade to "not granted", never crash the
        // ViewModel - the initial state is built from this call.
        return try {
            Environment.isExternalStorageManager()
        } catch (_: Exception) {
            false
        }
    }

    fun refreshPermission() {
        _hasStoragePermission.value = checkStoragePermission()
        // onResume lands here: files may have changed while the app was
        // backgrounded (and permission may have been revoked in Settings), so
        // reuse refreshCurrentDirectory to drop the session cache instead of
        // serving the listing captured before the app was backgrounded.
        refreshCurrentDirectory()
    }

    /** Re-reads the current folder from disk, ignoring the session cache. */
    fun refreshCurrentDirectory() {
        repository.invalidateDirectoryCache()
        loadCurrentDirectory()
        loadStorageStats(deepScan = false)
    }

    // ======================================================================
    // Navigation
    // ======================================================================

    fun navigateToDirectory(directory: File) {
        if (!directory.exists() || !directory.isDirectory) return
        if (directory.absolutePath == _currentDirectory.value.absolutePath) return
        // A breadcrumb jump must rebuild the stack as the target's ANCESTOR
        // chain. Blindly pushing the current folder (old behavior) left the
        // descendants of the target in the stack, so Back re-entered them
        // instead of going up - every subsequent back press was off by one.
        // Rebuilt UNCONDITIONALLY: the chain is correct for any target, and a
        // jump onto another volume (chain ending at "/" instead of the app
        // root) walks up to "/" - the old else-branch pushed the CURRENT
        // folder there, so an upward jump put its own descendant on the stack.
        val chain = ArrayList<File>()
        var cursor: File? = directory
        while (cursor != null) {
            chain.add(cursor)
            if (cursor.absolutePath == rootDirectory.absolutePath) break
            cursor = cursor.parentFile
        }
        directoryBackstack.clear()
        for (i in chain.lastIndex downTo 1) directoryBackstack.add(chain[i])
        _currentDirectory.value = directory
        resetTransientFilters()
        loadCurrentDirectory()
    }

    /** Returns true when back was consumed internally (screen stays). */
    fun navigateBack(): Boolean {
        if (isSelectionMode) {
            clearSelection()
            return true
        }
        if (_searchQuery.value.isNotEmpty()) {
            _searchQuery.value = ""
            loadCurrentDirectory()
            return true
        }
        if (_selectedCategory.value != null) {
            _selectedCategory.value = null
            loadCurrentDirectory()
            return true
        }
        directoryBackstack.removeLastOrNull()?.let { previous ->
            _currentDirectory.value = previous
            resetTransientFilters()
            loadCurrentDirectory()
            return true
        }
        // Leave root-adjacent directories while a readable parent exists.
        val parent = _currentDirectory.value.parentFile ?: return false
        if (!parent.canRead()) return false
        if (_currentDirectory.value.absolutePath == rootDirectory.absolutePath) return false
        _currentDirectory.value = parent
        resetTransientFilters()
        loadCurrentDirectory()
        return true
    }

    private fun resetTransientFilters() {
        clearSelection()
        _selectedCategory.value = null
        _searchQuery.value = ""
    }

    // ======================================================================
    // Listing pipeline
    // ======================================================================

    fun loadCurrentDirectory() {
        directoryLoadJob?.cancel()
        directoryLoadJob = viewModelScope.launch {
            val currentSettings = settings.value
            val category = _selectedCategory.value
            val query = _searchQuery.value
            val directory = _currentDirectory.value
            // Ownership token: a newer load supersedes this one, and only the
            // owner may publish results or clear the loading flag. Without it
            // a canceled (but blocked in file I/O) load would stomp on the
            // state of the load that replaced it during fast interactions.
            val myJob = coroutineContext[Job]

            if (query.isNotBlank()) delay(200.milliseconds)
            _isLoading.value = true
            try {
                // Permission-gated roots can transiently report unreadable while
                // MediaProvider warms up right after a grant; emptying the list
                // on first probe made HOME render "empty" even though content
                // exists. Retry briefly, then accept reality once.
                // Probes run on Dispatchers.IO: exists()/canRead() are stat syscalls
                // that can block on a cold or remote volume, and the first one ran
                // on the main thread before any suspension point.
                suspend fun probeReady(): Boolean =
                    withContext(Dispatchers.IO) { directory.exists() && directory.canRead() }
                var ready = probeReady()
                var retries = 0
                while (!ready && retries < 3 && checkStoragePermission()) {
                    retries++
                    delay(250.milliseconds)
                    ready = probeReady()
                }
                if (!ready) {
                    // Same treatment as the catch branch below: a folder SWITCH
                    // falls back to the empty state for the new folder, and
                    // filesDirectory MUST follow it - FileListContent pins the
                    // spinner forever while the mounted listing belongs to
                    // another folder (DirectoryPage's stale-folder branch runs
                    // before the empty state). A same-folder refresh keeps
                    // whatever healthy list is on screen instead of wiping it.
                    if (directoryLoadJob === myJob &&
                        _filesDirectory.value?.absolutePath != directory.absolutePath
                    ) {
                        _files.value = emptyList()
                        _filesDirectory.value = directory
                        _unreadableFolder.value = true
                    }
                    // The probes above suspend, so the ownership check in `finally`
                    // covers this path now; clearing here as well keeps the flag
                    // honest even if a future change removes that suspension.
                    _isLoading.value = false
                    return@launch
                }

                val loadedFiles = repository.run {
                    when {
                        query.isNotBlank() -> searchFiles(
                            directory, query, recursive = true,
                            showHidden = currentSettings.showHiddenFiles,
                            sortMode = currentSettings.sortMode,
                            sortOrder = currentSettings.sortOrder
                        )
                        category != null -> categorySortFor(category).let { (mode, order) ->
                            getFilesByCategory(
                                category, directory,
                                showHidden = currentSettings.showHiddenFiles,
                                sortMode = mode,
                                sortOrder = order
                            )
                        }
                        else -> listFiles(
                            directory,
                            showHidden = currentSettings.showHiddenFiles,
                            sortMode = currentSettings.sortMode,
                            sortOrder = currentSettings.sortOrder
                        )
                    }
                }

                // Ignore results for a location the user has already left, or
                // for a filter that has since been switched away from.
                if (directoryLoadJob !== myJob) return@launch
                if (directory.absolutePath != _currentDirectory.value.absolutePath) return@launch

                _files.value = loadedFiles
                _filesDirectory.value = directory
                _unreadableFolder.value = false
                pruneSelection()
                startVideoDurationEnrichment(_files, loadedFiles)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Transient I/O failure: a same-folder refresh keeps whatever
                // is on screen rather than wiping it blank; a folder switch
                // falls back to the empty state for the new folder.
                if (directoryLoadJob === myJob &&
                    _filesDirectory.value?.absolutePath != directory.absolutePath
                ) {
                    _files.value = emptyList()
                    _filesDirectory.value = directory
                    _unreadableFolder.value = true
                }
            } finally {
                // A canceled superseded load must not clear the flag of the
                // load that replaced it.
                if (directoryLoadJob === myJob) _isLoading.value = false
            }
        }
    }

    /**
     * Fill in video durations one-by-one in the background. The listing stays
     * instant (the stall fix); each resolved duration updates items in place -
     * positions are stable because sorting never depends on duration.
     */
    private fun startVideoDurationEnrichment(
        target: MutableStateFlow<List<FileItem>>,
        items: List<FileItem>
    ) {
        // Cancel BEFORE the early return: on a refresh where every duration is
        // already known `pending` is empty and the old code returned first,
        // leaving the previous job alive to keep rewriting the old listing.
        val isCategoryTarget = target === _categoryFiles
        (if (isCategoryTarget) categoryVideoMetaJob else videoMetaJob)?.cancel()
        if (isCategoryTarget) categoryVideoMetaJob = null else videoMetaJob = null

        val pending = items.filter {
            !it.isDirectory && it.fileType == FileType.VIDEO && it.duration == null
        }
        if (pending.isEmpty()) return

        // One slot per surface: _files (the directory listing) and
        // _categoryFiles (the drill list) are independent, so a single shared
        // field meant opening a category cancelled the files listing's
        // enrichment mid-way - durations silently stayed null there.
        val job = viewModelScope.launch {
            for (video in pending) {
                ensureActive()
                val duration = repository.resolveVideoDuration(video.file, video.lastModified ?: 0L) ?: continue
                val current = target.value
                if (current.any { it.path == video.path && it.duration == null }) {
                    target.value = current.map {
                        if (it.path == video.path && it.duration == null) it.copy(duration = duration) else it
                    }
                }
            }
        }
        if (isCategoryTarget) categoryVideoMetaJob = job else videoMetaJob = job
    }

    fun setSearchQuery(query: String) {
        if (query == _searchQuery.value) return
        // The listing is about to be replaced: a selection carried across would
        // point at rows that are no longer on screen, so Copy/Cut/Delete would
        // silently resolve to nothing (or to the wrong set).
        clearSelection()
        _searchQuery.value = query
        loadCurrentDirectory()
    }

    fun setCategoryFilter(category: FileType?) {
        if (category == _selectedCategory.value) return
        clearSelection()
        _selectedCategory.value = category
        loadCurrentDirectory()
    }

    fun loadStorageStats(deepScan: Boolean = false) {
        if (deepScan) {
            storageStatsJob?.cancel()
            storageStatsJob = viewModelScope.launch {
                // The repository rethrows scan failures now: swallow them here
                // (never CancellationException) so a transient I/O error keeps
                // the numbers already on screen instead of crashing the app or
                // overwriting a real breakdown with an empty model.
                try {
                    _storageStats.value = repository.getStorageStats()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
            return
        }
        // Cheap capacity probe that PRESERVES the already-measured
        // per-category breakdown. Replacing the whole model with a fresh
        // capacity-only one zeroed every size whenever the app was resumed
        // while sitting on the Storage Analysis screen.
        //
        // It must also never CANCEL a running deep scan: refreshPermission()
        // (every onResume) and every mutation call this overload, and killing
        // the deep scan mid-walk left the category rows at 0 B until the user
        // left and re-entered the screen.
        if (storageStatsJob?.isActive == true) return
        storageStatsJob = viewModelScope.launch {
            try {
                val cap = repository.getStorageCapacity()
                _storageStats.value = _storageStats.value.copy(
                    totalBytes = cap.totalBytes,
                    freeBytes = cap.freeBytes,
                    usedBytes = cap.usedBytes
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A SecurityException/IO failure while probing capacity must not
                // kill the coroutine (viewModelScope rethrows uncaught failures).
            }
        }
    }

    fun loadCategoryFiles(category: FileType) {
        activeCategoryDrill = category
        categoryLoadJob?.cancel()
        categoryLoadJob = viewModelScope.launch {
            val myJob = coroutineContext[Job]
            _isCategoryLoading.value = true
            try {
                val currentSettings = settings.value
                val (sortMode, sortOrder) = categorySortFor(category)
                val loaded = repository.getFilesByCategory(
                    category, rootDirectory,
                    showHidden = currentSettings.showHiddenFiles,
                    sortMode = sortMode,
                    sortOrder = sortOrder
                )
                _categoryFiles.value = loaded
                pruneSelection()
                startVideoDurationEnrichment(_categoryFiles, loaded)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // An I/O/Security failure while walking the tree must not kill
                // the coroutine (viewModelScope rethrows uncaught failures) nor
                // leave the drill spinner up forever - the finally below clears it.
            } finally {
                // A superseded load must not clear the flag of the one that
                // replaced it (same ownership rule as loadCurrentDirectory).
                if (categoryLoadJob === myJob) _isCategoryLoading.value = false
            }
        }
    }

    // ======================================================================
    // Selection & clipboard
    // ======================================================================

    fun toggleSelection(item: FileItem) {
        val current = _selectedPaths.value.toMutableSet()
        if (!current.add(item.path)) current.remove(item.path)
        if (current.isEmpty()) _selectedPaths.value = emptySet()
        else _selectedPaths.value = current
    }

    fun selectAll() {
        // Always the listing currently on screen: `_files` holds the home
        // directory view INCLUDING any active category chip (the chip reloads
        // `_files`). `_categoryFiles` is the Storage-Analysis drill list - a
        // different surface - and reading it here made "Select all" either
        // clear the selection (drill never opened) or select up to 1000 files
        // the user cannot see (stale drill data), which bulk actions then
        // operated on.
        val candidatePaths = _files.value.mapTo(mutableSetOf()) { it.path }
        // Toggle: when everything already selected, clear the whole selection.
        _selectedPaths.value =
            if (candidatePaths.isNotEmpty() && _selectedPaths.value.containsAll(candidatePaths)) {
                emptySet()
            } else {
                candidatePaths
            }
    }

    /** True while every listed item on the home surface is selected. */
    val isAllSelected: StateFlow<Boolean> = combine(
        _files,
        _selectedPaths
    ) { files, selected ->
        files.isNotEmpty() && selected.containsAll(files.mapTo(mutableSetOf()) { it.path })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun clearSelection() {
        _selectedPaths.value = emptySet()
    }

    private fun resolveSelection(): List<FileItem> {
        val paths = _selectedPaths.value
        if (paths.isEmpty()) return emptyList()
        return (_files.value + _categoryFiles.value)
            .filter { paths.contains(it.path) }
            .distinctBy { it.path }
    }

    /**
     * Drops selected paths that vanished from BOTH listing surfaces (home and
     * the category drill share one selection set - see [resolveSelection]).
     * Called whenever a listing is replaced: hiding hidden files, a pull-to-
     * refresh or a permission re-grant used to leave "N selected" pointing at
     * rows that are no longer listed, so Copy/Cut/Delete silently acted on a
     * smaller subset with no feedback.
     */
    private fun pruneSelection() {
        val paths = _selectedPaths.value
        if (paths.isEmpty()) return
        val present = (_files.value + _categoryFiles.value).mapTo(HashSet()) { it.path }
        _selectedPaths.value = paths.filterTo(mutableSetOf()) { it in present }
    }

    fun copySelected() {
        val selected = resolveSelection()
        if (selected.isNotEmpty()) {
            _clipboard.value = ClipboardState(selected, ClipboardAction.COPY)
            clearSelection()
        }
    }

    fun cutSelected() {
        val selected = resolveSelection()
        if (selected.isNotEmpty()) {
            _clipboard.value = ClipboardState(selected, ClipboardAction.CUT)
            clearSelection()
        }
    }

    fun clearClipboard() {
        _clipboard.value = null
    }

    /** Single-use paste: clipboard is consumed up-front so repeat taps can
     *  never re-run the operation against an already-updated folder. */
    fun pasteToCurrentDirectory() {
        val state = _clipboard.value ?: return
        // Consume the clipboard up front as the double-tap guard; a FAILED
        // paste puts it back (below) so the user's cut/copy selection is not
        // thrown away together with the operation that did not happen.
        _clipboard.value = null

        val app = getApplication<Application>()
        val count = state.items.size

        viewModelScope.launch {
            val moved = if (state.action == ClipboardAction.COPY) {
                repository.copyFiles(state.items.map { it.file }, _currentDirectory.value)
            } else {
                repository.moveFiles(state.items.map { it.file }, _currentDirectory.value)
            }
            if (moved) {
                android.widget.Toast.makeText(
                    app, app.resources.getQuantityString(R.plurals.toast_pasted_n, count, count), android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                _clipboard.value = state
                android.widget.Toast.makeText(
                    app, app.getString(R.string.toast_paste_failed), android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            reloadSurfaces()
        }
    }

    // ======================================================================
    // Mutations
    // ======================================================================

    fun createFolder(name: String) {
        if (name.isBlank()) {
            _nameError.value = NameError.EMPTY
            return
        }
        viewModelScope.launch {
            repository.createDirectory(_currentDirectory.value, name)
                .onSuccess {
                    _nameError.value = null
                    _showNewItemDialog.value = null
                    reloadSurfaces()
                }
                .onFailure { _nameError.value = it.toNameError() }
        }
    }

    fun createNewFile(name: String) {
        if (name.isBlank()) {
            _nameError.value = NameError.EMPTY
            return
        }
        viewModelScope.launch {
            repository.createNewFile(_currentDirectory.value, name)
                .onSuccess {
                    _nameError.value = null
                    _showNewItemDialog.value = null
                    reloadSurfaces()
                }
                .onFailure { _nameError.value = it.toNameError() }
        }
    }

    /**
     * Renames [item] to [newName]. On success the dialog closes and the
     * listing reloads; on rejection it stays open with the reason visible
     * (see [nameError]) so the user can correct the name in place.
     */
    fun renameFile(item: FileItem, newName: String) {
        if (newName.isBlank()) {
            _nameError.value = NameError.EMPTY
            return
        }
        viewModelScope.launch {
            repository.rename(item.file, newName)
                .onSuccess {
                    _nameError.value = null
                    _renameTargetItem.value = null
                    reloadSurfaces()
                }
                .onFailure { _nameError.value = it.toNameError() }
        }
    }

    /** Clears the dialog's name error (called on edit and when it reopens). */
    fun clearNameError() {
        _nameError.value = null
    }

    private fun Throwable.toNameError(): NameError = when (this) {
        is InvalidNameException -> NameError.INVALID
        is NameConflictException -> NameError.EXISTS
        else -> NameError.FAILED
    }

    fun deleteSelectedFiles() {
        val selected = resolveSelection()
        if (selected.isEmpty()) return
        deleteConfirmed(selected)
    }

    fun deleteSingleFile(item: FileItem) {
        deleteConfirmed(listOf(item))
    }

    private fun deleteConfirmed(items: List<FileItem>) {
        _pendingDeleteItems.value = items
        _showNewItemDialog.value = DialogActionType.DELETE_CONFIRM
    }

    fun confirmDelete() {
        val items = _pendingDeleteItems.value
        if (items.isEmpty()) return
        // Buffered confirm: close the dialog and release its state immediately
        // so the UI reacts at once; the (possibly slow) deletion then runs in
        // the background and the listing refreshes when it completes.
        _pendingDeleteItems.value = emptyList()
        _showNewItemDialog.value = null
        viewModelScope.launch {
            val ok = repository.deleteFiles(items.map { it.file })
            clearSelection()
            if (!ok) {
                // Previously silent: a partial/failed delete left the user
                // guessing why some rows were still there.
                android.widget.Toast.makeText(
                    getApplication(), R.string.toast_delete_failed, android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            reloadSurfaces()
        }
    }

    // ======================================================================
    // Opening / sharing
    // ======================================================================

    fun openFile(item: FileItem) {
        when {
            item.isDirectory -> navigateToDirectory(item.file)
            // Only formats the engine can actually READ get the built-in
            // inspector: gz/iso/zst/... are classified ARCHIVE for the
            // category filter but resolve to ArchiveType.OTHER, which the
            // inspector used to render as a fake "0 entries" archive whose
            // Extract then failed. Everything else goes to the OS resolver,
            // which offers ONLY handlers matching this format (repository
            // maps precise MIME types) with the native "Just once / Always"
            // choice, remembered by the system per type.
            item.fileType == FileType.ARCHIVE &&
                item.archiveType != null &&
                item.archiveType != ArchiveType.OTHER -> _activeArchiveInspectItem.value = item
            else -> repository.openFile(item.file)
        }
    }

    fun shareFile(item: FileItem) {
        repository.shareFiles(listOf(item.file))
    }

    /** Always presents the system app chooser so the user can open the file
     *  in any capable app, bypassing the remembered default handler. */
    fun openFileWithExternalApp(item: FileItem) {
        repository.openFileWithChooser(item.file)
    }

    fun shareSelectedFiles() {
        val selected = resolveSelection()
        if (selected.isNotEmpty()) repository.shareFiles(selected.map { it.file })
    }

    // ======================================================================
    // Dialog intents (replaces direct StateFlow poking from the UI layer)
    // ======================================================================

    fun showActionSheet(item: FileItem) {
        _activeActionSheetItem.value = item
    }

    fun closeActionSheet() {
        _activeActionSheetItem.value = null
    }

    fun showArchiveInspector(item: FileItem) {
        // Defensive twin of the gate in openFile: ArchiveType.OTHER (.gz/.xz/
        // .iso/...) cannot be listed - the engine falls through to its ZIP
        // reader and throws, which the sheet renders as "corrupt archive" for
        // a perfectly valid file. Route those to the OS resolver instead.
        if (item.fileType == FileType.ARCHIVE &&
            item.archiveType != null &&
            item.archiveType != ArchiveType.OTHER
        ) {
            _activeArchiveInspectItem.value = item
        } else {
            openFile(item)
        }
    }

    fun closeArchiveInspector() {
        _activeArchiveInspectItem.value = null
    }

    fun startRename(item: FileItem) {
        _nameError.value = null
        _renameTargetItem.value = item
        // Snapshot the parent's entries once (hidden included, lowercased,
        // target excluded) so the dialog can flag conflicts live. The target
        // is matched by EXACT name, not case-insensitively: on case-sensitive
        // filesystems a sibling that only differs in case from the target is
        // still a real conflict for a case-insensitive rename.
        _renameSiblingNames.value = emptySet()
        // A previously launched snapshot must not win the race: it would
        // overwrite the new target's siblings with the old folder's list and
        // make the dialog accept/reject conflicts against the wrong directory.
        renameSiblingsJob?.cancel()
        renameSiblingsJob = viewModelScope.launch {
            val parent = item.file.parentFile ?: return@launch
            _renameSiblingNames.value = repository.siblingNamesIn(parent)
                .filter { it != item.name }
                .mapTo(HashSet()) { it.lowercase() }
        }
    }

    fun closeRename() {
        renameSiblingsJob?.cancel()
        renameSiblingsJob = null
        _nameError.value = null
        _renameTargetItem.value = null
        _renameSiblingNames.value = emptySet()
    }

    fun requestCreateArchive() {
        // A stale/empty selection would open a dialog whose Compress button can
        // never succeed (and, before validation existed, could never close).
        if (resolveSelection().isEmpty()) return
        _nameError.value = null
        _showCreateArchiveDialog.value = true
    }

    fun dismissCreateArchive() {
        _nameError.value = null
        _showCreateArchiveDialog.value = false
    }

    /** Opens the New Folder/File dialog; DELETE_CONFIRM goes through [deleteConfirmed]. */
    fun requestNewItem(type: DialogActionType) {
        when (type) {
            DialogActionType.NEW_FOLDER, DialogActionType.NEW_FILE -> {
                _nameError.value = null
                _showNewItemDialog.value = type
            }
            else -> {}
        }
    }

    fun dismissNewItem() {
        _nameError.value = null
        _showNewItemDialog.value = null
    }

    // ======================================================================
    // Archives
    // ======================================================================

    /**
     * Reload every surface a mutation could have touched: the home listing,
     * the Storage-Analysis drill list (when it is the active surface) and the
     * storage figures. Archive operations write through [ArchiveEngine], which
     * bypasses the repository's own mutation paths and therefore never calls
     * [FileManagerRepository.invalidateDirectoryCache] - without the explicit
     * drop here, a freshly created archive / extracted folder stays invisible
     * until pull-to-refresh.
     */
    private fun reloadSurfaces() {
        repository.invalidateDirectoryCache()
        loadCurrentDirectory()
        activeCategoryDrill?.let { loadCategoryFiles(it) }
        loadStorageStats()
    }

    fun createArchive(name: String, format: ArchiveType) {
        // Single-flight: a second job would overwrite archiveJob/activeArchiveJob,
        // so the RUNNING job's Cancel would act on null and never fire - and its
        // completion would tear down the second job's popup/progress.
        if (archiveJob?.isActive == true) {
            _showCreateArchiveDialog.value = false
            android.widget.Toast.makeText(
                getApplication(), R.string.toast_archive_busy, android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        val selected = resolveSelection()
        // The dialog chips the selected format's extension onto whatever the
        // user typed ("bundle" + ZIP -> "bundle.zip"): strip a typed copy back
        // so entering it yourself does not produce "bundle.zip.zip".
        val cleanName = stripTypedExtension(name.trim(), format.extension)

        // On the Storage-Analysis drill-down there is no browsable directory on
        // screen: `_currentDirectory` still points at whatever folder Home was
        // showing, so the archive used to land in an unrelated, invisible place.
        // Target the selection's common parent instead - a folder the picked
        // files actually live in.
        val destDir = if (activeCategoryDrill != null) {
            commonParentOf(selected.map { it.file }) ?: _currentDirectory.value
        } else {
            _currentDirectory.value
        }
        val destFile = File(destDir, "$cleanName.${format.extension}")

        // Validation mirrors createFolder/createNewFile: on rejection the
        // dialog stays open with the reason inline. Checking here (not only in
        // the engine) is what keeps an existing archive from being truncated.
        when {
            selected.isEmpty() -> {
                _showCreateArchiveDialog.value = false
                return
            }
            cleanName.isBlank() -> {
                _nameError.value = NameError.EMPTY
                return
            }
            !repository.isValidItemName(cleanName) -> {
                _nameError.value = NameError.INVALID
                return
            }
            destFile.exists() -> {
                _nameError.value = NameError.EXISTS
                return
            }
        }

        val app = getApplication<Application>()
        val sourceFiles = selected.map { it.file }

        _nameError.value = null
        _showCreateArchiveDialog.value = false
        clearSelection()
        _showArchiveDialog.value = true
        _archiveProgress.value = ArchiveProgress(
            // format.label ("7Z", "TAR.GZ") - format.name leaked "SEVEN_Z"
            // into the popup until the engine's first emit replaced it.
            operation = app.getString(R.string.op_creating, format.label),
            isIndeterminate = true, targetFile = destFile
        )

        archiveJob = viewModelScope.launch {
            // Ownership token: finishArchiveProgress/finishArchiveCancelled use
            // it to avoid tearing down a NEWER operation's popup/handles.
            val myJob = coroutineContext[Job]
            var lastProgress: ArchiveProgress? = null
            try {
                val success = ArchiveEngine.createArchive(
                    sourceFiles = sourceFiles,
                    destinationArchive = destFile,
                    format = format,
                    onProgress = { progress ->
                        lastProgress = progress
                        _archiveProgress.value = progress
                        // While the popup is hidden, the notification carries
                        // the live progress (updates in place).
                        if (!_showArchiveDialog.value) postArchiveNotification(progress)
                    }
                )
                if (!success && lastProgress?.error == null) {
                    lastProgress = ArchiveProgress(
                        operation = app.getString(R.string.op_compression_failed),
                        isComplete = true,
                        error = "Unknown compression error"
                    )
                }

                reloadSurfaces()
                finishArchiveProgress(
                    lastProgress,
                    app.getString(R.string.toast_archive_created, destFile.name),
                    myJob
                )
            } catch (_: CancellationException) {
                // Cancel requested mid-run: a half-written archive is garbage.
                runCatching { if (destFile.exists()) destFile.delete() }
                reloadSurfaces()
                finishArchiveCancelled(app.getString(R.string.toast_compress_cancelled), myJob)
            }
        }
        // Register for the notification's Cancel action (static access path).
        activeArchiveJob = archiveJob
    }

    /**
     * Deepest directory that contains every file in [files] (an ancestor chain
     * intersection, computed on absolute paths so sibling folders such as
     * /A/x and /B/x resolve to / rather than to nothing).
     */
    private fun commonParentOf(files: List<File>): File? {
        if (files.isEmpty()) return null
        val chains = files.map { file ->
            val ancestors = ArrayList<String>()
            var cursor: File? = file.parentFile
            while (cursor != null) {
                ancestors.add(cursor.absolutePath)
                cursor = cursor.parentFile
            }
            ancestors
        }
        val first = chains.first()
        for (candidate in first) {
            if (chains.all { candidate in it }) return File(candidate)
        }
        return null
    }

    fun extractArchive(archiveItem: FileItem) {
        // Single-flight - see createArchive.
        if (archiveJob?.isActive == true) {
            android.widget.Toast.makeText(
                getApplication(), R.string.toast_archive_busy, android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        val parentDir = archiveItem.file.parentFile ?: _currentDirectory.value
        val autoSubfolder = settings.value.autoCreateExtractFolder
        val stem = extractStemOf(archiveItem.name)
        val targetDir = if (autoSubfolder) {
            uniqueExtractTarget(File(parentDir, stem))
        } else parentDir

        val app = getApplication<Application>()

        _showArchiveDialog.value = true
        _archiveProgress.value = ArchiveProgress(
            operation = app.getString(R.string.op_extracting_name, stem),
            isIndeterminate = true, targetFile = targetDir
        )

        archiveJob = viewModelScope.launch {
            // Ownership token - see finishArchiveProgress.
            val myJob = coroutineContext[Job]
            var lastProgress: ArchiveProgress? = null
            try {
                val success = ArchiveEngine.extractArchive(
                    archiveFile = archiveItem.file,
                    targetDir = targetDir,
                    onProgress = { progress ->
                        lastProgress = progress
                        _archiveProgress.value = progress
                        // While the popup is hidden, the notification carries
                        // the live progress (updates in place).
                        if (!_showArchiveDialog.value) postArchiveNotification(progress)
                    }
                )
                if (!success && lastProgress?.error == null) {
                    lastProgress = ArchiveProgress(
                        operation = app.getString(R.string.op_extraction_failed),
                        isComplete = true,
                        error = "Unknown error while extracting"
                    )
                }

                reloadSurfaces()
                // Entries whose target already existed were kept untouched -
                // say so instead of claiming a plain "complete".
                val keptExisting = lastProgress?.skippedExisting ?: 0
                finishArchiveProgress(
                    lastProgress,
                    if (keptExisting > 0) {
                        app.resources.getQuantityString(
                            R.plurals.toast_extraction_kept, keptExisting, keptExisting
                        )
                    } else {
                        app.getString(R.string.toast_extraction_complete)
                    },
                    myJob
                )
            } catch (_: CancellationException) {
                // Extraction keeps already-written entries (standard behavior);
                // the folder listing refreshes right away.
                reloadSurfaces()
                finishArchiveCancelled(app.getString(R.string.toast_extract_cancelled), myJob)
            }
        }
        // Register for the notification's Cancel action (static access path).
        activeArchiveJob = archiveJob
    }

    /**
     * User pressed Cancel: buffered action - the popup and its progress state
     * are torn down immediately so the interface never feels stuck, then the
     * coroutine is interrupted; engine checkpoints throw during the unwind.
     * The trailing cancellation toast still fires when the unwind completes.
     */
    fun cancelArchive() {
        _showArchiveDialog.value = false
        _archiveProgress.value = null
        activeArchiveJob?.cancel()
        activeArchiveJob = null
        archiveJob = null
    }

    /** Popup dismissed (back press / outside tap / Hide button): close it and
     *  hand the progress over to an ongoing Android notification whose actions
     *  can bring the dialog back or cancel the operation outright. */
    fun hideArchiveDialog() {
        _showArchiveDialog.value = false
        postArchiveNotification(_archiveProgress.value)
    }

    /** Reopens the progress popup (notification "Show dialog" action or a tap
     *  on the notification body) and takes the notification back down. */
    fun showArchiveDialogPopup() {
        removeArchiveNotification()
        _showArchiveDialog.value = true
    }

    /**
     * Publishes a completion/cancellation toast and closes the popup.
     *
     * [sourceJob] is the coroutine that is finishing. The UI teardown (popup,
     * progress, handles, notification) only runs while it is still the CURRENT
     * operation: cancelArchive() nulls the handles first and lets a newer
     * operation start, so a late-finishing old job must not close the new job's
     * popup or null the handles its Cancel button depends on.
     */
    private fun finishArchiveProgress(
        final: ArchiveProgress?,
        successMessage: String,
        sourceJob: Job?
    ) {
        val finalState = final ?: ArchiveProgress(
            operation = getApplication<Application>().getString(R.string.op_done),
            isComplete = true
        )

        val app = getApplication<Application>()
        val toastText = if (finalState.error.isNullOrBlank()) {
            successMessage
        } else {
            // Both halves localized: the engine's operation anchors and our
            // fixed fallback errors must not put English into a Russian toast.
            "${localizedProgressOperation(app, finalState.operation)}: " +
                localizedArchiveError(app, finalState.error)
        }
        android.widget.Toast.makeText(app, toastText, android.widget.Toast.LENGTH_SHORT).show()

        if (sourceJob == null || archiveJob === sourceJob) {
            removeArchiveNotification()
            _showArchiveDialog.value = false
            _archiveProgress.value = null
            archiveJob = null
            activeArchiveJob = null
        }
    }

    private fun finishArchiveCancelled(message: String, sourceJob: Job?) {
        android.widget.Toast.makeText(
            getApplication(), message, android.widget.Toast.LENGTH_SHORT
        ).show()
        if (sourceJob == null || archiveJob === sourceJob) {
            removeArchiveNotification()
            _showArchiveDialog.value = false
            _archiveProgress.value = null
            archiveJob = null
            activeArchiveJob = null
        }
    }

    // ======================================================================
    // Ongoing notification for a HIDDEN archive operation. It carries the
    // live progress plus "Show dialog" and "Cancel" actions; it never exists
    // while the popup is on screen.
    // ======================================================================

    private fun ensureArchiveChannel(app: Application) {
        val manager = app.getSystemService(Application.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    CHANNEL_ID,
                    "Archive operations",
                    android.app.NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Progress of hidden compression and extraction tasks"
                    setShowBadge(false)
                }
            )
        }
    }

    /**
     * Posts or updates the ongoing notification for a hidden operation. Safe
     * to invoke when notifications are disabled (SecurityException -> no-op).
     */
    private fun postArchiveNotification(progress: ArchiveProgress?) {
        val p = progress ?: return
        val app = getApplication<Application>()
        try {
            ensureArchiveChannel(app)

            val showPendingIntent = android.app.PendingIntent.getActivity(
                app,
                0,
                android.content.Intent(app, com.baiel.expressivefiles.MainActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(ArchiveActionReceiver.EXTRA_SHOW_ARCHIVE_DIALOG, true),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            val cancelPendingIntent = android.app.PendingIntent.getBroadcast(
                app,
                1,
                android.content.Intent(app, ArchiveActionReceiver::class.java)
                    .setAction(ArchiveActionReceiver.ACTION_CANCEL_ARCHIVE),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )

            val showAction = androidx.core.app.NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_view,
                app.getString(R.string.progress_show),
                showPendingIntent
            ).build()
            val cancelAction = androidx.core.app.NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,
                app.getString(R.string.progress_cancel),
                cancelPendingIntent
            ).build()

            val indeterminate = p.isIndeterminate || p.totalFiles <= 0

            val notification = androidx.core.app.NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(localizedProgressOperation(app, p.operation))
                .setContentText(
                    buildString {
                        if (!p.error.isNullOrBlank()) append(localizedArchiveError(app, p.error))
                        else if (p.currentFileName.isNotBlank()) append(p.currentFileName)
                        if (!indeterminate) append(" (${p.filesProcessed}/${p.totalFiles})")
                    }.ifBlank { null }
                )
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(showPendingIntent)
                .addAction(showAction)
                .addAction(cancelAction)
                .apply {
                    if (indeterminate) setProgress(0, 0, true)
                    else setProgress(p.totalFiles.coerceAtLeast(1), p.filesProcessed, false)
                }
                .build()

            androidx.core.app.NotificationManagerCompat.from(app).notify(NOTIF_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted: the hidden task runs without a
            // notification; the completion toast still arrives.
        } catch (_: Exception) {
        }
    }

    private fun removeArchiveNotification() {
        try {
            androidx.core.app.NotificationManagerCompat.from(getApplication()).cancel(NOTIF_ID)
        } catch (_: Exception) {
        }
    }

    // ======================================================================
    // Settings passthrough
    // ======================================================================

    fun updateThemeMode(themeMode: AppThemeMode) = settingsRepository.updateThemeMode(themeMode)

    /** Persists the tag; the caller recreates the Activity to re-inflate locale. */
    fun updateAppLanguage(tag: String) = settingsRepository.saveLanguageTag(tag)
    fun updateColorPalette(palette: AppColorPalette) = settingsRepository.updateColorPalette(palette)
    fun updatePitchBlack(pitchBlack: Boolean) = settingsRepository.updatePitchBlack(pitchBlack)

    fun toggleShowHiddenFiles() {
        settingsRepository.toggleShowHiddenFiles()
        loadCurrentDirectory()
    }

    fun toggleShowStorageOverview() = settingsRepository.toggleShowStorageOverview()

    fun updateSort(mode: SortMode, order: SortOrder) {
        settingsRepository.updateSortMode(mode)
        settingsRepository.updateSortOrder(order)
        loadCurrentDirectory()
    }

    fun updateViewMode(viewMode: ViewMode) = settingsRepository.updateViewMode(viewMode)
    fun toggleStorageOverviewCollapsed() = settingsRepository.toggleStorageOverviewCollapsed()
    fun updateDefaultArchiveType(type: ArchiveType) = settingsRepository.updateDefaultArchiveType(type)
    fun updateAutoCreateExtractFolder(auto: Boolean) =
        settingsRepository.updateAutoCreateExtractFolder(auto)
}

/**
 * Auto-extract folder for [preferred], uniquified so re-extracting into a
 * directory that already has content never silently overwrites it:
 * "photos" becomes "photos (1)", "photos (2)", ... Existing empty folders are
 * reused; a plain FILE in the way is skipped over as well.
 *
 * Top-level (and internal) purely so the suffix rules are unit-testable - the
 * old private member could only be exercised through a full extract.
 */
internal fun uniqueExtractTarget(preferred: File): File {
    if (!preferred.exists()) return preferred
    val existing = preferred.listFiles()
    if (preferred.isDirectory && existing != null && existing.isEmpty()) return preferred
    val parent = preferred.parentFile ?: return preferred
    val base = preferred.name
    for (index in 1..999) {
        val candidate = File(parent, "$base ($index)")
        if (!candidate.exists()) return candidate
    }
    return preferred
}

/**
 * Folder name (and progress label stem) for an extracted archive: COMPOUND
 * extensions lose their whole suffix - "backup.tar.gz" extracts into "backup",
 * not "backup.tar" (substringBeforeLast('.') mangled every multi-dot archive).
 * Everything else loses only the last segment ("photos.zip" -> "photos").
 *
 * Top-level internal so the suffix rules are unit-testable without running a
 * full extract (same rationale as [uniqueExtractTarget]).
 */
internal fun extractStemOf(name: String): String {
    val stem = when {
        name.endsWith(".tar.gz", ignoreCase = true) -> name.dropLast(7)
        name.endsWith(".tar.bz2", ignoreCase = true) -> name.dropLast(8)
        name.endsWith(".tar.xz", ignoreCase = true) -> name.dropLast(7)
        else -> name.substringBeforeLast('.')
    }
    // Dot-only names (".zip", ".tar.gz" - an archive name with no stem) used
    // to yield "": File(parent, "") normalizes to the PARENT, so the extract
    // target escaped into a sibling of the container folder and the progress
    // label read "«»". Keep the literal name instead.
    return stem.ifEmpty { name }
}

/**
 * Drops a trailing copy of [extension] when the name ends with it as a
 * separate segment ("bundle.ZIP" with ZIP selected -> "bundle"), so typing the
 * format's own extension does not produce "bundle.zip.zip". Another typed
 * extension stays part of the stem ("backup.tar" + ZIP -> "backup.tar.zip")
 * and "myzip" is untouched (no dot before the suffix).
 *
 * Top-level internal for unit tests (same rationale as [extractStemOf]).
 */
internal fun stripTypedExtension(name: String, extension: String): String {
    if (extension.isEmpty() || name.length <= extension.length) return name
    if (!name.endsWith(extension, ignoreCase = true)) return name
    return if (name[name.length - extension.length - 1] == '.') {
        name.dropLast(extension.length + 1)
    } else {
        name
    }
}
