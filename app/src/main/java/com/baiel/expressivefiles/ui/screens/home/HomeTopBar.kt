package com.baiel.expressivefiles.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baiel.expressivefiles.R
import com.baiel.expressivefiles.model.ViewMode
import com.baiel.expressivefiles.ui.components.ChunkyIconButton
import com.baiel.expressivefiles.ui.theme.ExpressiveAsymmetricShape
import com.baiel.expressivefiles.ui.theme.ExpressiveRoundedShape
import com.baiel.expressivefiles.ui.theme.PillShape
import com.baiel.expressivefiles.ui.theme.osIconButtonShape

private enum class TopBarMode { STANDARD, SEARCH, SELECTION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSelectionMode: Boolean,
    selectedPathsCount: Int,
    onSelectAll: () -> Unit,
    isAllSelected: Boolean,
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit,
    showSortBar: Boolean,
    onToggleSortBar: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    Column {
    TopAppBar(
        title = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                AnimatedContent(
                    targetState = when {
                        isSearchActive -> TopBarMode.SEARCH
                        isSelectionMode -> TopBarMode.SELECTION
                        else -> TopBarMode.STANDARD
                    },
                    transitionSpec = {
                        if (targetState == TopBarMode.SEARCH) {
                            // Search expands from the search icon position
                            // towards the right edge of the bar.
                            (fadeIn(tween(220)) + expandHorizontally(
                                expandFrom = Alignment.Start,
                                animationSpec = tween(220)
                            )) togetherWith
                                    (fadeOut(tween(180)) + shrinkHorizontally(
                                        shrinkTowards = Alignment.Start,
                                        animationSpec = tween(180)
                                    ))
                        } else {
                            (fadeIn(tween(220)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220))) togetherWith
                                    (fadeOut(tween(180)) + scaleOut(targetScale = 0.92f, animationSpec = tween(180)))
                        }
                    },
                    label = "topbar_title"
                ) { mode ->
                    when (mode) {
                        TopBarMode.SEARCH -> SearchTextField(
                            query = searchQuery,
                            onQueryChange = onSearchQueryChange,
                            onClear = { onSearchQueryChange("") },
                            focusRequester = focusRequester
                        )
                        TopBarMode.SELECTION -> Text(
                            text = pluralStringResource(
                                R.plurals.topbar_selected_count,
                                selectedPathsCount,
                                selectedPathsCount
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Main title lives in the bar itself (same row as the
                        // actions) so it stays aligned; font/style untouched,
                        // single-line ellipsis keeps it fitted in the slot.
                        TopBarMode.STANDARD -> Text(
                            text = stringResource(R.string.app_title),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        actions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = isSearchActive,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(initialScale = 0.85f)) togetherWith
                                (fadeOut(tween(180)) + scaleOut(targetScale = 0.85f))
                    },
                    label = "topbar_actions_search"
                ) { searching ->
                    if (searching) {
                        // While search is active every other action is hidden;
                        // only the X cancel button remains.
                        ChunkyIconButton(
                            icon = Icons.Rounded.Close,
                            onClick = {
                                onSearchQueryChange("")
                                onSearchActiveChange(false)
                            },
                            contentDescription = stringResource(R.string.topbar_clear_search)
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChunkyIconButton(
                                icon = Icons.Rounded.Search,
                                onClick = { onSearchActiveChange(true) },
                                contentDescription = stringResource(R.string.topbar_search),
                                shape = osIconButtonShape(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))

                            ChunkyIconButton(
                                icon = when (viewMode) {
                                    ViewMode.LIST -> Icons.AutoMirrored.Rounded.ViewList
                                    ViewMode.GRID -> Icons.Rounded.GridView
                                    ViewMode.EXPRESSIVE_CARDS -> Icons.Rounded.Dashboard
                                },
                                onClick = {
                                    val modes = ViewMode.entries
                                    onViewModeChange(modes[(viewMode.ordinal + 1) % modes.size])
                                },
                                contentDescription = stringResource(R.string.topbar_switch_view),
                                shape = osIconButtonShape(ExpressiveRoundedShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))

                            // Persistent quick-sort toggle for the strip below the bar;
                            // lit up whenever that strip is open.
                            ChunkyIconButton(
                                icon = Icons.AutoMirrored.Rounded.Sort,
                                onClick = onToggleSortBar,
                                contentDescription = stringResource(R.string.topbar_sort),
                                shape = osIconButtonShape(ExpressiveAsymmetricShape),
                                backgroundColor =
                                    if (showSortBar) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                tint =
                                    if (showSortBar) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))

                            // Mode-specific actions (Selection vs Standard)
                            AnimatedContent(
                                targetState = isSelectionMode,
                                transitionSpec = {
                                    (fadeIn(tween(220)) + scaleIn(initialScale = 0.85f)) togetherWith
                                            (fadeOut(tween(180)) + scaleOut(targetScale = 0.85f))
                                },
                                label = "topbar_actions_dynamic"
                            ) { selecting ->
                                if (selecting) {
                                    SelectionActions(
                                        onSelectAll = onSelectAll,
                                        isAllSelected = isAllSelected
                                    )
                                } else {
                                    StandardActions(onNavigateToSettings = onNavigateToSettings)
                                }
                            }
                        }
                    }
                }
            }
        },
        // Transparent: the bar floats on the solid themed sheet painted by
        // HomeScreen - the visible fill comes from that wrapper, not from
        // the TopAppBar itself.
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
    }
}

@Composable
private fun SearchTextField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester
) {
    // Request focus after the animated search subtree has actually attached.
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    // Thin inline field: fixed 44dp height so it stays inside the top bar
    // instead of stretching it full-screen tall.
    Surface(
        shape = ExpressiveRoundedShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(end = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.topbar_search_hint),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.topbar_clear_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionActions(
    onSelectAll: () -> Unit,
    isAllSelected: Boolean
) {
    // Toggle button: select all when something is missing, deselect all
    // once every listed item is already picked. Capsule silhouette so the
    // selection toolbar doesn't repeat the main bar's circle/cookie shapes.
    ChunkyIconButton(
        icon = if (isAllSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
        onClick = onSelectAll,
        shape = osIconButtonShape(PillShape),
        contentDescription = stringResource(
            if (isAllSelected) R.string.topbar_deselect_all else R.string.topbar_select_all
        )
    )
}

@Composable
private fun StandardActions(
    onNavigateToSettings: () -> Unit
) {
    ChunkyIconButton(
        icon = Icons.Rounded.Settings,
        onClick = onNavigateToSettings,
        contentDescription = stringResource(R.string.topbar_settings)
    )
}
