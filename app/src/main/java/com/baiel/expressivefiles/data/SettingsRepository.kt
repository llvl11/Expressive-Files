package com.baiel.expressivefiles.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.baiel.expressivefiles.model.AppColorPalette
import com.baiel.expressivefiles.model.AppLanguage
import com.baiel.expressivefiles.model.AppSettings
import com.baiel.expressivefiles.model.AppThemeMode
import com.baiel.expressivefiles.model.ArchiveType
import com.baiel.expressivefiles.model.SortMode
import com.baiel.expressivefiles.model.SortOrder
import com.baiel.expressivefiles.model.OsType
import com.baiel.expressivefiles.model.ViewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

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
            osType = runCatching { OsType.valueOf(prefs.getString("os_type", OsType.PIXEL.name) ?: OsType.PIXEL.name) }
                .getOrDefault(OsType.PIXEL),
            pitchBlack = prefs.getBoolean("pitch_black", false),
            showHiddenFiles = prefs.getBoolean("show_hidden_files", false),
            showStorageOverview = prefs.getBoolean("show_storage_overview", true),
            storageOverviewCollapsed = prefs.getBoolean("storage_overview_collapsed", false),
            sortMode = runCatching { SortMode.valueOf(sortModeName) }.getOrDefault(SortMode.NAME),
            sortOrder = runCatching { SortOrder.valueOf(sortOrderName) }.getOrDefault(SortOrder.ASCENDING),
            viewMode = runCatching { ViewMode.valueOf(viewModeName) }.getOrDefault(ViewMode.LIST),
            defaultArchiveType = runCatching { ArchiveType.valueOf(archiveTypeName) }.getOrDefault(ArchiveType.ZIP),
            autoCreateExtractFolder = prefs.getBoolean("auto_create_extract_folder", true),
            appLanguage = AppLanguage.fromTag(loadLanguageTag())
        )
    }

    /**
     * Synchronous pre-inflation language read used by the Activity's
     * attachBaseContext - before any Flow machinery exists - so the whole
     * view tree resolves against the right resources. Defaults to Russian.
     */
    fun loadLanguageTag(): String =
        prefs.getString(KEY_LANGUAGE_TAG, null)?.takeIf { it.isNotBlank() } ?: AppLanguage.RUSSIAN.tag

    /** Persists the tag immediately and mirrors the choice into the settings flow. */
    fun saveLanguageTag(tag: String) {
        prefs.edit { putString(KEY_LANGUAGE_TAG, tag) }
        _settings.value = _settings.value.copy(appLanguage = AppLanguage.fromTag(tag))
    }

    private companion object {
        const val PREFS_FILE = "expressive_files_prefs"
        const val KEY_LANGUAGE_TAG = "app_language_tag"
    }

    fun updateThemeMode(mode: AppThemeMode) {
        prefs.edit { putString("theme_mode", mode.name) }
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    fun updateColorPalette(palette: AppColorPalette) {
        prefs.edit { putString("color_palette", palette.name) }
        _settings.value = _settings.value.copy(colorPalette = palette)
    }

    fun updateOsType(type: OsType) {
        prefs.edit { putString("os_type", type.name) }
        _settings.value = _settings.value.copy(osType = type)
    }

    fun updatePitchBlack(enabled: Boolean) {
        prefs.edit { putBoolean("pitch_black", enabled) }
        _settings.value = _settings.value.copy(pitchBlack = enabled)
    }

    fun toggleShowHiddenFiles() {
        val newValue = !_settings.value.showHiddenFiles
        prefs.edit { putBoolean("show_hidden_files", newValue) }
        _settings.value = _settings.value.copy(showHiddenFiles = newValue)
    }

    fun toggleShowStorageOverview() {
        val newValue = !_settings.value.showStorageOverview
        prefs.edit { putBoolean("show_storage_overview", newValue) }
        _settings.value = _settings.value.copy(showStorageOverview = newValue)
    }

    fun toggleStorageOverviewCollapsed() {
        val newValue = !_settings.value.storageOverviewCollapsed
        prefs.edit { putBoolean("storage_overview_collapsed", newValue) }
        _settings.value = _settings.value.copy(storageOverviewCollapsed = newValue)
    }

    fun updateSortMode(mode: SortMode) {
        prefs.edit { putString("sort_mode", mode.name) }
        _settings.value = _settings.value.copy(sortMode = mode)
    }

    fun updateSortOrder(order: SortOrder) {
        prefs.edit { putString("sort_order", order.name) }
        _settings.value = _settings.value.copy(sortOrder = order)
    }

    fun updateViewMode(mode: ViewMode) {
        prefs.edit { putString("view_mode", mode.name) }
        _settings.value = _settings.value.copy(viewMode = mode)
    }

    fun updateDefaultArchiveType(type: ArchiveType) {
        prefs.edit { putString("default_archive_type", type.name) }
        _settings.value = _settings.value.copy(defaultArchiveType = type)
    }

    fun updateAutoCreateExtractFolder(auto: Boolean) {
        prefs.edit { putBoolean("auto_create_extract_folder", auto) }
        _settings.value = _settings.value.copy(autoCreateExtractFolder = auto)
    }

    /**
     * Per-category sort choices, e.g. {"VIDEO": "DATE:DESC", "IMAGE": "NAME:ASC"}.
     * Serialized as "&"-joined KEY=MODE:ORDER pairs so adding categories needs
     * no schema migration. Unknown keys/modes are dropped silently on load.
     */
    fun getCategorySorts(): Map<String, Pair<SortMode, SortOrder>> =
        prefs.getString("category_sorts", null)
            ?.split('&')
            ?.mapNotNull { entry ->
                val eq = entry.indexOf('=')
                val colon = entry.indexOf(':')
                if (eq !in 1 until colon) return@mapNotNull null
                val key = entry.substring(0, eq)
                val mode = runCatching { SortMode.valueOf(entry.substring(eq + 1, colon)) }.getOrNull()
                val order = runCatching { SortOrder.valueOf(entry.substring(colon + 1)) }.getOrNull()
                if (mode != null && order != null) key to (mode to order) else null
            }
            ?.toMap()
            ?: emptyMap()

    fun saveCategorySorts(sorts: Map<String, Pair<SortMode, SortOrder>>) {
        val encoded = sorts.entries.joinToString("&") { "${it.key}=${it.value.first.name}:${it.value.second.name}" }
        prefs.edit { putString("category_sorts", encoded) }
    }
}
