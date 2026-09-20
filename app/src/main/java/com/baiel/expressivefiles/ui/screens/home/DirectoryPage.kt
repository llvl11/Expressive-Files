package com.baiel.expressivefiles.ui.screens.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.baiel.expressivefiles.ui.components.SquigglyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baiel.expressivefiles.R
import com.baiel.expressivefiles.model.FileItem
import com.baiel.expressivefiles.model.FileType
import com.baiel.expressivefiles.model.SortMode
import com.baiel.expressivefiles.model.SortOrder
import com.baiel.expressivefiles.model.StorageStats
import com.baiel.expressivefiles.model.ViewMode
import com.baiel.expressivefiles.ui.components.BreadcrumbBar
import com.baiel.expressivefiles.ui.components.CategorySortBar
import com.baiel.expressivefiles.ui.components.ChunkyChip
import com.baiel.expressivefiles.ui.components.CookieIconContainer
import com.baiel.expressivefiles.ui.components.FileExpressiveCard
import com.baiel.expressivefiles.ui.components.FileGridCard
import com.baiel.expressivefiles.ui.components.FileListCard
import com.baiel.expressivefiles.ui.components.HazeFadeBand
import com.baiel.expressivefiles.ui.components.StorageHeroCard
import com.baiel.expressivefiles.ui.components.elasticScrollObserver
import com.baiel.expressivefiles.ui.components.fileTypeIcon
import com.baiel.expressivefiles.ui.components.rememberElasticScrollState
import com.baiel.expressivefiles.ui.theme.ChunkyIconShape
import com.baiel.expressivefiles.ui.theme.ChunkyTileShape
import com.baiel.expressivefiles.ui.theme.ChunkySheetShape
import com.baiel.expressivefiles.ui.theme.PillShape
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryPage(
    directory: File,
    rootDirectory: File,
    files: List<FileItem>,
    isLoading: Boolean,
    showStorageOverview: Boolean,
    storageOverviewCollapsed: Boolean,
    // Collected here (only the hero card consumes it) instead of HomeScreen.
    storageStatsFlow: kotlinx.coroutines.flow.StateFlow<StorageStats>,
    // Hoisted blur capture: owned by HomeScreen so the top bar and FAB menu
    // sample the SAME list capture as the strips and bands - one source, one
    // record per frame, no nested double-composite of blur outputs.
    hazeState: HazeState,
    selectedCategory: FileType?,
    searchQuery: String,
    // Quick sort bar: shown as a floating glass segment under the type
    // strip; mode/order are pre-resolved by HomeScreen (per-category when a
    // filter chip is active, else the global listing sort).
    showSortBar: Boolean,
    sortMode: SortMode,
    sortOrder: SortOrder,
    onSortChange: (SortMode, SortOrder) -> Unit,
    viewMode: ViewMode,
    isSelectionMode: Boolean,
    selectedPaths: Set<String>,
    filesDirectory: File?,
    // Height of the floating top bar (status bar + bar) that the page is
    // full-bleed under; the hero card and the floating overlays clear it.
    topChromeHeight: Dp,
    onNavigateToDir: (File) -> Unit,
    onNavigateBack: () -> Unit,
    onToggleStorageCollapse: () -> Unit,
    onCategoryClick: (FileType?) -> Unit,
    onAnalyzeStorageClick: () -> Unit,
    onFileClick: (FileItem) -> Unit,
    onFileLongClick: (FileItem) -> Unit,
    onFileMoreClick: (FileItem) -> Unit,
    onRefresh: () -> Unit = {}
) {
    val storageStats by storageStatsFlow.collectAsStateWithLifecycle()

    // Set when a pull-to-refresh starts, cleared as soon as any load finishes.
    var isRefreshing by remember { mutableStateOf(false) }

    // Measure the entire stack, including animated sort/hero height, rather
    // than guessing each strip's size from its buttons' requested dimensions.
    var chromeHeightPx by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Floating controls share a measured stack above the listing.
        val isAtRoot = directory.absolutePath == rootDirectory.absolutePath
        val breadcrumbVisible = !isAtRoot
        val heroVisible = showStorageOverview && isAtRoot && searchQuery.isEmpty() && selectedCategory == null
        // Quick sort bar: same visibility rule as the type strip (hidden
        // while searching), toggled from the top bar.
        val sortBarVisible = showSortBar && searchQuery.isEmpty()

        // Navigation direction for the folder transition: deeper fades in
        // from the right, going back up mirrors from the left. Tracked from
        // the previous path (siblings and first mount default to the right).
        var lastDirPath by remember { mutableStateOf<String?>(null) }
        fun depthOf(path: String) = path.count { it == '/' }
        val navDirection = remember(directory.path) {
            val prev = lastDirPath
            if (prev != null && depthOf(directory.path) < depthOf(prev)) -1 else 1
        }
        LaunchedEffect(directory.path) { lastDirPath = directory.path }

        // Shared glass recipe for the floating segments (breadcrumb, filter
        // strip): soft 12dp blur replay under a light tint, no border/shadow.
        val isDarkBg = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val glassTint = if (isDarkBg) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        val stripGap = 12.dp
        // Unchanged by the drift headroom below: chromeHeight measures the
        // bare strip content (onSizeChanged sits inside the headroom
        // padding), so rows-vs-strips spacing still comes only from here.
        val folderGap = 24.dp
        val chromeHeight = with(LocalDensity.current) { chromeHeightPx.toDp() }
        // Upward drift budget: a small rise only, so strips can bounce up
        // without ever reaching (and clipping under) the solid top bar.
        val riseBudgetPx = with(LocalDensity.current) { 4.dp.toPx() }.roundToInt()

        // Single list surface: one node renders the listing, so overlapping
        // layers are structurally impossible. The entrance is a subtle,
        // one-shot directional slide keyed on the new directory path.
        //
        // key(directory.path): rebinding ONE node across folder switches let a
        // stale subtree survive during fast enter/exit sequences and composite
        // its old rows on top of / behind the fresh listing ("ghost items of
        // another folder"). Remounting per directory disposes the previous
        // tree outright; scroll memory still works because positions live in
        // the external LRU map, not in this node.
        // Elastic scroll-follow: while the user drags the listing, the floating
        // strip drifts with the scroll and springs back once it stops.
        val stripElastic = rememberElasticScrollState()
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pathEnterSlide(directory.path, navDirection)
                .clipToBounds()
                .elasticScrollObserver(stripElastic)
        ) {
            // Cap the floating chrome on short viewports (landscape,
            // split-screen): the stack scrolls instead of squeezing the
            // sort controls to zero height. Tall screens are unaffected.
            val maxChromeHeight =
                (maxHeight - topChromeHeight - stripGap * 2).coerceAtLeast(0.dp)
            // Placement-weighted drift: the higher a strip sits, the less of
            // the shared elastic offset it takes (breadcrumb laziest, sort
            // strip follows fully).
            val driftFor: (Float) -> Int = { weight ->
                (stripElastic.offsetPx * weight).roundToInt().coerceAtLeast(-riseBudgetPx)
            }
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    onRefresh()
                },
                modifier = Modifier.fillMaxSize()
            ) {
                val scrollPositions = remember { boundedLruMap<String, Pair<Int, Int>>() }
                val scrollKey =
                    "${viewMode.name}:${directory.absolutePath}:${selectedCategory?.name ?: "ALL"}"
                // Remount on directory AND category/view changes: rebinding one node
                // across switches let a stale subtree survive fast interactions and
                // composite its old rows on top of the fresh listing ("ghost items").
                // Remounting per key disposes the previous tree outright; scroll
                // memory still works because positions live in the external LRU map.
                key(
                    directory.absolutePath,
                    selectedCategory?.name ?: "ALL",
                    viewMode.name
                ) {
                    FileListContent(
                        directory = directory,
                        filesDirectory = filesDirectory,
                        files = files,
                        isLoading = isLoading,
                        searchQuery = searchQuery,
                        // The same measured height follows both enter and exit,
                        // so rows never jump underneath a still-visible sort bar.
                        topContentPadding = topChromeHeight + stripGap + chromeHeight + folderGap,
                        // Thin loading cue sits just under the strips (not down
                        // at folder level) and lines up with their 12dp insets.
                        loadingBarTopPadding = topChromeHeight + stripGap + chromeHeight + 8.dp,
                        viewMode = viewMode,
                        isSelectionMode = isSelectionMode,
                        selectedPaths = selectedPaths,
                        scrollPositions = scrollPositions,
                        scrollKey = scrollKey,
                        hazeState = hazeState,
                        onFileClick = onFileClick,
                        onFileLongClick = onFileLongClick,
                        onFileMoreClick = onFileMoreClick
                    )
                }
            }

            // The spinner stops with the load that the pull started; any other
            // completion (navigation resetTransientFilters etc.) also clears it.
            LaunchedEffect(isLoading) {
                if (!isLoading) isRefreshing = false
            }

            // Frosted edges (Haze): alpha-mask-dissolved blur of the list;
            // draw-only, so touch passes straight through. The top band
            // covers the floating top bar (full frost behind it) and then
            // melts over a short 96dp below the bar - the same short melt
            // as the bottom band - so the fade is actually visible in open
            // content instead of ending exactly at the bar's bottom edge
            // (which read as a sharp-edged frosted bar).
            HazeFadeBand(
                hazeState = hazeState,
                height = topChromeHeight + 96.dp,
                top = true,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            )
            HazeFadeBand(
                hazeState = hazeState,
                height = 96.dp,
                top = false,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            )

            // One floating stack owns all gaps so adjacent strips cannot
            // collide. The list still scrolls behind the glass; each strip
            // drifts with its own placement weight (see driftFor).
            //
            // Drift headroom: strips swing up to 14dp down / 4dp up while the
            // list scrolls, but this scroll viewport wraps its content exactly
            // and would slice them at its bounds mid-swing (last strip bottom
            // going down, first strip top going up). The viewport is therefore
            // oversized by the max swing on both sides; the outer paddings
            // shrink by the same amount, so the strips and the folder list
            // keep their exact rest geometry (12dp gaps, 24dp to folders).
            val driftHeadroom = 16.dp
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .testTag("chrome_stack")
                    .padding(start = 12.dp, top = topChromeHeight + stripGap - driftHeadroom, end = 12.dp)
                    .heightIn(max = maxChromeHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(top = driftHeadroom, bottom = driftHeadroom)
                    .onSizeChanged { chromeHeightPx = it.height },
                verticalArrangement = Arrangement.spacedBy(stripGap)
            ) {
                if (breadcrumbVisible) {
                    val breadcrumbScale = remember { Animatable(0.94f) }
                    LaunchedEffect(directory.path) {
                        breadcrumbScale.snapTo(0.94f)
                        breadcrumbScale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 380f))
                    }
                    Surface(
                        shape = PillShape,
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("breadcrumb_strip")
                            .graphicsLayer {
                                scaleX = breadcrumbScale.value
                                scaleY = breadcrumbScale.value
                            }
                            // Topmost strip: laziest drift (must precede
                            // hazeEffect so the blur re-samples displaced).
                            .offset { IntOffset(0, driftFor(0.4f)) }
                            .clip(PillShape)
                            .hazeEffect(hazeState) {
                                blurRadius = 16.dp
                                tints = listOf(HazeTint(glassTint))
                                noiseFactor = 0f
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pathEnterSlide(directory.path, navDirection)
                                .clipToBounds()
                        ) {
                            BreadcrumbBar(
                                currentDirectory = directory,
                                rootDirectory = rootDirectory,
                                onNavigateToDir = onNavigateToDir,
                                onNavigateBack = onNavigateBack
                            )
                        }
                    }
                }

                if (heroVisible) {
                    StorageHeroCard(
                        stats = storageStats,
                        isCollapsed = storageOverviewCollapsed,
                        onToggleCollapse = onToggleStorageCollapse,
                        onAnalyzeStorageClick = onAnalyzeStorageClick,
                        // Same one-shot directional entrance as the listing.
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("storage_hero")
                            .offset { IntOffset(0, driftFor(0.3f)) }
                            .pathEnterSlide(directory.path, navDirection)
                            .clipToBounds()
                    )
                }

                // Keep filter and sort strips together; hide both while searching.
                if (searchQuery.isEmpty()) {
                    Column {
                        val stripScale = remember { Animatable(0.94f) }
                        LaunchedEffect(directory.path) {
                            stripScale.snapTo(0.94f)
                            stripScale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 380f))
                        }
                        Surface(
                            shape = PillShape,
                            // Transparent: the visible fill is the frosted
                            // hazeEffect replay, not a flat translucent color.
                            color = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("filter_strip")
                                .graphicsLayer {
                                    scaleX = stripScale.value
                                    scaleY = stripScale.value
                                }
                                .offset { IntOffset(0, driftFor(0.65f)) }
                                .clip(PillShape)
                                .hazeEffect(hazeState) {
                                    blurRadius = 16.dp
                                    tints = listOf(HazeTint(glassTint))
                                    noiseFactor = 0f
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                categoryFilters().forEach { (type, label) ->
                                    ChunkyChip(
                                        text = label,
                                        selected = selectedCategory == type,
                                        onClick = { onCategoryClick(type) },
                                        icon = type?.let { fileTypeIcon(it) }
                                    )
                                }
                            }
                        }

                        // Animate the glass and buttons together, with the gap inside the
                        // reveal so no empty slot or opaque capsule flashes on rapid taps.
                        AnimatedVisibility(
                            visible = sortBarVisible,
                            enter = expandVertically(
                                expandFrom = Alignment.Top,
                                animationSpec = spring(dampingRatio = 1f, stiffness = 380f)
                            ) + fadeIn(tween(160)) + scaleIn(
                                initialScale = 0.86f,
                                transformOrigin = TransformOrigin(0.5f, 0f),
                                animationSpec = spring(dampingRatio = 0.62f, stiffness = 380f)
                            ),
                            exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(180)) +
                                fadeOut(tween(120)) + scaleOut(
                                    targetScale = 0.94f,
                                    transformOrigin = TransformOrigin(0.5f, 0f),
                                    animationSpec = tween(180)
                                ),
                            label = "sort_strip_reveal"
                        ) {
                            Surface(
                                shape = PillShape,
                                // Transparent: the visible fill is the frosted
                                // hazeEffect replay, not a flat translucent color.
                                color = Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = stripGap)
                                    .testTag("sort_strip")
                                    // Lowest strip: follows the drift fully.
                                    .offset { IntOffset(0, driftFor(1f)) }
                                    .clip(PillShape)
                                    .hazeEffect(hazeState) {
                                        blurRadius = 16.dp
                                        tints = listOf(HazeTint(glassTint))
                                        noiseFactor = 0f
                                    }
                            ) {
                                CategorySortBar(
                                    mode = sortMode,
                                    order = sortOrder,
                                    onChange = onSortChange,
                                    // Same row insets as the file-type strip above so the
                                    // pills line up with the chips.
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One-shot directional fade-and-slide applied whenever [key] changes.
 * Entering deeper slides in from the right ([direction] = +1); going back up
 * mirrors it from the left ([direction] = -1). All reads happen in the draw
 * phase, so the animation never recomposes the wrapped content.
 * Finally-snaps to rest so a canceled run can never strand the container at
 * a stale offset/fade.
 */
@Composable
private fun Modifier.pathEnterSlide(key: Any?, direction: Int = 1): Modifier {
    val slideDistancePx = with(LocalDensity.current) { 48.dp.toPx() }
    val slide = remember(key, direction) { Animatable(direction * slideDistancePx) }
    LaunchedEffect(key, direction) {
        try {
            slide.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
        } finally {
            // On interruption land flat immediately - a frozen mid-flight
            // translation/fade must never linger under real content.
            withContext(NonCancellable) { slide.snapTo(0f) }
        }
    }
    return graphicsLayer {
        translationX = slide.value
        val progress = (abs(slide.value) / slideDistancePx).coerceIn(0f, 1f)
        alpha = 1f - progress
    }
}

/**
 * Elastic vertical settle applied when the listing is remounted by a folder
 * or view switch: the fresh content glides up into place with a springy
 * overshoot instead of popping in at the top. Filter taps are keyed out so
 * the list swaps with no motion. Runs alongside [pathEnterSlide] on
 * navigation, where the combined diagonal entrance reads as one motion.
 */
@Composable
private fun Modifier.elasticEnterSlide(key: Any?): Modifier {
    val slideDistancePx = with(LocalDensity.current) { 36.dp.toPx() }
    val slide = remember(key) { Animatable(slideDistancePx) }
    val fade = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        try {
            launch {
                slide.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 380f))
            }
            fade.animateTo(1f, tween(140))
        } finally {
            withContext(NonCancellable) {
                slide.snapTo(0f)
                fade.snapTo(1f)
            }
        }
    }
    return graphicsLayer {
        translationY = slide.value
        alpha = fade.value
    }
}

@Composable
private fun FileListContent(
    directory: File,
    filesDirectory: File?,
    files: List<FileItem>,
    isLoading: Boolean,
    searchQuery: String,
    topContentPadding: Dp,
    loadingBarTopPadding: Dp = topContentPadding,
    viewMode: ViewMode,
    isSelectionMode: Boolean,
    selectedPaths: Set<String>,
    scrollPositions: MutableMap<String, Pair<Int, Int>>,
    scrollKey: String,
    hazeState: HazeState,
    onFileClick: (FileItem) -> Unit,
    onFileLongClick: (FileItem) -> Unit,
    onFileMoreClick: (FileItem) -> Unit
) {
    // Stale content stays mounted while a reload runs; surface a thin cue
    // only once a load outlives fast reads, so quick navigations never flash
    // an indicator and slow ones still signal that fresh content is coming.
    var showLoadCue by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(150.milliseconds)
            showLoadCue = true
        } else {
            showLoadCue = false
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Record this listing every frame; the floating strips and edge
            // bands replay it blurred behind themselves (see HazeState).
            .hazeSource(hazeState)
            // Smooth, elastic settle whenever the listing is remounted by a
            // directory/view switch: instead of the fresh list suddenly
            // appearing at the top, it glides up into place with a springy
            // overshoot. Filter taps are keyed out on purpose - the list
            // swaps instantly with no jump while chips keep their bounce.
            .elasticEnterSlide(directory.path to viewMode.name)
    ) {
        when {
            // The mounted listing belongs to a folder the user already left
            // (fast enter-enter navigation): show the loader rather than
            // flashing rows from somewhere else.
            filesDirectory != null &&
                filesDirectory.absolutePath != directory.absolutePath -> LoadingView(topContentPadding)
            // Keep the existing list mounted during refreshes (navigation, deletes, renames)
            // so it is not torn down and rebuilt, which causes a visible flash and a full
            // relayout of every visible card.
            isLoading && files.isEmpty() -> LoadingView(topContentPadding)
            files.isEmpty() -> EmptyFolderView(searchQuery.isNotEmpty())
            else -> {
                when (viewMode) {
                    ViewMode.GRID -> FileGridView(files, isSelectionMode, selectedPaths, scrollPositions, scrollKey, topContentPadding, onFileClick, onFileLongClick)
                    ViewMode.EXPRESSIVE_CARDS -> FileExpressiveGridView(files, isSelectionMode, selectedPaths, scrollPositions, scrollKey, topContentPadding, onFileClick, onFileLongClick, onFileMoreClick)
                    ViewMode.LIST -> FileListView(files, isSelectionMode, selectedPaths, scrollPositions, scrollKey, topContentPadding, onFileClick, onFileLongClick, onFileMoreClick)
                }
            }
        }
        // Gated on the stale-folder branch below: while a new folder loads,
        // LoadingView already draws its own squiggly, and without this gate
        // the cue would double up underneath it.
        val isStaleFolder = filesDirectory != null &&
            filesDirectory.absolutePath != directory.absolutePath
        if (showLoadCue && files.isNotEmpty() && !isStaleFolder) {
            SquigglyProgressIndicator(
                // Squiggly stroke only: the straight track line behind it
                // reads as a doubled loading bar, mostly in light theme.
                showTrack = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = loadingBarTopPadding, start = 12.dp, end = 12.dp)
                    .align(Alignment.TopCenter)
            )
        }
    }
}

/**
 * Folder loading indicator: a thin indeterminate line pinned to the top of the
 * content area - directly below the type filter chips - instead of a blocking
 * centered spinner, so the page never loses its structure while fresh content
 * is read.
 */
@Composable
private fun LoadingView(topContentPadding: Dp) {
    Box(modifier = Modifier.fillMaxSize()) {
        SquigglyProgressIndicator(
            // Squiggly stroke only, matching the refresh cue above.
            showTrack = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topContentPadding, start = 24.dp, end = 24.dp)
                .align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun EmptyFolderView(isSearch: Boolean) {
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
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(if (isSearch) R.string.list_no_matches else R.string.list_folder_empty),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(if (isSearch) R.string.list_no_matches_hint
            else R.string.list_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// Access-ordered map that evicts the least-recently-used folder once the cap of
// 32 entries is reached, so scroll position memory stays bounded across sessions.
private fun <K, V> boundedLruMap(): MutableMap<K, V> =
    object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean =
            size > 32
    }

// Built per composition: labels are localized resources resolved at call time.
@Composable
private fun categoryFilters(): List<Pair<FileType?, String>> = listOf(
    null to stringResource(R.string.filter_all),
    FileType.IMAGE to stringResource(R.string.filter_images),
    FileType.VIDEO to stringResource(R.string.filter_videos),
    FileType.AUDIO to stringResource(R.string.filter_audio),
    FileType.DOCUMENT to stringResource(R.string.filter_docs),
    FileType.ARCHIVE to stringResource(R.string.filter_archives),
    FileType.CODE to stringResource(R.string.filter_code),
    FileType.APK to stringResource(R.string.filter_apk)
)

/**
 * Scroll restore/persist for a folder listing: keeps the last visible position
 * per folder+mode key (via [scrollKey]) so going back into a folder lands where
 * the user left off.
 *
 * Persistence stays dormant until that folder's items have been restored once:
 * a freshly mounted state reports (0, 0) while the directory is still loading,
 * and writing that through would erase the very position we are about to
 * restore (or a sibling folder's transient readings would erase its own).
 */
@Composable
private fun FolderScrollPersistence(
    scrollPositions: MutableMap<String, Pair<Int, Int>>,
    scrollKey: String,
    itemCount: Int,
    currentPosition: () -> Pair<Int, Int>,
    scrollToPosition: suspend (Int, Int) -> Unit
) {
    var restored by remember(scrollKey) { mutableStateOf(false) }

    LaunchedEffect(scrollKey, itemCount) {
        if (!restored && itemCount > 0) {
            restored = true
            // Consume the entry so it is never re-applied on a mere refresh;
            // ongoing scrolling below writes a fresh one right away.
            val saved = scrollPositions.remove(scrollKey)
            if (saved != null) {
                runCatching {
                    scrollToPosition(saved.first.coerceAtMost(itemCount - 1), saved.second)
                }
            }
        }
    }

    LaunchedEffect(scrollKey) {
        snapshotFlow(currentPosition)
            .dropWhile { !restored }
            .collect { scrollPositions[scrollKey] = it }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListView(
    files: List<FileItem>,
    isSelectionMode: Boolean,
    selectedPaths: Set<String>,
    scrollPositions: MutableMap<String, Pair<Int, Int>>,
    scrollKey: String,
    topContentPadding: Dp,
    onFileClick: (FileItem) -> Unit,
    onFileLongClick: (FileItem) -> Unit,
    onFileMoreClick: (FileItem) -> Unit
) {
    val listState = rememberLazyListState()

    FolderScrollPersistence(
        scrollPositions = scrollPositions,
        scrollKey = scrollKey,
        itemCount = files.size,
        currentPosition = { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset },
        scrollToPosition = { index, offset -> listState.scrollToItem(index, offset) }
    )

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topContentPadding,
            // Full-bleed: rows scroll under the nav bar, so the last item
            // must clear both the FAB zone and the navigation bar inset.
            bottom = 100.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = files,
            key = { it.path },
            contentType = { it.fileType }
        ) { item ->
            FileListCard(
                item = item,
                isSelected = selectedPaths.contains(item.path),
                isSelectionMode = isSelectionMode,
                onClick = { onFileClick(item) },
                onLongClick = { onFileLongClick(item) },
                onMoreClick = { onFileMoreClick(item) },
                modifier = Modifier.animateItem()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridView(
    files: List<FileItem>,
    isSelectionMode: Boolean,
    selectedPaths: Set<String>,
    scrollPositions: MutableMap<String, Pair<Int, Int>>,
    scrollKey: String,
    topContentPadding: Dp,
    onFileClick: (FileItem) -> Unit,
    onFileLongClick: (FileItem) -> Unit
) {
    FileLazyGrid(
        files = files,
        scrollPositions = scrollPositions,
        scrollKey = scrollKey,
        columns = 3,
        cellSpacing = 10.dp,
        topContentPadding = topContentPadding
    ) { item ->
        FileGridCard(
            item = item,
            isSelected = selectedPaths.contains(item.path),
            isSelectionMode = isSelectionMode,
            onClick = { onFileClick(item) },
            onLongClick = { onFileLongClick(item) },
            modifier = Modifier.animateItem()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileExpressiveGridView(
    files: List<FileItem>,
    isSelectionMode: Boolean,
    selectedPaths: Set<String>,
    scrollPositions: MutableMap<String, Pair<Int, Int>>,
    scrollKey: String,
    topContentPadding: Dp,
    onFileClick: (FileItem) -> Unit,
    onFileLongClick: (FileItem) -> Unit,
    onFileMoreClick: (FileItem) -> Unit
) {
    FileLazyGrid(
        files = files,
        scrollPositions = scrollPositions,
        scrollKey = scrollKey,
        columns = 2,
        cellSpacing = 12.dp,
        topContentPadding = topContentPadding
    ) { item ->
        FileExpressiveCard(
            item = item,
            isSelected = selectedPaths.contains(item.path),
            isSelectionMode = isSelectionMode,
            onClick = { onFileClick(item) },
            onLongClick = { onFileLongClick(item) },
            onMoreClick = { onFileMoreClick(item) },
            modifier = Modifier.animateItem()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileLazyGrid(
    files: List<FileItem>,
    scrollPositions: MutableMap<String, Pair<Int, Int>>,
    scrollKey: String,
    columns: Int,
    cellSpacing: Dp,
    topContentPadding: Dp = 0.dp,
    card: @Composable LazyGridItemScope.(FileItem) -> Unit
) {
    val gridState = rememberLazyGridState()

    FolderScrollPersistence(
        scrollPositions = scrollPositions,
        scrollKey = scrollKey,
        itemCount = files.size,
        currentPosition = { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset },
        scrollToPosition = { index, offset -> gridState.scrollToItem(index, offset) }
    )

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topContentPadding,
            // Full-bleed: same nav-bar clearance as the list (see above).
            bottom = 100.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        ),
        horizontalArrangement = Arrangement.spacedBy(cellSpacing),
        verticalArrangement = Arrangement.spacedBy(cellSpacing),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = files,
            key = { it.path },
            contentType = { it.fileType },
            itemContent = card
        )
    }
}
