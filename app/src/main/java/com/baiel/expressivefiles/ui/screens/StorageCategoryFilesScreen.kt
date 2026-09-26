package com.baiel.expressivefiles.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baiel.expressivefiles.R
import com.baiel.expressivefiles.model.FileType
import com.baiel.expressivefiles.ui.components.CategorySortBar
import com.baiel.expressivefiles.ui.components.ChunkyIconButton
import com.baiel.expressivefiles.ui.components.FileListCard
import com.baiel.expressivefiles.ui.screens.home.HomeBottomActionStrip
import com.baiel.expressivefiles.ui.components.fileTypeIcon
import com.baiel.expressivefiles.ui.theme.ChunkyIconShape
import com.baiel.expressivefiles.viewmodel.FileViewModel

@Composable
private fun categoryTitle(type: FileType): String = when (type) {
    FileType.IMAGE -> stringResource(R.string.filter_images)
    FileType.VIDEO -> stringResource(R.string.filter_videos)
    FileType.AUDIO -> stringResource(R.string.filter_audio)
    FileType.DOCUMENT -> stringResource(R.string.filter_docs)
    FileType.ARCHIVE -> stringResource(R.string.filter_archives)
    FileType.APK -> stringResource(R.string.filter_apk)
    FileType.CODE -> stringResource(R.string.filter_code)
    else -> type.name
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StorageCategoryFilesScreen(
    viewModel: FileViewModel,
    category: FileType,
    onNavigateBack: () -> Unit
) {
    LaunchedEffect(category) {
        // Entering a new surface with a stale multi-selection would let bulk
        // actions resolve picks from the previous surface via the shared pool.
        viewModel.clearSelection()
        viewModel.loadCategoryFiles(category)
    }
    DisposableEffect(category) {
        onDispose { viewModel.markCategoryDrillActive(null) }
    }

    val files by viewModel.categoryFiles.collectAsStateWithLifecycle()
    val isLoading by viewModel.isCategoryLoading.collectAsStateWithLifecycle()
    val selectedPaths by viewModel.selectedPaths.collectAsStateWithLifecycle()
    val isSelectionMode = selectedPaths.isNotEmpty()

    // Set when a pull-to-refresh starts, cleared once the reload completes.
    var isRefreshing by remember { mutableStateOf(false) }

    val title = categoryTitle(category)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!isLoading) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.list_items_suffix, files.size, files.size
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    ChunkyIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        onClick = {
                            viewModel.clearSelection()
                            onNavigateBack()
                        },
                        size = 40.dp,
                        shape = ChunkyIconShape,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val sorts by viewModel.categorySorts.collectAsStateWithLifecycle()
            val effectiveSort = sorts[category.name] ?: viewModel.categorySortFor(category)
            CategorySortBar(
                mode = effectiveSort.first,
                order = effectiveSort.second,
                onChange = { mode, order -> viewModel.setCategorySort(category, mode, order) }
            )

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    viewModel.loadCategoryFiles(category)
                },
                modifier = Modifier.fillMaxSize()
            ) {
            AnimatedContent(
                targetState = isLoading to files.isEmpty(),
                transitionSpec = { (fadeIn(tween(200)) togetherWith fadeOut(tween(150))) },
                label = "category_content"
            ) { (loading, empty) ->
                when {
                    loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.category_loading, title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    empty -> Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(fileTypeIcon(category), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.category_none_found, title.lowercase()), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(R.string.category_none_found_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(files, key = { it.path }) { item ->
                            FileListCard(
                                item = item,
                                isSelected = selectedPaths.contains(item.path),
                                isSelectionMode = isSelectionMode,
                                showParentPath = true,
                                // Same invocation-time routing as the home listing:
                                // selection taps always toggle, plain taps open.
                                onClick = {
                                    if (viewModel.selectedPaths.value.isNotEmpty()) viewModel.toggleSelection(item)
                                    else viewModel.openFile(item)
                                },
                                onLongClick = { viewModel.toggleSelection(item) },
                                onMoreClick = { viewModel.showActionSheet(item) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
            }

            // Spinner ends with whichever load the pull triggered.
            LaunchedEffect(isLoading) {
                if (!isLoading) isRefreshing = false
            }

            HomeBottomActionStrip(
                clipboardFlow = viewModel.clipboard,
                isSelectionMode = isSelectionMode,
                selectedCount = selectedPaths.size,
                onClearSelected = { viewModel.clearSelection() },
                // No browsable directory here: pasting would write into whatever
                // folder Home happened to be showing (invisible from this screen).
                onPaste = null,
                onClearClipboard = { viewModel.clearClipboard() },
                onCopySelected = { viewModel.copySelected() },
                onCutSelected = { viewModel.cutSelected() },
                onCompressSelected = { viewModel.requestCreateArchive() },
                onShareSelected = { viewModel.shareSelectedFiles() },
                onDeleteSelected = { viewModel.deleteSelectedFiles() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        }
    }
}
