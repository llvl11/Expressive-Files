package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import com.example.model.ArchiveType
import com.example.model.ClipboardAction
import com.example.model.FileItem
import com.example.model.FileType
import com.example.model.SortMode
import com.example.model.SortOrder
import com.example.model.ViewMode
import com.example.ui.components.ArchiveProgressDialog
import com.example.ui.components.ArchiveViewerSheet
import com.example.ui.components.BreadcrumbBar
import com.example.ui.components.ChunkyButton
import com.example.ui.components.ChunkyChip
import com.example.ui.components.ChunkyIconButton
import com.example.ui.components.CookieBadge
import com.example.ui.components.CookieIconContainer
import com.example.ui.components.CreateArchiveDialog
import com.example.ui.components.DialogActionType
import com.example.ui.components.FileActionSheet
import com.example.ui.components.FileExpressiveCard
import com.example.ui.components.FileGridCard
import com.example.ui.components.FileListCard
import com.example.ui.components.FilePreviewModal
import com.example.ui.components.NewItemDialog
import com.example.ui.components.StorageHeroCard
import com.example.ui.theme.ArchiveZipColor
import com.example.ui.theme.AudioColor
import com.example.ui.theme.ChunkyIconShape
import com.example.ui.theme.ChunkyTileShape
import com.example.ui.theme.DocumentColor
import com.example.ui.theme.ImageColor
import com.example.ui.theme.PillShape
import com.example.ui.theme.VideoColor
import com.example.viewmodel.FileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: FileViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToStorageAnalysis: () -> Unit
) {
    val context = LocalContext.current
    val files by viewModel.files.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentDirectory by viewModel.currentDirectory.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedPaths by viewModel.selectedPaths.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()
    val storageStats by viewModel.storageStats.collectAsState()
    val archiveProgress by viewModel.archiveProgress.collectAsState()
    val hasPermission by viewModel.hasStoragePermission.collectAsState()

    // Dialog state collectors
    val showCreateArchive by viewModel.showCreateArchiveDialog.collectAsState()
    val showNewItem by viewModel.showNewItemDialog.collectAsState()
    val activeActionSheetItem by viewModel.activeActionSheetItem.collectAsState()
    val activePreviewItem by viewModel.activePreviewItem.collectAsState()
    val activeArchiveInspectItem by viewModel.activeArchiveInspectItem.collectAsState()
    val renameTargetItem by viewModel.renameTargetItem.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }

    val isSelectionMode = selectedPaths.isNotEmpty()

    // Predictive back gesture state
    var backProgress by remember { mutableFloatStateOf(0f) }
    var isBackInProgress by remember { mutableStateOf(false) }

    val canNavigateBack = showFabMenu || isSearchActive || (currentDirectory.absolutePath != viewModel.rootDirectory.absolutePath)

    PredictiveBackHandler(enabled = canNavigateBack) { progressFlow ->
        try {
            isBackInProgress = true
            progressFlow.collect { backEvent ->
                backProgress = backEvent.progress
            }
            // Gesture finished: execute back navigation action
            if (showFabMenu) {
                showFabMenu = false
            } else if (isSearchActive) {
                isSearchActive = false
                viewModel.setSearchQuery("")
            } else {
                viewModel.navigateBack()
            }
        } catch (e: CancellationException) {
            // Cancelled back gesture
        } finally {
            isBackInProgress = false
            backProgress = 0f
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        Surface(
                            shape = PillShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .padding(end = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
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
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search files, archives...",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { viewModel.setSearchQuery(it) },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.setSearchQuery("") },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Clear search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else if (isSelectionMode) {
                        Text(
                            text = "${selectedPaths.size} Selected",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Files",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-1).sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        ChunkyIconButton(
                            icon = Icons.Rounded.SelectAll,
                            onClick = {
                                if (selectedPaths.size == files.size) {
                                    viewModel.clearSelection()
                                } else {
                                    viewModel.selectAll()
                                }
                            },
                            contentDescription = "Select All"
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        ChunkyIconButton(
                            icon = Icons.Rounded.Close,
                            onClick = { viewModel.clearSelection() },
                            contentDescription = "Clear Selection"
                        )
                    } else {
                        // Search Button
                        ChunkyIconButton(
                            icon = if (isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                            onClick = {
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) viewModel.setSearchQuery("")
                            },
                            contentDescription = "Search"
                        )
                        Spacer(modifier = Modifier.width(6.dp))

                        // View Mode Toggle
                        ChunkyIconButton(
                            icon = when (settings.viewMode) {
                                ViewMode.LIST -> Icons.Rounded.ViewList
                                ViewMode.GRID -> Icons.Rounded.GridView
                                ViewMode.EXPRESSIVE_CARDS -> Icons.Rounded.Dashboard
                            },
                            onClick = {
                                val nextMode = when (settings.viewMode) {
                                    ViewMode.LIST -> ViewMode.GRID
                                    ViewMode.GRID -> ViewMode.EXPRESSIVE_CARDS
                                    ViewMode.EXPRESSIVE_CARDS -> ViewMode.LIST
                                }
                                viewModel.updateViewMode(nextMode)
                            },
                            contentDescription = "Switch View"
                        )
                        Spacer(modifier = Modifier.width(6.dp))

                        // Sort Menu Button
                        Box {
                            ChunkyIconButton(
                                icon = Icons.Rounded.Sort,
                                onClick = { showSortMenu = true },
                                contentDescription = "Sort"
                            )
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                shape = ChunkyTileShape,
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Name (A → Z)", fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        viewModel.updateSort(SortMode.NAME, SortOrder.ASCENDING)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (settings.sortMode == SortMode.NAME && settings.sortOrder == SortOrder.ASCENDING) {
                                            Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Name (Z → A)", fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        viewModel.updateSort(SortMode.NAME, SortOrder.DESCENDING)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (settings.sortMode == SortMode.NAME && settings.sortOrder == SortOrder.DESCENDING) {
                                            Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Size: Big → Small", fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        viewModel.updateSort(SortMode.SIZE, SortOrder.DESCENDING)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (settings.sortMode == SortMode.SIZE && settings.sortOrder == SortOrder.DESCENDING) {
                                            Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Size: Small → Big", fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        viewModel.updateSort(SortMode.SIZE, SortOrder.ASCENDING)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (settings.sortMode == SortMode.SIZE && settings.sortOrder == SortOrder.ASCENDING) {
                                            Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Date (Newest first)", fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        viewModel.updateSort(SortMode.DATE, SortOrder.DESCENDING)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (settings.sortMode == SortMode.DATE && settings.sortOrder == SortOrder.DESCENDING) {
                                            Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Date (Oldest first)", fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        viewModel.updateSort(SortMode.DATE, SortOrder.ASCENDING)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (settings.sortMode == SortMode.DATE && settings.sortOrder == SortOrder.ASCENDING) {
                                            Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("File Type", fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        viewModel.updateSort(SortMode.TYPE, SortOrder.ASCENDING)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (settings.sortMode == SortMode.TYPE) {
                                            Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))

                        // Settings Button
                        ChunkyIconButton(
                            icon = Icons.Rounded.Settings,
                            onClick = onNavigateToSettings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (!isSelectionMode && clipboard == null) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Secondary FAB items when menu open
                    AnimatedVisibility(
                        visible = showFabMenu,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ChunkyButton(
                                text = "New Folder",
                                icon = Icons.Rounded.CreateNewFolder,
                                onClick = {
                                    showFabMenu = false
                                    viewModel.showNewItemDialog.value = DialogActionType.NEW_FOLDER
                                },
                                height = 46.dp,
                                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            ChunkyButton(
                                text = "New File",
                                icon = Icons.Rounded.NoteAdd,
                                onClick = {
                                    showFabMenu = false
                                    viewModel.showNewItemDialog.value = DialogActionType.NEW_FILE
                                },
                                height = 46.dp,
                                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    // Main FAB
                    FloatingActionButton(
                        onClick = { showFabMenu = !showFabMenu },
                        shape = RoundedCornerShape(22.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(6.dp),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = if (showFabMenu) Icons.Rounded.Close else Icons.Rounded.Add,
                            contentDescription = "Add Actions",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .graphicsLayer {
                    if (isBackInProgress) {
                        val scale = 1f - (backProgress * 0.08f)
                        scaleX = scale
                        scaleY = scale
                        translationX = backProgress * 36f
                        alpha = 1f - (backProgress * 0.15f)
                        shape = RoundedCornerShape((backProgress * 24f).dp)
                        clip = true
                    }
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Permission Notice if needed
                if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = ChunkyTileShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "All Files Access",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Grant storage access for full device file and 7z/tar/zip/rar archive extraction.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            ChunkyButton(
                                text = "Grant",
                                onClick = {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                },
                                height = 36.dp,
                                backgroundColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        }
                    }
                }

                // Storage Overview Hero Card (shown at storage root if enabled in settings)
                val isAtRoot = currentDirectory.absolutePath == viewModel.rootDirectory.absolutePath
                if (settings.showStorageOverview && isAtRoot && !isSearchActive && selectedCategory == null) {
                    StorageHeroCard(
                        stats = storageStats,
                        isCollapsed = settings.storageOverviewCollapsed,
                        onToggleCollapse = { viewModel.toggleStorageOverviewCollapsed() },
                        onCategoryClick = { category ->
                            viewModel.setCategoryFilter(category)
                        },
                        onAnalyzeStorageClick = onNavigateToStorageAnalysis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                // Category Chips Row (Horizontal Scroll)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChunkyChip(
                        text = "All Files",
                        selected = selectedCategory == null,
                        onClick = { viewModel.setCategoryFilter(null) },
                        icon = Icons.Rounded.Folder
                    )
                    ChunkyChip(
                        text = "Archives",
                        selected = selectedCategory == FileType.ARCHIVE,
                        onClick = { viewModel.setCategoryFilter(FileType.ARCHIVE) },
                        icon = Icons.Rounded.FolderZip,
                        badgeText = "7z/zip/tar/rar"
                    )
                    ChunkyChip(
                        text = "APKs",
                        selected = selectedCategory == FileType.APK,
                        onClick = { viewModel.setCategoryFilter(FileType.APK) },
                        icon = Icons.Rounded.Android
                    )
                    ChunkyChip(
                        text = "Images",
                        selected = selectedCategory == FileType.IMAGE,
                        onClick = { viewModel.setCategoryFilter(FileType.IMAGE) },
                        icon = Icons.Rounded.Image
                    )
                    ChunkyChip(
                        text = "Documents",
                        selected = selectedCategory == FileType.DOCUMENT,
                        onClick = { viewModel.setCategoryFilter(FileType.DOCUMENT) },
                        icon = Icons.Rounded.Description
                    )
                    ChunkyChip(
                        text = "Videos",
                        selected = selectedCategory == FileType.VIDEO,
                        onClick = { viewModel.setCategoryFilter(FileType.VIDEO) },
                        icon = Icons.Rounded.Movie
                    )
                    ChunkyChip(
                        text = "Audio",
                        selected = selectedCategory == FileType.AUDIO,
                        onClick = { viewModel.setCategoryFilter(FileType.AUDIO) },
                        icon = Icons.Rounded.Audiotrack
                    )
                }

                // Breadcrumb path navigation bar
                BreadcrumbBar(
                    currentDirectory = currentDirectory,
                    rootDirectory = viewModel.rootDirectory,
                    onNavigateToDir = { viewModel.navigateToDirectory(it) },
                    onNavigateBack = { viewModel.navigateBack() }
                )

                // Files List / Grid / Expressive Cards View with Folder Enter/Exit Animations
                AnimatedContent(
                    targetState = currentDirectory.absolutePath,
                    transitionSpec = {
                        val isEntering = targetState.length > initialState.length
                        if (isEntering) {
                            (slideInHorizontally(animationSpec = tween(280)) { width -> width / 3 } + fadeIn(tween(280)))
                                .togetherWith(slideOutHorizontally(animationSpec = tween(220)) { width -> -width / 3 } + fadeOut(tween(200)))
                        } else {
                            (slideInHorizontally(animationSpec = tween(280)) { width -> -width / 3 } + fadeIn(tween(280)))
                                .togetherWith(slideOutHorizontally(animationSpec = tween(220)) { width -> width / 3 } + fadeOut(tween(200)))
                        }
                    },
                    label = "folder_navigation_animation",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { _ ->
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (isLoading) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Loading files...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (files.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CookieIconContainer(
                                    backgroundColor = MaterialTheme.colorScheme.primary,
                                    size = 72.dp,
                                    shape = ChunkyIconShape
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FolderZip,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "No matching files" else "Folder is empty",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "Try searching for a different keyword"
                                           else "Tap + below to create a folder, file, or compress archives",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            when (settings.viewMode) {
                                ViewMode.GRID -> {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(3),
                                        contentPadding = PaddingValues(
                                            start = 16.dp,
                                            end = 16.dp,
                                            top = 8.dp,
                                            bottom = if (isSelectionMode || clipboard != null) 100.dp else 80.dp
                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(files, key = { it.path }) { item ->
                                            FileGridCard(
                                                item = item,
                                                isSelectionMode = isSelectionMode,
                                                onClick = {
                                                    if (isSelectionMode) {
                                                        viewModel.toggleSelection(item)
                                                    } else {
                                                        viewModel.openFile(item)
                                                    }
                                                },
                                                onLongClick = {
                                                    viewModel.toggleSelection(item)
                                                },
                                                onMoreClick = {
                                                    viewModel.activeActionSheetItem.value = item
                                                }
                                            )
                                        }
                                    }
                                }
                                ViewMode.EXPRESSIVE_CARDS -> {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        contentPadding = PaddingValues(
                                            start = 16.dp,
                                            end = 16.dp,
                                            top = 8.dp,
                                            bottom = if (isSelectionMode || clipboard != null) 100.dp else 80.dp
                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(files, key = { it.path }) { item ->
                                            FileExpressiveCard(
                                                item = item,
                                                isSelectionMode = isSelectionMode,
                                                onClick = {
                                                    if (isSelectionMode) {
                                                        viewModel.toggleSelection(item)
                                                    } else {
                                                        viewModel.openFile(item)
                                                    }
                                                },
                                                onLongClick = {
                                                    viewModel.toggleSelection(item)
                                                },
                                                onMoreClick = {
                                                    viewModel.activeActionSheetItem.value = item
                                                }
                                            )
                                        }
                                    }
                                }
                                ViewMode.LIST -> {
                                    LazyColumn(
                                        contentPadding = PaddingValues(
                                            start = 16.dp,
                                            end = 16.dp,
                                            top = 8.dp,
                                            bottom = if (isSelectionMode || clipboard != null) 100.dp else 80.dp
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(files, key = { it.path }) { item ->
                                            FileListCard(
                                                item = item,
                                                isSelectionMode = isSelectionMode,
                                                onClick = {
                                                    if (isSelectionMode) {
                                                        viewModel.toggleSelection(item)
                                                    } else {
                                                        viewModel.openFile(item)
                                                    }
                                                },
                                                onLongClick = {
                                                    viewModel.toggleSelection(item)
                                                },
                                                onMoreClick = {
                                                    viewModel.activeActionSheetItem.value = item
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Clipboard Paste Bar Floating at Bottom
            AnimatedVisibility(
                visible = clipboard != null && !isSelectionMode,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                clipboard?.let { clip ->
                    Surface(
                        shape = ChunkyTileShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (clip.action == ClipboardAction.COPY) Icons.Rounded.ContentCopy else Icons.Rounded.ContentCut,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "${if (clip.action == ClipboardAction.COPY) "Copy" else "Move"} ${clip.items.size} item(s)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Paste into current folder",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            Row {
                                ChunkyButton(
                                    text = "Paste",
                                    icon = Icons.Rounded.ContentPaste,
                                    onClick = { viewModel.pasteToCurrentDirectory() },
                                    height = 40.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                ChunkyIconButton(
                                    icon = Icons.Rounded.Close,
                                    onClick = { viewModel.clearClipboard() },
                                    size = 40.dp,
                                    shape = PillShape
                                )
                            }
                        }
                    }
                }
            }

            // Selection Mode Bottom Action Strip
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Surface(
                    shape = ChunkyTileShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        // Copy Action
                        ChunkyIconButton(
                            icon = Icons.Rounded.ContentCopy,
                            onClick = { viewModel.copySelected() },
                            size = 44.dp,
                            shape = ChunkyIconShape,
                            contentDescription = "Copy"
                        )

                        // Cut Action
                        ChunkyIconButton(
                            icon = Icons.Rounded.ContentCut,
                            onClick = { viewModel.cutSelected() },
                            size = 44.dp,
                            shape = ChunkyIconShape,
                            contentDescription = "Move"
                        )

                        // Compress to Archive Action
                        ChunkyIconButton(
                            icon = Icons.Rounded.Archive,
                            onClick = { viewModel.showCreateArchiveDialog.value = true },
                            size = 44.dp,
                            shape = ChunkyIconShape,
                            backgroundColor = ArchiveZipColor.copy(alpha = 0.2f),
                            tint = ArchiveZipColor,
                            contentDescription = "Compress Archive"
                        )

                        // Share Action
                        ChunkyIconButton(
                            icon = Icons.Rounded.Share,
                            onClick = { viewModel.shareSelectedFiles() },
                            size = 44.dp,
                            shape = ChunkyIconShape,
                            contentDescription = "Share"
                        )

                        // Delete Action
                        ChunkyIconButton(
                            icon = Icons.Rounded.Delete,
                            onClick = { viewModel.deleteSelectedFiles() },
                            size = 44.dp,
                            shape = ChunkyIconShape,
                            backgroundColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            tint = MaterialTheme.colorScheme.error,
                            contentDescription = "Delete"
                        )
                    }
                }
            }
        }
    }

    // Modal Dialogs & Sheets

    // 1. Create Archive Dialog
    if (showCreateArchive) {
        CreateArchiveDialog(
            selectedFiles = files.filter { it.isSelected },
            defaultFormat = settings.defaultArchiveType,
            onDismiss = { viewModel.showCreateArchiveDialog.value = false },
            onCompress = { name, format ->
                viewModel.createArchive(name, format)
            }
        )
    }

    // 2. New Folder / File Dialog
    showNewItem?.let { type ->
        NewItemDialog(
            type = type,
            onDismiss = { viewModel.showNewItemDialog.value = null },
            onConfirm = { name ->
                viewModel.showNewItemDialog.value = null
                if (type == DialogActionType.NEW_FOLDER) {
                    viewModel.createFolder(name)
                } else {
                    viewModel.createNewFile(name)
                }
            }
        )
    }

    // 3. Rename Dialog
    renameTargetItem?.let { target ->
        NewItemDialog(
            type = DialogActionType.RENAME,
            initialValue = target.name,
            onDismiss = { viewModel.renameTargetItem.value = null },
            onConfirm = { newName ->
                viewModel.renameTargetItem.value = null
                viewModel.renameFile(target, newName)
            }
        )
    }

    // 4. Archive Inspector Sheet
    activeArchiveInspectItem?.let { archiveItem ->
        ArchiveViewerSheet(
            archiveItem = archiveItem,
            onDismiss = { viewModel.activeArchiveInspectItem.value = null },
            onExtractArchive = { item ->
                viewModel.extractArchive(item)
            }
        )
    }

    // 5. File Action Sheet (Context menu)
    activeActionSheetItem?.let { item ->
        FileActionSheet(
            targetItem = item,
            onDismiss = { viewModel.activeActionSheetItem.value = null },
            onOpen = { viewModel.openFile(it) },
            onCopy = {
                viewModel.toggleSelection(it)
                viewModel.copySelected()
            },
            onCut = {
                viewModel.toggleSelection(it)
                viewModel.cutSelected()
            },
            onRename = { viewModel.renameTargetItem.value = it },
            onDelete = { viewModel.deleteSingleFile(it) },
            onShare = { viewModel.shareFile(it) },
            onCompress = {
                viewModel.toggleSelection(it)
                viewModel.showCreateArchiveDialog.value = true
            },
            onExtract = { viewModel.extractArchive(it) },
            onInspectArchive = { viewModel.activeArchiveInspectItem.value = it },
            onDetails = { viewModel.activePreviewItem.value = it }
        )
    }

    // 6. File Preview Modal
    activePreviewItem?.let { item ->
        FilePreviewModal(
            item = item,
            onDismiss = { viewModel.activePreviewItem.value = null },
            onOpenExternal = { viewModel.openFileExternal(it) },
            onShare = { viewModel.shareFile(it) }
        )
    }

    // 7. Archive Progress Dialog
    archiveProgress?.let { progress ->
        ArchiveProgressDialog(
            progress = progress,
            onDismiss = { viewModel.dismissArchiveProgress() }
        )
    }
}
