package com.baiel.expressivefiles.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.baiel.expressivefiles.R
import com.baiel.expressivefiles.model.AppColorPalette
import com.baiel.expressivefiles.model.AppLanguage
import com.baiel.expressivefiles.model.AppThemeMode
import com.baiel.expressivefiles.model.ViewMode
import com.baiel.expressivefiles.model.label
import com.baiel.expressivefiles.ui.components.CREATABLE_ARCHIVE_FORMATS
import com.baiel.expressivefiles.ui.components.ChunkyIconButton
import com.baiel.expressivefiles.ui.components.CookieIconContainer
import com.baiel.expressivefiles.viewmodel.FileViewModel
import com.baiel.expressivefiles.ui.theme.ChunkyIconShape
import com.baiel.expressivefiles.ui.theme.ChunkyTileShape
import com.baiel.expressivefiles.ui.theme.PillShape
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: FileViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var palettesExpanded by remember { mutableStateOf(false) }

    // Glass top bar (Haze): the settings list records itself as the blur
    // source and the pinned bar replays it frosted while scrolling. Lighter
    // than the small floating pills (which share one recipe): this bar spans
    // the full width, so the same tint would read much darker here.
    val hazeState = rememberHazeState()
    val isDarkBg = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val glassTint = if (isDarkBg) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.32f)
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.28f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                },
                navigationIcon = {
                    ChunkyIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        onClick = onNavigateBack,
                        contentDescription = stringResource(R.string.breadcrumb_back),
                        size = 40.dp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier.hazeEffect(hazeState) {
                    blurRadius = 8.dp
                    tints = listOf(HazeTint(glassTint))
                    noiseFactor = 0f
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Appearance & Theming Section
            SettingsSectionHeader(title = stringResource(R.string.settings_section_appearance), icon = Icons.Rounded.ColorLens)

            // Accent Color Palette (first option)
            SettingsTile {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Rounded ripple to match the tile's corners.
                        .clip(ChunkyTileShape)
                        .clickable { palettesExpanded = !palettesExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.settings_color_palette_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = if (palettesExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(
                    visible = palettesExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        AppColorPalette.entries.forEach { palette ->
                            val isSelected = settings.colorPalette == palette
                            Surface(
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                // Plain rounded row, not a cookie shape.
                                shape = ChunkyTileShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ChunkyTileShape)
                                    .clickable { viewModel.updateColorPalette(palette) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = palette.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SettingsTile {
                val context = LocalContext.current
                Text(
                    text = stringResource(R.string.settings_language_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Applies immediately: the tag is persisted, then the activity
                // recreates so every resource re-resolves under the new locale.
                SegmentedRow(
                    options = AppLanguage.entries.toList(),
                    label = { it.title },
                    isSelected = { settings.appLanguage == it },
                    onSelect = { language ->
                        if (settings.appLanguage != language) {
                            viewModel.updateAppLanguage(language.tag)
                            (context as? Activity)?.recreate()
                        }
                    },
                    height = 40.dp
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                )

                Text(
                    text = stringResource(R.string.settings_theme_mode_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                val systemLabel = stringResource(R.string.settings_theme_system)
                val lightLabel = stringResource(R.string.settings_theme_light)
                val darkLabel = stringResource(R.string.settings_theme_dark)
                SegmentedRow(
                    options = AppThemeMode.entries.toList(),
                    label = {
                        when (it) {
                            AppThemeMode.SYSTEM -> systemLabel
                            AppThemeMode.LIGHT -> lightLabel
                            AppThemeMode.DARK -> darkLabel
                        }
                    },
                    isSelected = { settings.themeMode == it },
                    onSelect = { viewModel.updateThemeMode(it) }
                )

                if (settings.themeMode != AppThemeMode.LIGHT) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )
                    ToggleRow(
                        title = stringResource(R.string.settings_pitch_black_title),
                        subtitle = stringResource(R.string.settings_pitch_black_subtitle),
                        checked = settings.pitchBlack,
                        onCheckedChange = { viewModel.updatePitchBlack(it) }
                    )
                }
            }

            // File Display Preferences
            SettingsSectionHeader(title = stringResource(R.string.settings_section_files), icon = Icons.Rounded.Visibility)

            SettingsTile {
                ToggleRow(
                    title = stringResource(R.string.settings_storage_overview_title),
                    subtitle = stringResource(R.string.settings_storage_overview_subtitle),
                    checked = settings.showStorageOverview,
                    onCheckedChange = { viewModel.toggleShowStorageOverview() }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                )

                ToggleRow(
                    title = stringResource(R.string.settings_hidden_files_title),
                    subtitle = stringResource(R.string.settings_hidden_files_subtitle),
                    checked = settings.showHiddenFiles,
                    onCheckedChange = { viewModel.toggleShowHiddenFiles() }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                )

                // Default View Mode
                Text(
                    text = stringResource(R.string.settings_view_mode_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                val listLabel = stringResource(R.string.settings_view_list)
                val gridLabel = stringResource(R.string.settings_view_grid)
                val cardsLabel = stringResource(R.string.settings_view_cards)
                SegmentedRow(
                    options = ViewMode.entries.toList(),
                    label = {
                        when (it) {
                            ViewMode.LIST -> listLabel
                            ViewMode.GRID -> gridLabel
                            ViewMode.EXPRESSIVE_CARDS -> cardsLabel
                        }
                    },
                    isSelected = { settings.viewMode == it },
                    onSelect = { viewModel.updateViewMode(it) },
                    height = 40.dp
                )
            }

            // Archive & Compression Settings
            SettingsSectionHeader(title = stringResource(R.string.settings_section_archive), icon = Icons.Rounded.Archive)

            SettingsTile {
                ToggleRow(
                    title = stringResource(R.string.settings_extract_subfolder_title),
                    subtitle = stringResource(R.string.settings_extract_subfolder_subtitle),
                    checked = settings.autoCreateExtractFolder,
                    onCheckedChange = { viewModel.updateAutoCreateExtractFolder(it) }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                )

                // Default archive format
                Text(
                    text = stringResource(R.string.settings_default_format_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                SegmentedRow(
                    options = CREATABLE_ARCHIVE_FORMATS,
                    label = { it.label },
                    isSelected = { settings.defaultArchiveType == it },
                    onSelect = { viewModel.updateDefaultArchiveType(it) },
                    height = 40.dp,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/** Rounded card container shared by every settings group. */
@Composable
private fun SettingsTile(content: @Composable () -> Unit) {
    Surface(
        shape = ChunkyTileShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

/** Title + caption row with a trailing switch. */
@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}

/** Equal-width pill selector row (theme mode, view mode, archive format...). */
@Composable
private fun <T> SegmentedRow(
    options: List<T>,
    label: (T) -> String,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    height: Dp = 44.dp,
    fontSize: TextUnit = 12.sp
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val selected = isSelected(option)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .clip(PillShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(option),
                    fontSize = fontSize,
                    fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
    ) {
        CookieIconContainer(
            backgroundColor = MaterialTheme.colorScheme.primary,
            size = 32.dp,
            shape = ChunkyIconShape
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

