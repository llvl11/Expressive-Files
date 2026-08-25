package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.archive.ArchiveEngine
import com.example.data.FileManagerRepository
import com.example.data.SettingsRepository
import com.example.model.AppColorPalette
import com.example.model.AppSettings
import com.example.model.AppThemeMode
import com.example.model.ArchiveProgress
import com.example.model.ArchiveType
import com.example.model.ClipboardAction
import com.example.model.ClipboardState
import com.example.model.FileItem
import com.example.model.FileType
import com.example.model.SortMode
import com.example.model.SortOrder
import com.example.model.StorageStats
import com.example.model.ViewMode
import com.example.ui.components.DialogActionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class FileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FileManagerRepository(application)
    private val settingsRepository = SettingsRepository(application)

    val settings: StateFlow<AppSettings> = settingsRepository.settings

    // Navigation state
    val rootDirectory: File = repository.rootStorageDirectory
    val appStorageDirectory: File = repository.appStorageDirectory

    private val _currentDirectory = MutableStateFlow(rootDirectory)
    val currentDirectory: StateFlow<File> = _currentDirectory.asStateFlow()

    private val directoryBackstack = mutableListOf<File>()

    // File list state
    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Search and Category Filter
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<FileType?>(null)
    val selectedCategory: StateFlow<FileType?> = _selectedCategory.asStateFlow()

    // Selection state
    private val _selectedPaths = MutableStateFlow<Set<String>>(emptySet())
    val selectedPaths: StateFlow<Set<String>> = _selectedPaths.asStateFlow()

    val isSelectionMode: Boolean
        get() = _selectedPaths.value.isNotEmpty()

    // Clipboard
    private val _clipboard = MutableStateFlow<ClipboardState?>(null)
    val clipboard: StateFlow<ClipboardState?> = _clipboard.asStateFlow()

    // Storage Statistics
    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    // Dialog & Sheet States
    var showCreateArchiveDialog = MutableStateFlow(false)
    var showNewItemDialog = MutableStateFlow<DialogActionType?>(null)
    var activeActionSheetItem = MutableStateFlow<FileItem?>(null)
    var activePreviewItem = MutableStateFlow<FileItem?>(null)
    var activeArchiveInspectItem = MutableStateFlow<FileItem?>(null)
    var renameTargetItem = MutableStateFlow<FileItem?>(null)

    // Archive Progress
    private val _archiveProgress = MutableStateFlow<ArchiveProgress?>(null)
    val archiveProgress: StateFlow<ArchiveProgress?> = _archiveProgress.asStateFlow()

    // Permission state
    private val _hasStoragePermission = MutableStateFlow(checkStoragePermission())
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initializeDemoFilesIfNeeded()
            loadCurrentDirectory()
            loadStorageStats()
        }
    }

    fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun refreshPermission() {
        _hasStoragePermission.value = checkStoragePermission()
        loadCurrentDirectory()
        loadStorageStats()
    }

    fun navigateToDirectory(directory: File) {
        if (!directory.exists() || !directory.isDirectory) return
        if (directory.absolutePath != _currentDirectory.value.absolutePath) {
            directoryBackstack.add(_currentDirectory.value)
            _currentDirectory.value = directory
            clearSelection()
            _selectedCategory.value = null
            _searchQuery.value = ""
            loadCurrentDirectory()
        }
    }

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
        if (directoryBackstack.isNotEmpty()) {
            val previous = directoryBackstack.removeAt(directoryBackstack.size - 1)
            _currentDirectory.value = previous
            clearSelection()
            loadCurrentDirectory()
            return true
        }
        val parent = _currentDirectory.value.parentFile
        if (parent != null && parent.canRead() && _currentDirectory.value.absolutePath != rootDirectory.absolutePath) {
            _currentDirectory.value = parent
            clearSelection()
            loadCurrentDirectory()
            return true
        }
        return false
    }

    fun loadCurrentDirectory() {
        viewModelScope.launch {
            _isLoading.value = true
            val currentSettings = settings.value

            val category = _selectedCategory.value
            val query = _searchQuery.value

            val loadedFiles = when {
                query.isNotBlank() -> {
                    repository.searchFiles(
                        startDir = _currentDirectory.value,
                        query = query,
                        recursive = true,
                        showHidden = currentSettings.showHiddenFiles,
                        sortMode = currentSettings.sortMode,
                        sortOrder = currentSettings.sortOrder
                    )
                }
                category != null -> {
                    repository.getFilesByCategory(
                        category = category,
                        baseDir = _currentDirectory.value,
                        showHidden = currentSettings.showHiddenFiles,
                        sortMode = currentSettings.sortMode,
                        sortOrder = currentSettings.sortOrder
                    )
                }
                else -> {
                    repository.listFiles(
                        directory = _currentDirectory.value,
                        showHidden = currentSettings.showHiddenFiles,
                        sortMode = currentSettings.sortMode,
                        sortOrder = currentSettings.sortOrder
                    )
                }
            }

            // Apply selection mapping
            val selected = _selectedPaths.value
            _files.value = loadedFiles.map { item ->
                item.copy(isSelected = selected.contains(item.path))
            }
            _isLoading.value = false
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        loadCurrentDirectory()
    }

    fun setCategoryFilter(category: FileType?) {
        _selectedCategory.value = category
        loadCurrentDirectory()
    }

    fun loadStorageStats() {
        viewModelScope.launch {
            _storageStats.value = repository.getStorageStats()
        }
    }

    // Selection Management
    fun toggleSelection(item: FileItem) {
        val set = _selectedPaths.value.toMutableSet()
        if (set.contains(item.path)) {
            set.remove(item.path)
        } else {
            set.add(item.path)
        }
        _selectedPaths.value = set
        _files.value = _files.value.map { it.copy(isSelected = set.contains(it.path)) }
    }

    fun selectAll() {
        val allPaths = _files.value.map { it.path }.toSet()
        _selectedPaths.value = allPaths
        _files.value = _files.value.map { it.copy(isSelected = true) }
    }

    fun clearSelection() {
        _selectedPaths.value = emptySet()
        _files.value = _files.value.map { it.copy(isSelected = false) }
    }

    // File Operations
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
            loadStorageStats()
        }
    }

    fun deleteSelectedFiles() {
        viewModelScope.launch {
            val targets = _files.value.filter { it.isSelected }.map { it.file }
            repository.deleteFiles(targets)
            clearSelection()
            loadCurrentDirectory()
            loadStorageStats()
        }
    }

    fun deleteSingleFile(item: FileItem) {
        viewModelScope.launch {
            repository.deleteFiles(listOf(item.file))
            loadCurrentDirectory()
            loadStorageStats()
        }
    }

    fun duplicateFile(item: FileItem) {
        viewModelScope.launch {
            repository.duplicate(item.file)
            loadCurrentDirectory()
            loadStorageStats()
        }
    }

    fun openFile(item: FileItem) {
        if (item.isDirectory) {
            navigateToDirectory(item.file)
        } else if (item.fileType == FileType.ARCHIVE) {
            activeArchiveInspectItem.value = item
        } else {
            activePreviewItem.value = item
        }
    }

    fun openFileExternal(item: FileItem) {
        repository.openFile(item.file)
    }

    fun shareFile(item: FileItem) {
        repository.shareFiles(listOf(item.file))
    }

    fun shareSelectedFiles() {
        val targets = _files.value.filter { it.isSelected }.map { it.file }
        repository.shareFiles(targets)
    }

    // Clipboard
    fun copySelected() {
        val selected = _files.value.filter { it.isSelected }
        _clipboard.value = ClipboardState(items = selected, action = ClipboardAction.COPY)
        clearSelection()
    }

    fun cutSelected() {
        val selected = _files.value.filter { it.isSelected }
        _clipboard.value = ClipboardState(items = selected, action = ClipboardAction.CUT)
        clearSelection()
    }

    fun clearClipboard() {
        _clipboard.value = null
    }

    fun pasteToCurrentDirectory() {
        val clip = _clipboard.value ?: return
        viewModelScope.launch {
            val sources = clip.items.map { it.file }
            if (clip.action == ClipboardAction.COPY) {
                repository.copyFiles(sources, _currentDirectory.value)
            } else {
                repository.moveFiles(sources, _currentDirectory.value)
                _clipboard.value = null
            }
            loadCurrentDirectory()
            loadStorageStats()
        }
    }

    // Archive Operations
    fun createArchive(name: String, format: ArchiveType) {
        val selected = _files.value.filter { it.isSelected }
        if (selected.isEmpty()) return

        val sourceFiles = selected.map { it.file }
        val destFile = File(_currentDirectory.value, "$name.${format.extension}")

        viewModelScope.launch {
            showCreateArchiveDialog.value = false
            clearSelection()

            ArchiveEngine.createArchive(
                sourceFiles = sourceFiles,
                destinationArchive = destFile,
                format = format,
                onProgress = { progress ->
                    _archiveProgress.value = progress
                }
            )
            loadCurrentDirectory()
            loadStorageStats()
        }
    }

    fun extractArchive(archiveItem: FileItem) {
        val parentDir = archiveItem.file.parentFile ?: _currentDirectory.value
        val autoSubfolder = settings.value.autoCreateExtractFolder
        val targetDir = if (autoSubfolder) {
            File(parentDir, archiveItem.name.substringBeforeLast('.'))
        } else {
            parentDir
        }

        viewModelScope.launch {
            ArchiveEngine.extractArchive(
                archiveFile = archiveItem.file,
                targetDir = targetDir,
                onProgress = { progress ->
                    _archiveProgress.value = progress
                }
            )
            loadCurrentDirectory()
            loadStorageStats()
        }
    }

    fun dismissArchiveProgress() {
        _archiveProgress.value = null
    }

    // Settings actions
    fun updateThemeMode(mode: AppThemeMode) = settingsRepository.updateThemeMode(mode)
    fun updateColorPalette(palette: AppColorPalette) = settingsRepository.updateColorPalette(palette)
    fun toggleShowHiddenFiles() {
        settingsRepository.toggleShowHiddenFiles()
        loadCurrentDirectory()
    }
    fun toggleShowStorageOverview() = settingsRepository.toggleShowStorageOverview()
    fun updateShowStorageOverview(show: Boolean) = settingsRepository.updateShowStorageOverview(show)
    fun updateSortMode(mode: SortMode) {
        settingsRepository.updateSortMode(mode)
        loadCurrentDirectory()
    }
    fun updateSortOrder(order: SortOrder) {
        settingsRepository.updateSortOrder(order)
        loadCurrentDirectory()
    }
    fun updateSort(mode: SortMode, order: SortOrder) {
        settingsRepository.updateSortMode(mode)
        settingsRepository.updateSortOrder(order)
        loadCurrentDirectory()
    }
    fun toggleSortOrder() {
        val nextOrder = if (settings.value.sortOrder == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
        settingsRepository.updateSortOrder(nextOrder)
        loadCurrentDirectory()
    }
    fun updateViewMode(mode: ViewMode) = settingsRepository.updateViewMode(mode)
    fun toggleStorageOverviewCollapsed() = settingsRepository.toggleStorageOverviewCollapsed()
    fun updateStorageOverviewCollapsed(collapsed: Boolean) = settingsRepository.updateStorageOverviewCollapsed(collapsed)
    fun updateDefaultArchiveType(type: ArchiveType) = settingsRepository.updateDefaultArchiveType(type)
    fun updateAutoCreateExtractFolder(auto: Boolean) = settingsRepository.updateAutoCreateExtractFolder(auto)
    fun updateGridColumnCount(count: Int) = settingsRepository.updateGridColumnCount(count)

    fun resetDemoData() {
        viewModelScope.launch {
            repository.initializeDemoFilesIfNeeded()
            loadCurrentDirectory()
            loadStorageStats()
        }
    }
}
