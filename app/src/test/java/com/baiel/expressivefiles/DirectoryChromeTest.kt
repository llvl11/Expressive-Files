package com.baiel.expressivefiles

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DirectoryChromeTest {
    @get:Rule val compose = createComposeRule()

    private val root = File("/storage/emulated/0")
    private val directory = mutableStateOf(File(root, "Documents"))
    private val showSort = mutableStateOf(false)
    private val viewMode = mutableStateOf(ViewMode.LIST)
    private val collapsed = mutableStateOf(true)
    private val query = mutableStateOf("")
    private val searchActive = mutableStateOf(false)

    private fun showDirectory(
        files: List<FileItem>? = null,
        unreadable: Boolean = false
    ): Unit {
        val stats = MutableStateFlow(StorageStats(totalBytes = 1024L, freeBytes = 512L, usedBytes = 512L))
        val actualFiles = files ?: listOf(
            FileItem.fromAttrs(File(directory.value, "Example folder"), true, 0L, 0L)
        )
        compose.setContent {
            ExpressiveFilesTheme {
                DirectoryPage(
                    directory = directory.value,
                    rootDirectory = root,
                    files = actualFiles,
                    isLoading = false,
                    showStorageOverview = true,
                    storageOverviewCollapsed = collapsed.value,
                    storageStatsFlow = stats,
                    hazeState = rememberHazeState(),
                    selectedCategory = null,
                    searchQuery = query.value,
                    isSearchActive = searchActive.value,
                    showSortBar = showSort.value,
                    sortMode = SortMode.NAME,
                    sortOrder = SortOrder.ASCENDING,
                    onSortChange = { _, _ -> },
                    viewMode = viewMode.value,
                    isSelectionMode = false,
                    selectedPaths = emptySet(),
                    filesDirectory = directory.value,
                    unreadableFolder = unreadable,
                    topChromeHeight = 80.dp,
                    onNavigateToDir = {},
                    onNavigateBack = {},
                    onToggleStorageCollapse = { collapsed.value = !collapsed.value },
                    onCategoryClick = {},
                    onAnalyzeStorageClick = {},
                    onFileClick = {},
                    onFileLongClick = {},
                    onFileMoreClick = {}
                )
            }
        }
    }

    private fun folderTop() = compose.onNode(hasText("Example folder") and hasClickAction())
        .getUnclippedBoundsInRoot().top.value

    private fun <T> update(state: MutableState<T>, value: T) {
        compose.runOnIdle { state.value = value }
        compose.waitForIdle()
    }

    private fun assertGaps(firstStrip: String, sortVisible: Boolean) {
        val first = compose.onNodeWithTag(firstStrip).getUnclippedBoundsInRoot()
        val filter = compose.onNodeWithTag("filter_strip").getUnclippedBoundsInRoot()
        assertEquals(12f, (filter.top - first.bottom).value, 1f)
        val last = if (sortVisible) {
            compose.onNodeWithTag("sort_strip").getUnclippedBoundsInRoot().also {
                assertEquals(12f, (it.top - filter.bottom).value, 1f)
            }
        } else filter
        assertEquals(24f, folderTop() - last.bottom.value, 1f)
    }

    @Test fun stripsAndFoldersKeepTheirGapsAcrossLayouts() {
        showDirectory()
        for (mode in ViewMode.entries) {
            update(viewMode, mode)
            assertGaps("breadcrumb_strip", sortVisible = false)
            update(showSort, true)
            assertGaps("breadcrumb_strip", sortVisible = true)
            update(showSort, false)
        }
        update(directory, root)
        assertGaps("storage_hero", sortVisible = false)
        update(showSort, true)
        assertGaps("storage_hero", sortVisible = true)
        update(collapsed, false)
        assertGaps("storage_hero", sortVisible = true)
    }

    @Test fun sortRevealMovesFoldersSmoothlyAndSurvivesReversal() {
        showDirectory()
        val closedTop = folderTop()
        compose.mainClock.autoAdvance = false
        update(showSort, true)
        compose.mainClock.advanceTimeBy(96)
        val openingTop = folderTop()
        assertTrue(openingTop > closedTop)
        compose.mainClock.advanceTimeBy(1000)
        val openTop = folderTop()
        assertTrue(openingTop < openTop)
        assertGaps("breadcrumb_strip", sortVisible = true)

        update(showSort, false)
        compose.mainClock.advanceTimeBy(64)
        val closingTop = folderTop()
        assertTrue(closingTop > closedTop)
        assertTrue(closingTop < openTop)
        update(showSort, true)
        compose.mainClock.advanceTimeBy(1000)
        assertGaps("breadcrumb_strip", sortVisible = true)

        update(showSort, false)
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("sort_strip").assertDoesNotExist()
        assertEquals(closedTop, folderTop(), 1f)
    }

    @Test fun searchHidesBothStripsAndRestoresSortWithoutAnEmptySlot() {
        showDirectory()
        update(showSort, true)
        update(query, "example")
        compose.onNodeWithTag("filter_strip").assertDoesNotExist()
        compose.onNodeWithTag("sort_strip").assertDoesNotExist()
        val breadcrumb = compose.onNodeWithTag("breadcrumb_strip").getUnclippedBoundsInRoot()
        assertEquals(24f, folderTop() - breadcrumb.bottom.value, 1f)
        update(query, "")
        assertGaps("breadcrumb_strip", sortVisible = true)
    }

    @Test fun searchDropsTheTopFrostBandAndRestoresItOnExit() {
        showDirectory()
        compose.onNodeWithTag("fade_band_top").assertExists()
        compose.onNodeWithTag("fade_band_bottom").assertExists()
        update(searchActive, true)
        compose.onNodeWithTag("fade_band_top").assertDoesNotExist()
        // The bottom edge frost is not search chrome - it stays.
        compose.onNodeWithTag("fade_band_bottom").assertExists()
        update(searchActive, false)
        compose.onNodeWithTag("fade_band_top").assertExists()
    }

    @Test fun unreadableFolderSaysNoAccessInsteadOfEmpty() {
        showDirectory(files = emptyList(), unreadable = true)
        val app = ApplicationProvider.getApplicationContext<Application>()
        // The folder could not be read at all - claiming it is empty hid the
        // access problem behind a normal-looking empty state.
        compose.onNode(hasText(app.getString(R.string.list_no_access))).assertExists()
        compose.onNode(hasText(app.getString(R.string.list_folder_empty))).assertDoesNotExist()
    }
}
