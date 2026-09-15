package com.baiel.expressivefiles.viewmodel

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baiel.expressivefiles.ArchiveActionReceiver
import com.baiel.expressivefiles.R
import com.baiel.expressivefiles.archive.ArchiveEngine
import com.baiel.expressivefiles.data.FileManagerRepository
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
import com.baiel.expressivefiles.model.OsType
import com.baiel.expressivefiles.model.SortMode
import com.baiel.expressivefiles.model.SortOrder
import com.baiel.expressivefiles.model.StorageStats
import com.baiel.expressivefiles.model.ViewMode
import com.baiel.expressivefiles.ui.components.DialogActionType
import kotlinx.coroutines.CancellationException
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
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

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
    if (!raw.startsWith("Extracting") && !raw.startsWith("Compression")) return raw
    val res = context.resources
    return when (raw.lowercase()) {
        "extracting zip" -> res.getString(R.string.op_extracting_zip)
        "extracting 7z" -> res.getString(R.string.op_extracting_7z)
        "extracting tar" -> res.getString(R.string.op_extracting_tar)
        "extracting rar" -> res.getString(R.string.op_extracting_rar)
        "extracting archive" -> res.getString(R.string.op_extracting_archive)
        "extraction complete" -> res.getString(R.string.op_extraction_complete)
        "compression complete" -> res.getString(R.string.op_compression_complete)
        else -> raw
    }
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

    private val _pendingDeleteItems = MutableStateFlow<List<FileItem>>(emptyList())
    val pendingDeleteItems: StateFlow<List<FileItem>> = _pendingDeleteItems.asStateFlow()

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
        return Environment.isExternalStorageManager()
    }

    fun refreshPermission() {
        _hasStoragePermission.value = checkStoragePermission()
        loadCurrentDirectory()
        loadStorageStats()
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
        directoryBackstack.add(_currentDirectory.value)
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
                var ready = directory.exists() && directory.canRead()
                var retries = 0
                while (!ready && retries < 3 && checkStoragePermission()) {
                    retries++
                    delay(250.milliseconds)
                    ready = directory.exists() && directory.canRead()
                }
                if (!ready) {
                    if (_selectedPaths.value.isEmpty()) {
                        // Real denial/unreadable target: clear once, never clobber
                        // an existing healthy list mid-session.
                        _files.value = emptyList()
                    }
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
        val pending = items.filter {
            !it.isDirectory && it.fileType == FileType.VIDEO && it.duration == null
        }
        if (pending.isEmpty()) return

        videoMetaJob?.cancel()
        videoMetaJob = viewModelScope.launch {
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
    }

    fun setSearchQuery(query: String) {
        if (query == _searchQuery.value) return
        _searchQuery.value = query
        loadCurrentDirectory()
    }

    fun setCategoryFilter(category: FileType?) {
        if (category == _selectedCategory.value) return
        _selectedCategory.value = category
        loadCurrentDirectory()
    }

    fun loadStorageStats(deepScan: Boolean = false) {
        storageStatsJob?.cancel()
        storageStatsJob = viewModelScope.launch {
            if (deepScan) {
                _storageStats.value = repository.getStorageStats()
            } else {
                // Cheap capacity probe that PRESERVES the already-measured
                // per-category breakdown. Replacing the whole model with a fresh
                // capacity-only one zeroed every size whenever the app was
                // resumed while sitting on the Storage Analysis screen.
                val cap = repository.getStorageCapacity()
                _storageStats.value = _storageStats.value.copy(
                    totalBytes = cap.totalBytes,
                    freeBytes = cap.freeBytes,
                    usedBytes = cap.usedBytes
                )
            }
        }
    }

    fun loadCategoryFiles(category: FileType) {
        activeCategoryDrill = category
        categoryLoadJob?.cancel()
        categoryLoadJob = viewModelScope.launch {
            _isCategoryLoading.value = true
            val currentSettings = settings.value
            val (sortMode, sortOrder) = categorySortFor(category)
            val loaded = repository.getFilesByCategory(
                category, rootDirectory,
                showHidden = currentSettings.showHiddenFiles,
                sortMode = sortMode,
                sortOrder = sortOrder
            )
            _categoryFiles.value = loaded
            startVideoDurationEnrichment(_categoryFiles, loaded)
            _isCategoryLoading.value = false
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
        val candidates = _selectedCategory.value?.let { _categoryFiles.value } ?: _files.value
        val candidatePaths = candidates.mapTo(mutableSetOf()) { it.path }
        // Toggle: when everything already selected, clear the whole selection.
        _selectedPaths.value =
            if (candidatePaths.isNotEmpty() && _selectedPaths.value.containsAll(candidatePaths)) {
                emptySet()
            } else {
                candidatePaths
            }
    }

    /** True while every listed item (current directory or category view) is selected. */
    val isAllSelected: StateFlow<Boolean> = combine(
        _files,
        _categoryFiles,
        _selectedPaths,
        _selectedCategory
    ) { files, categoryFiles, selected, selectedCategory ->
        val candidates = if (selectedCategory != null) categoryFiles else files
        candidates.isNotEmpty() && selected.containsAll(candidates.mapTo(mutableSetOf()) { it.path })
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
                android.widget.Toast.makeText(
                    app, app.getString(R.string.toast_paste_failed), android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            loadCurrentDirectory()
            loadStorageStats()
        }
    }

    // ======================================================================
    // Mutations
    // ======================================================================

    fun createFolder(name: String) {
        viewModelScope.launch {
            repository.createDirectory(_currentDirectory.value, name)
            loadCurrentDirectory()
            loadStorageStats()
        }
    }

    fun createNewFile(name: String) {
        viewModelScope.launch {
            repository.createNewFile(_currentDirectory.value, name)
            loadCurrentDirectory()
            loadStorageStats()
        }
    }

    fun renameFile(item: FileItem, newName: String) {
        viewModelScope.launch {
            repository.rename(item.file, newName)
            loadCurrentDirectory()
        }
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
            repository.deleteFiles(items.map { it.file })
            clearSelection()
            loadCurrentDirectory()
            loadStorageStats()
        }
    }

    // ======================================================================
    // Opening / sharing
    // ======================================================================

    fun openFile(item: FileItem) {
        when {
            item.isDirectory -> navigateToDirectory(item.file)
            // Compressed files get the built-in inspector; everything else goes
            // to the OS resolver, which offers ONLY handlers matching this
            // format (repository maps precise MIME types) with the native
            // "Just once / Always" choice, remembered by the system per type.
            item.fileType == FileType.ARCHIVE -> _activeArchiveInspectItem.value = item
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
        _activeArchiveInspectItem.value = item
    }

    fun closeArchiveInspector() {
        _activeArchiveInspectItem.value = null
    }

    fun startRename(item: FileItem) {
        _renameTargetItem.value = item
    }

    fun closeRename() {
        _renameTargetItem.value = null
    }

    fun requestCreateArchive() {
        _showCreateArchiveDialog.value = true
    }

    fun dismissCreateArchive() {
        _showCreateArchiveDialog.value = false
    }

    /** Opens the New Folder/File dialog; DELETE_CONFIRM goes through [deleteConfirmed]. */
    fun requestNewItem(type: DialogActionType) {
        when (type) {
            DialogActionType.NEW_FOLDER, DialogActionType.NEW_FILE ->
                _showNewItemDialog.value = type
            else -> {}
        }
    }

    fun dismissNewItem() {
        _showNewItemDialog.value = null
    }

    // ======================================================================
    // Archives
    // ======================================================================

    fun createArchive(name: String, format: ArchiveType) {
        val selected = resolveSelection()
        if (selected.isEmpty()) return

        val app = getApplication<Application>()
        val sourceFiles = selected.map { it.file }
        val destFile = File(_currentDirectory.value, "$name.${format.extension}")

        _showCreateArchiveDialog.value = false
        clearSelection()
        _showArchiveDialog.value = true
        _archiveProgress.value = ArchiveProgress(
            operation = app.getString(R.string.op_creating, format.name),
            isIndeterminate = true, targetFile = destFile
        )

        archiveJob = viewModelScope.launch {
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

                loadCurrentDirectory()
                loadStorageStats()
                finishArchiveProgress(lastProgress, app.getString(R.string.toast_archive_created, destFile.name))
            } catch (_: CancellationException) {
                // Cancel requested mid-run: a half-written archive is garbage.
                runCatching { if (destFile.exists()) destFile.delete() }
                loadCurrentDirectory()
                finishArchiveCancelled(app.getString(R.string.toast_compress_cancelled))
            }
        }
        // Register for the notification's Cancel action (static access path).
        activeArchiveJob = archiveJob
    }

    fun extractArchive(archiveItem: FileItem) {
        val parentDir = archiveItem.file.parentFile ?: _currentDirectory.value
        val autoSubfolder = settings.value.autoCreateExtractFolder
        val targetDir = if (autoSubfolder) {
            File(parentDir, archiveItem.name.substringBeforeLast('.'))
        } else parentDir

        val app = getApplication<Application>()

        _showArchiveDialog.value = true
        _archiveProgress.value = ArchiveProgress(
            operation = app.getString(R.string.op_extracting_name, archiveItem.name.substringBeforeLast('.')),
            isIndeterminate = true, targetFile = targetDir
        )

        archiveJob = viewModelScope.launch {
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

                loadCurrentDirectory()
                loadStorageStats()
                finishArchiveProgress(lastProgress, app.getString(R.string.toast_extraction_complete))
            } catch (_: CancellationException) {
                // Extraction keeps already-written entries (standard behavior);
                // the folder listing refreshes right away.
                loadCurrentDirectory()
                finishArchiveCancelled(app.getString(R.string.toast_extract_cancelled))
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
     * Publishes a completion/cancellation toast and closes the popup. No
     * Android notification is involved anywhere in the archive flow.
     */
    private fun finishArchiveProgress(final: ArchiveProgress?, successMessage: String) {
        val finalState = final ?: ArchiveProgress(
            operation = getApplication<Application>().getString(R.string.op_done),
            isComplete = true
        )
        _archiveProgress.value = finalState

        val app = getApplication<Application>()
        val toastText = if (finalState.error.isNullOrBlank()) {
            successMessage
        } else {
            "${finalState.operation}: ${finalState.error}"
        }
        android.widget.Toast.makeText(app, toastText, android.widget.Toast.LENGTH_SHORT).show()

        removeArchiveNotification()
        _showArchiveDialog.value = false
        _archiveProgress.value = null
        archiveJob = null
        activeArchiveJob = null
    }

    private fun finishArchiveCancelled(message: String) {
        android.widget.Toast.makeText(
            getApplication(), message, android.widget.Toast.LENGTH_SHORT
        ).show()
        removeArchiveNotification()
        _showArchiveDialog.value = false
        _archiveProgress.value = null
        archiveJob = null
        activeArchiveJob = null
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
                        if (!p.error.isNullOrBlank()) append(p.error)
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

    /** Persists the OS skin; the theme composable switches scheme + icon set. */
    fun updateOsType(type: OsType) = settingsRepository.updateOsType(type)

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
