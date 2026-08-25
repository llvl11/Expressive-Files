package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.AppColorPalette
import com.example.model.AppSettings
import com.example.model.AppThemeMode
import com.example.model.ArchiveType
import com.example.model.SortMode
import com.example.model.SortOrder
import com.example.model.ViewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("expressive_files_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val themeModeName = prefs.getString("theme_mode", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        val paletteName = prefs.getString("color_palette", AppColorPalette.DYNAMIC.name) ?: AppColorPalette.DYNAMIC.name
        val sortModeName = prefs.getString("sort_mode", SortMode.NAME.name) ?: SortMode.NAME.name
        val sortOrderName = prefs.getString("sort_order", SortOrder.ASCENDING.name) ?: SortOrder.ASCENDING.name
        val viewModeName = prefs.getString("view_mode", ViewMode.LIST.name) ?: ViewMode.LIST.name
        val archiveTypeName = prefs.getString("default_archive_type", ArchiveType.ZIP.name) ?: ArchiveType.ZIP.name

        return AppSettings(
            themeMode = runCatching { AppThemeMode.valueOf(themeModeName) }.getOrDefault(AppThemeMode.SYSTEM),
            colorPalette = runCatching { AppColorPalette.valueOf(paletteName) }.getOrDefault(AppColorPalette.VIBRANT),
            showHiddenFiles = prefs.getBoolean("show_hidden_files", false),
            showStorageOverview = prefs.getBoolean("show_storage_overview", true),
            storageOverviewCollapsed = prefs.getBoolean("storage_overview_collapsed", false),
            sortMode = runCatching { SortMode.valueOf(sortModeName) }.getOrDefault(SortMode.NAME),
            sortOrder = runCatching { SortOrder.valueOf(sortOrderName) }.getOrDefault(SortOrder.ASCENDING),
            viewMode = runCatching { ViewMode.valueOf(viewModeName) }.getOrDefault(ViewMode.LIST),
            defaultArchiveType = runCatching { ArchiveType.valueOf(archiveTypeName) }.getOrDefault(ArchiveType.ZIP),
            autoCreateExtractFolder = prefs.getBoolean("auto_create_extract_folder", true),
            gridColumnCount = prefs.getInt("grid_column_count", 2)
        )
    }

    fun updateThemeMode(mode: AppThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    fun updateColorPalette(palette: AppColorPalette) {
        prefs.edit().putString("color_palette", palette.name).apply()
        _settings.value = _settings.value.copy(colorPalette = palette)
    }

    fun toggleShowHiddenFiles() {
        val newValue = !_settings.value.showHiddenFiles
        prefs.edit().putBoolean("show_hidden_files", newValue).apply()
        _settings.value = _settings.value.copy(showHiddenFiles = newValue)
    }

    fun toggleShowStorageOverview() {
        val newValue = !_settings.value.showStorageOverview
        prefs.edit().putBoolean("show_storage_overview", newValue).apply()
        _settings.value = _settings.value.copy(showStorageOverview = newValue)
    }

    fun updateShowStorageOverview(show: Boolean) {
        prefs.edit().putBoolean("show_storage_overview", show).apply()
        _settings.value = _settings.value.copy(showStorageOverview = show)
    }

    fun toggleStorageOverviewCollapsed() {
        val newValue = !_settings.value.storageOverviewCollapsed
        prefs.edit().putBoolean("storage_overview_collapsed", newValue).apply()
        _settings.value = _settings.value.copy(storageOverviewCollapsed = newValue)
    }

    fun updateStorageOverviewCollapsed(collapsed: Boolean) {
        prefs.edit().putBoolean("storage_overview_collapsed", collapsed).apply()
        _settings.value = _settings.value.copy(storageOverviewCollapsed = collapsed)
    }

    fun updateSortMode(mode: SortMode) {
        prefs.edit().putString("sort_mode", mode.name).apply()
        _settings.value = _settings.value.copy(sortMode = mode)
    }

    fun updateSortOrder(order: SortOrder) {
        prefs.edit().putString("sort_order", order.name).apply()
        _settings.value = _settings.value.copy(sortOrder = order)
    }

    fun updateViewMode(mode: ViewMode) {
        prefs.edit().putString("view_mode", mode.name).apply()
        _settings.value = _settings.value.copy(viewMode = mode)
    }

    fun updateDefaultArchiveType(type: ArchiveType) {
        prefs.edit().putString("default_archive_type", type.name).apply()
        _settings.value = _settings.value.copy(defaultArchiveType = type)
    }

    fun updateAutoCreateExtractFolder(auto: Boolean) {
        prefs.edit().putBoolean("auto_create_extract_folder", auto).apply()
        _settings.value = _settings.value.copy(autoCreateExtractFolder = auto)
    }

    fun updateGridColumnCount(count: Int) {
        prefs.edit().putInt("grid_column_count", count).apply()
        _settings.value = _settings.value.copy(gridColumnCount = count)
    }
}
