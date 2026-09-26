package com.baiel.expressivefiles

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.dp
import com.baiel.expressivefiles.model.FileItem
import com.baiel.expressivefiles.model.SortMode
import com.baiel.expressivefiles.model.SortOrder
import com.baiel.expressivefiles.model.StorageStats
import com.baiel.expressivefiles.model.ViewMode
import com.baiel.expressivefiles.ui.screens.home.DirectoryPage
import com.baiel.expressivefiles.ui.theme.ExpressiveFilesTheme
import dev.chrisbanes.haze.rememberHazeState
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The floating chrome (hero / breadcrumb / filter / sort strips) drifts with
 * the listing scroll (elastic follow: up to 14dp down, 4dp up — see
 * rememberElasticScrollState). The stack is a scroll viewport sized to its
 * content, so without clearance inside it the viewport slices the strips
 * mid-scroll: hero top going up, last strip bottom going down. These tests
 * pin that clearance geometrically (no gesture timing involved).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DirectoryDriftClipTest {
    @get:Rule val compose = createComposeRule()

    private val root = File("/storage/emulated/0")

    private fun showDirectory(
        directory: File,
        showSortBar: Boolean,
        collapsedHero: Boolean
    ) {
        val stats = MutableStateFlow(StorageStats(totalBytes = 1024L, freeBytes = 512L, usedBytes = 512L))
        val files = List(3) { index ->
            FileItem.fromAttrs(File(directory, "File %02d".format(index)), false, 1024L, 0L)
        }
        compose.setContent {
            ExpressiveFilesTheme {
                DirectoryPage(
                    directory = directory,
                    rootDirectory = root,
                    files = files,
                    isLoading = false,
                    showStorageOverview = true,
                    storageOverviewCollapsed = collapsedHero,
                    storageStatsFlow = stats,
                    hazeState = rememberHazeState(),
                    selectedCategory = null,
                    searchQuery = "",
                    showSortBar = showSortBar,
                    sortMode = SortMode.NAME,
                    sortOrder = SortOrder.ASCENDING,
                    onSortChange = { _, _ -> },
                    viewMode = ViewMode.LIST,
                    isSelectionMode = false,
                    selectedPaths = emptySet(),
                    filesDirectory = directory,
                    topChromeHeight = 80.dp,
                    onNavigateToDir = {},
                    onNavigateBack = {},
                    onToggleStorageCollapse = {},
                    onCategoryClick = {},
                    onAnalyzeStorageClick = {},
                    onFileClick = {},
                    onFileLongClick = {},
                    onFileMoreClick = {}
                )
            }
        }
        compose.waitForIdle()
    }

    private fun assertDriftHeadroom(
        firstTag: String,
        lastTag: String,
        topChromeHeight: androidx.compose.ui.unit.Dp
    ) {
        val stack = compose.onNodeWithTag("chrome_stack").getBoundsInRoot()
        val first = compose.onNodeWithTag(firstTag).getBoundsInRoot()
        val last = compose.onNodeWithTag(lastTag).getBoundsInRoot()
        // Rest geometry: the first strip starts exactly below the top chrome
        // + 12dp gap, headroom or not (node top includes the outer padding).
        org.junit.Assert.assertEquals(
            "first strip moved from its rest position",
            (topChromeHeight + 12.dp).value, (first.top - stack.top).value, 1f
        )
        // Drift headroom: viewport clearance below the last strip must cover
        // the 14dp downward elastic swing (node bottom == viewport bottom:
        // there is no outer bottom padding).
        val bottomClearance = (stack.bottom - last.bottom).value
        assertTrue(
            "stack slices strip bottoms mid-scroll: bottom clearance ${bottomClearance}dp < 14dp drift",
            bottomClearance >= 13f
        )
    }

    @Test fun rootStackClearsDriftingStrips() {
        showDirectory(root, showSortBar = true, collapsedHero = false)
        assertDriftHeadroom("storage_hero", "sort_strip", 80.dp)
    }

    @Test fun subfolderStackClearsDriftingStrips() {
        showDirectory(File(root, "Documents"), showSortBar = true, collapsedHero = true)
        assertDriftHeadroom("breadcrumb_strip", "sort_strip", 80.dp)
    }
}
