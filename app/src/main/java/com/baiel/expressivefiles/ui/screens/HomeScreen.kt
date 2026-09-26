package com.baiel.expressivefiles.ui.screens

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baiel.expressivefiles.R
import com.baiel.expressivefiles.model.FileItem
import com.baiel.expressivefiles.ui.components.ChunkyButton
import com.baiel.expressivefiles.ui.components.CookieIconContainer
import com.baiel.expressivefiles.ui.components.DialogActionType
import com.baiel.expressivefiles.ui.components.elasticScrollObserver
import com.baiel.expressivefiles.ui.components.rememberElasticScrollState
import com.baiel.expressivefiles.ui.screens.home.DirectoryPage
import com.baiel.expressivefiles.ui.screens.home.HomeBottomActionStrip
import com.baiel.expressivefiles.ui.screens.home.HomeTopBar
import com.baiel.expressivefiles.ui.theme.ChunkyIconShape
import com.baiel.expressivefiles.ui.theme.ChunkyTileShape
import com.baiel.expressivefiles.ui.components.fileTypeIcon
import com.baiel.expressivefiles.ui.theme.osIconButtonShape
import com.baiel.expressivefiles.viewmodel.FileViewModel
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.CancellationException
import java.io.File
import kotlin.math.roundToInt

/** Tap-routing callbacks hoisted into one remembered instance for stability. */
private class HomeCallbacks(private val viewModel: FileViewModel) {
    // Tap routing reads the ViewModel's source of truth AT INVOCATION TIME.
    // Even if composition state were momentarily stale, a tap while items are
    // selected always toggles, and a tap with empty selection always opens.
    val onFileClick: (FileItem) -> Unit = { item ->
        if (viewModel.selectedPaths.value.isNotEmpty()) viewModel.toggleSelection(item)
        else viewModel.openFile(item)
    }
    val onFileLongClick: (FileItem) -> Unit = { item -> viewModel.toggleSelection(item) }
    val onFileMoreClick: (FileItem) -> Unit = { item -> viewModel.showActionSheet(item) }
    val onNavigateToDir: (File) -> Unit = { dir -> viewModel.navigateToDirectory(dir) }
    val onNavigateBack: () -> Unit = {
        viewModel.navigateBack()
    }
}

@OptIn(ExperimentalHazeApi::class)
@Composable
fun HomeScreen(
    viewModel: FileViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToStorageAnalysis: () -> Unit,
) {
    val context = LocalContext.current
    // Only screen-level state is collected here. High-frequency or dialog-scoped
    // flows (archive progress, action sheets, metadata spinner, storage stats...)
    // are collected inside their consumer composables so their emissions never
    // recompose the file list.
    val files by viewModel.files.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val currentDirectory by viewModel.currentDirectory.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedPaths by viewModel.selectedPaths.collectAsStateWithLifecycle()
    val isAllSelected by viewModel.isAllSelected.collectAsStateWithLifecycle()
    val filesDirectory by viewModel.filesDirectory.collectAsStateWithLifecycle()
    val unreadableFolder by viewModel.unreadableFolder.collectAsStateWithLifecycle()
    val categorySorts by viewModel.categorySorts.collectAsStateWithLifecycle()

    // Saveable: a rotation/language change recreates the activity while the
    // ViewModel keeps searchQuery - with a plain `remember` the search field
    // vanished but the listing stayed filtered (and Back then exited the app
    // instead of clearing the query).
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var showSortBar by rememberSaveable { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }

    // THE blur capture for the whole Home screen, hoisted here and passed
    // down: the file list records itself once (inside DirectoryPage), and the
    // glass top bar, FAB menu, strips and fade bands all sample that single
    // capture. Two states used to nest sources - a full-page record per frame
    // that composited every blur output twice.
    val hazeState = rememberHazeState()
    // Elastic scroll-follow displacement for the FAB (see ElasticScrollState).
    // A tighter cap than the strip: the heavier element should move less.
    val fabElastic = rememberElasticScrollState(maxOffset = 12.dp)

    // Plain derivation from the collected state: no derivedStateOf/remembered
    // lambda layers anywhere in the tap-routing path, so a stale flag can never
    // swallow selection clicks or hide the action strip.
    val isSelectionMode = selectedPaths.isNotEmpty()

    // Predictive back gesture state
    var backProgress by remember { mutableFloatStateOf(0f) }
    var isBackInProgress by remember { mutableStateOf(false) }

    // Double-back-to-exit: at the root with nothing to pop, the first back
    // press arms a short window; only a second press within it exits. Uses
    // elapsedRealtime so a user-adjusted wall clock cannot break the window.
    var lastBackExitAttempt by remember { mutableLongStateOf(0L) }
    val callbacks = remember(viewModel) { HomeCallbacks(viewModel) }
    // Hoisted: the back-handler coroutine is not a composable scope.
    val exitToastText = stringResource(R.string.back_press_again_to_exit)

    // Entering selection mode hides the FAB; close its menu so it cannot
    // resurface in an already-open state after the selection ends.
    LaunchedEffect(isSelectionMode) {
        if (isSelectionMode) showFabMenu = false
    }

    PredictiveBackHandler(enabled = true) { progressFlow ->
        try {
            isBackInProgress = true
            progressFlow.collect { backEvent ->
                backProgress = backEvent.progress
            }

            // Gesture completed: now evaluate the back action. State is read
            // at invocation time so the decision always reflects what is on
            // screen right now, not the values captured when the handler was
            // (re)composed.
            val canNavigateBack = viewModel.selectedPaths.value.isNotEmpty() ||
                    showFabMenu || isSearchActive || showSortBar ||
                    (viewModel.selectedCategory.value != null) ||
                    viewModel.hasBackStack ||
                    (viewModel.currentDirectory.value.absolutePath !=
                        viewModel.rootDirectory.absolutePath)
            if (!canNavigateBack) {
                // Root state: require a second back press within the window to exit.
                val now = SystemClock.elapsedRealtime()
                if (now - lastBackExitAttempt < 2000L) {
                    (context as? android.app.Activity)?.finish()
                } else {
                    lastBackExitAttempt = now
                    android.widget.Toast.makeText(
                        context,
                        exitToastText,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                if (showFabMenu) {
                    showFabMenu = false
                } else if (isSearchActive) {
                    isSearchActive = false
                    viewModel.setSearchQuery("")
                } else if (showSortBar) {
                    showSortBar = false
                } else if (selectedCategory != null) {
                    viewModel.setCategoryFilter(null)
                } else {
                    viewModel.navigateBack()
                }
            }
        } catch (_: CancellationException) {
            // Back gesture was canceled/aborted by the user.
        } finally {
            isBackInProgress = false
            backProgress = 0f
        }
    }

    Scaffold(
        floatingActionButton = {
            // FAB stays visible during selection and clipboard actions, sliding up
            // so the bottom action strip doesn't cover it.
            val hasClipboard by viewModel.clipboard.collectAsStateWithLifecycle()
            val fabOffset by animateDpAsState(
                targetValue = if (isSelectionMode || hasClipboard != null) 92.dp else 0.dp,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 340f),
                label = "fab_selection_offset"
            )
            Box(
                modifier = Modifier
                    .padding(bottom = fabOffset.coerceAtLeast(0.dp))
                    // Elastic scroll-follow displacement, layered on top of the
                    // selection-mode slide (see ElasticScrollState).
                    .offset { IntOffset(0, fabElastic.offsetPx.roundToInt()) }
            ) {
                HomeFab(
                    hazeState = hazeState,
                    showFabMenu = showFabMenu,
                    onFabClick = { showFabMenu = !showFabMenu },
                    onNewFolder = {
                        showFabMenu = false
                        viewModel.requestNewItem(DialogActionType.NEW_FOLDER)
                    },
                    onNewFile = {
                        showFabMenu = false
                        viewModel.requestNewItem(DialogActionType.NEW_FILE)
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        // The top bar (standard / search / multiselect modes) floats as a solid
        // themed sheet instead of a Scaffold topBar slot, so the page slides
        // under it (the FAB menu glass still samples the content). Chrome the
        // page must clear:
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        var topChromeHeightPx by remember { mutableIntStateOf(0) }
        val topChromeHeight = if (topChromeHeightPx > 0) {
            with(LocalDensity.current) { topChromeHeightPx.toDp() }
        } else statusBarTop + 64.dp
        // Predictive-back preview shared by the page and the top bar, so
        // both shrink/shift as one card during the gesture.
        val backShiftPx = with(LocalDensity.current) { 16.dp.toPx() }
        val backPreview: (Modifier) -> Modifier = { node ->
            node.graphicsLayer {
                // Progress resolves to 0 outside a gesture and EVERY property
                // is assigned on every frame: graphicsLayer state persists
                // between draws, so conditionally skipping assignments would
                // strand the screen shifted/clipped after the gesture ends or
                // is canceled.
                //
                // The preview is slide + fade + corner rounding ONLY - no
                // scale. Haze computes the frosted strips' backdrop offsets
                // from on-screen positions (which include ancestor transforms)
                // while the captured listing replay is unscaled, so any
                // ancestor scale makes the blurred copy drift away from the
                // real rows (items appear duplicated, splitting from the
                // middle). Translation, alpha and clip cancel out in that
                // math; scale is the one transform that breaks it.
                val progress = if (isBackInProgress) backProgress else 0f
                translationX = progress * backShiftPx
                alpha = 1f - (progress * 0.08f)
                shape = RoundedCornerShape((progress * 14f).dp)
                clip = isBackInProgress
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Full-bleed INCLUDING the navigation bar: the listing scrolls
                // under it so the bottom frost fade reaches the screen edge.
                // Insets are handled per child (strip, list padding).
                // Any tap anywhere closes the open FAB menu - observed without
                // consuming, so the tap still reaches the button or row below.
                .pointerInput(showFabMenu) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        if (showFabMenu) showFabMenu = false
                    }
                }
                // Same for any scroll: the menu gets out of the way but the
                // list keeps scrolling underneath it.
                .nestedScroll(remember(showFabMenu) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            available // unused: the connection only reacts, it never consumes
                            source // unused: only drag/fling scrolling is observed here
                            if (showFabMenu) showFabMenu = false
                            return Offset.Zero
                        }
                    }
                })
                // Elastic scroll-follow: the FAB drifts with the listing scroll
                // and springs back when scrolling stops (see ElasticScrollState).
                .elasticScrollObserver(fabElastic)
                .let(backPreview)
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Quick sort bar now renders inside DirectoryPage as a floating
                // glass segment under the type strip (same recipe, drift and
                // pop) - it takes no layout space here anymore, so toggling it
                // no longer shoves the whole page down.
                DirectoryPage(
                directory = currentDirectory,
                rootDirectory = viewModel.rootDirectory,
                files = files,
                isLoading = isLoading,
                // Quick sort bar state: DirectoryPage renders it as a floating
                // glass segment. The mode/order resolution lives here (per-
                // category sort when a filter chip is active, else the global
                // listing sort) so the page only receives plain values.
                showSortBar = showSortBar,
                sortMode = selectedCategory?.let { categorySorts[it.name] }?.first ?: settings.sortMode,
                sortOrder = selectedCategory?.let { categorySorts[it.name] }?.second ?: settings.sortOrder,
                onSortChange = { mode, order ->
                    val activeCategory = selectedCategory
                    if (activeCategory != null) viewModel.setCategorySort(activeCategory, mode, order)
                    else viewModel.updateSort(mode, order)
                },
                showStorageOverview = settings.showStorageOverview,
                storageOverviewCollapsed = settings.storageOverviewCollapsed,
                storageStatsFlow = viewModel.storageStats,
                hazeState = hazeState,
                selectedCategory = selectedCategory,
                searchQuery = searchQuery,
                isSearchActive = isSearchActive,
                viewMode = settings.viewMode,
                isSelectionMode = isSelectionMode,
                selectedPaths = selectedPaths,
                filesDirectory = filesDirectory,
                unreadableFolder = unreadableFolder,
                // Height of the floating top bar the page is full-bleed
                // under; hero + floating overlays clear it inside the page.
                topChromeHeight = topChromeHeight,
                onNavigateToDir = callbacks.onNavigateToDir,
                onNavigateBack = callbacks.onNavigateBack,
                onToggleStorageCollapse = { viewModel.toggleStorageOverviewCollapsed() },
                onCategoryClick = { category -> viewModel.setCategoryFilter(category) },
                onAnalyzeStorageClick = onNavigateToStorageAnalysis,
                onFileClick = callbacks.onFileClick,
                onFileLongClick = callbacks.onFileLongClick,
                onFileMoreClick = callbacks.onFileMoreClick,
                onRefresh = { viewModel.refreshCurrentDirectory() }
            )
            }

            PermissionGate(
                viewModel = viewModel,
                onGrant = {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                    context.startActivity(intent)
                }
            )

            HomeBottomActionStrip(
                clipboardFlow = viewModel.clipboard,
                isSelectionMode = isSelectionMode,
                selectedCount = selectedPaths.size,
                onClearSelected = { viewModel.clearSelection() },
                onPaste = { viewModel.pasteToCurrentDirectory() },
                onClearClipboard = { viewModel.clearClipboard() },
                onCopySelected = { viewModel.copySelected() },
                onCutSelected = { viewModel.cutSelected() },
                onCompressSelected = { viewModel.requestCreateArchive() },
                onShareSelected = { viewModel.shareSelectedFiles() },
                onDeleteSelected = { viewModel.deleteSelectedFiles() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // Full-bleed content Box: keep the strip above the
                    // navigation bar (the component's own 16dp stays inside).
                    .padding(bottom = padding.calculateBottomPadding())
            )
            }

            // Top bar: standard title, search field and the multiselect
            // toolbar float transparently above the full-bleed listing. No
            // background is drawn here: the progressive blur band at the top
            // of DirectoryPage now spans this whole area (full frost at the
            // screen edge melting downward), so the bar and the frost read
            // as one continuous surface with no hard bottom edge. In search
            // mode that band is dropped (DirectoryPage), so the field floats
            // on the bare listing. Lives OUTSIDE the list's subtree so it
            // never samples its own pixels.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onSizeChanged { topChromeHeightPx = it.height }
            ) {
                HomeTopBar(
                    isSearchActive = isSearchActive,
                    onSearchActiveChange = { isSearchActive = it },
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    isSelectionMode = isSelectionMode,
                    selectedPathsCount = selectedPaths.size,
                    isAllSelected = isAllSelected,
                    onSelectAll = { viewModel.selectAll() },
                    viewMode = settings.viewMode,
                    onViewModeChange = { viewModel.updateViewMode(it) },
                    showSortBar = showSortBar,
                    onToggleSortBar = { showSortBar = !showSortBar },
                    onNavigateToSettings = onNavigateToSettings
                )
            }

        }
    }
}

/** Collects permission state locally so it never recomposes the screen. */
@Composable
private fun PermissionGate(
    viewModel: FileViewModel,
    onGrant: () -> Unit
) {
    val hasPermission by viewModel.hasStoragePermission.collectAsStateWithLifecycle()
    var dismissedByUser by rememberSaveable { mutableStateOf(false) }

    // Re-armed whenever access is granted so a later revoke re-prompts once.
    LaunchedEffect(hasPermission) {
        if (hasPermission) dismissedByUser = false
    }

    if (!hasPermission && !dismissedByUser) {
        Dialog(onDismissRequest = { dismissedByUser = true }) {
            Surface(
                shape = ChunkyTileShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CookieIconContainer(
                            backgroundColor = MaterialTheme.colorScheme.error,
                            size = 44.dp,
                            shape = ChunkyIconShape
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderZip,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.perm_all_files_access_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.perm_all_files_access_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChunkyButton(
                            text = stringResource(R.string.action_not_now),
                            onClick = { dismissedByUser = true },
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        ChunkyButton(
                            text = stringResource(R.string.action_grant),
                            onClick = onGrant,
                            backgroundColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalHazeApi::class)
@Composable
private fun HomeFab(
    hazeState: HazeState,
    showFabMenu: Boolean,
    onFabClick: () -> Unit,
    onNewFolder: () -> Unit,
    onNewFile: () -> Unit
) {
    val view = LocalView.current
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val buttonWidth = with(density) { (windowInfo.containerSize.width * 0.65f).toDp().coerceAtMost(280.dp) }

    // Shape morph: squircle pill when closed -> full circle when open,
    // while the plus rotates into an X. Purely draw-time properties.
    val fabScale by animateFloatAsState(
        targetValue = if (showFabMenu) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 380f),
        label = "fab_cookie_scale"
    )
    val fabRotation by animateFloatAsState(
        targetValue = if (showFabMenu) 45f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 380f),
        label = "fab_icon_rotation"
    )

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AnimatedVisibility(
            visible = showFabMenu,
            // Subtle: a short fade with a quarter-height glide, no big slide.
            enter = fadeIn(tween(160)) + slideInVertically(spring(dampingRatio = 0.6f, stiffness = 380f)) { it / 3 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { it / 6 }
        ) {
            // Frosted glass panel: replays the file listing blurred behind the
            // menu actions, matching the floating filter strip treatment.
            val isDarkBg = MaterialTheme.colorScheme.background.luminance() < 0.5f
            val menuTint = if (isDarkBg) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
                           else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .clip(RoundedCornerShape(26.dp))
                    .hazeEffect(hazeState) {
                        blurRadius = 12.dp
                        tints = listOf(HazeTint(menuTint))
                        noiseFactor = 0f
                        inputScale = HazeInputScale.Fixed(0.66f)
                    }
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChunkyButton(
                        text = stringResource(R.string.fab_new_folder),
                        icon = Icons.Rounded.CreateNewFolder,
                        onClick = onNewFolder,
                        height = 56.dp,
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.width(buttonWidth)
                    )
                    ChunkyButton(
                        text = stringResource(R.string.fab_new_file),
                        icon = Icons.AutoMirrored.Rounded.NoteAdd,
                        onClick = onNewFile,
                        height = 56.dp,
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.width(buttonWidth)
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onFabClick()
            },
            shape = osIconButtonShape(),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            elevation = FloatingActionButtonDefaults.elevation(6.dp),
            modifier = Modifier.size(80.dp).graphicsLayer { scaleX = fabScale; scaleY = fabScale }
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = if (showFabMenu) stringResource(R.string.fab_close_actions) else stringResource(R.string.fab_add_actions),
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer { rotationZ = fabRotation }
            )
        }
    }
}
