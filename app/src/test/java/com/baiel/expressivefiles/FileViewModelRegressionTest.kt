package com.baiel.expressivefiles

import android.app.Application
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import com.baiel.expressivefiles.model.ArchiveType
import com.baiel.expressivefiles.model.FileType
import com.baiel.expressivefiles.viewmodel.FileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Regression tests for ViewModel behaviour that is invisible in a screenshot:
 * back-stack integrity after a breadcrumb jump (on any volume, not just the
 * app root), what "Select all" selects while a category chip is active,
 * whether a freshly compressed archive becomes visible without restarting the
 * app, whether an unreadable folder falls back to the empty state instead of
 * a permanent spinner, and whether the selection shrinks with the listing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FileViewModelRegressionTest {

    private lateinit var root: File
    private lateinit var child: File
    private lateinit var grand: File
    private lateinit var viewModel: FileViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // The ViewModel boots straight into the external-storage root: create a
        // readable tree first so its very first load has something to show.
        root = Environment.getExternalStorageDirectory()
        root.mkdirs()
        File(root, "alpha.txt").writeText("alpha")
        File(root, "photo.jpg").writeText("jpg-bytes")
        child = File(root, "child").apply { mkdirs() }
        File(child, "beta.txt").writeText("beta")
        grand = File(child, "grand").apply { mkdirs() }
        viewModel = FileViewModel(ApplicationProvider.getApplicationContext())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Repository work lands on background dispatchers; poll instead of sleeping. */
    private fun awaitUntil(timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("condition not met within ${timeoutMs}ms")
            }
            Thread.sleep(25)
        }
    }

    @Test fun breadcrumbJumpRebuildsTheBackStackFromRoot() {
        viewModel.navigateToDirectory(child)
        awaitUntil { viewModel.currentDirectory.value == child }
        viewModel.navigateToDirectory(grand)
        awaitUntil { viewModel.currentDirectory.value == grand }
        assertTrue(viewModel.hasBackStack)

        // Jump back to `child` with the breadcrumb instead of pressing Back.
        viewModel.navigateToDirectory(child)
        assertEquals(child, viewModel.currentDirectory.value)

        // Only ancestors of `child` may remain: Back goes to the root, and the
        // folder we just left (`grand`) must be unreachable - the old code kept
        // it on the stack, so Back re-entered it and every press was off by one.
        assertTrue(viewModel.navigateBack())
        assertEquals(root, viewModel.currentDirectory.value)
        assertFalse(viewModel.hasBackStack)
        assertFalse(viewModel.navigateBack())
    }

    @Test fun selectAllSelectsExactlyTheListingOnScreenWhileAChipIsActive() {
        viewModel.loadCurrentDirectory()
        awaitUntil { viewModel.files.value.isNotEmpty() }
        val unfiltered = viewModel.files.value.map { it.path }.toSet()
        assertTrue(unfiltered.isNotEmpty())

        viewModel.setCategoryFilter(FileType.IMAGE)
        awaitUntil {
            val files = viewModel.files.value
            files.isNotEmpty() && files.all { it.fileType == FileType.IMAGE }
        }
        val chipListing = viewModel.files.value
        assertTrue(chipListing.isNotEmpty())
        // The chip narrows the same surface - it never swaps in another list.
        assertTrue(chipListing.all { it.path in unfiltered })

        viewModel.selectAll()
        val selected = viewModel.selectedPaths.value
        assertEquals(
            "Select all must match what the chip is showing",
            chipListing.mapTo(mutableSetOf()) { it.path },
            selected
        )
    }

    @Test fun compressedArchiveAppearsInTheListingWithoutARestart() {
        viewModel.loadCurrentDirectory()
        awaitUntil { viewModel.files.value.isNotEmpty() }
        val target = viewModel.files.value.first { !it.isDirectory }
        viewModel.toggleSelection(target)
        assertEquals(setOf(target.path), viewModel.selectedPaths.value)

        viewModel.createArchive("bundled", ArchiveType.ZIP)

        awaitUntil(30_000) { viewModel.files.value.any { it.name == "bundled.zip" } }
        assertEquals(root, viewModel.currentDirectory.value)
    }

    @Test fun typedArchiveExtensionIsNotDoubledOnDisk() {
        viewModel.loadCurrentDirectory()
        awaitUntil { viewModel.files.value.isNotEmpty() }
        val target = viewModel.files.value.first { !it.isDirectory }
        viewModel.toggleSelection(target)

        // The user types the format's own extension: destFile must still be
        // "note.zip", not "note.zip.zip".
        viewModel.createArchive("note.zip", ArchiveType.ZIP)

        awaitUntil(30_000) { viewModel.files.value.any { it.name == "note.zip" } }
        assertFalse(
            "the typed extension was appended a second time",
            viewModel.files.value.any { it.name == "note.zip.zip" }
        )
    }

    @Test fun initialCreateProgressShowsTheFormatLabelNotTheEnumName() {
        viewModel.loadCurrentDirectory()
        awaitUntil { viewModel.files.value.isNotEmpty() }
        val target = viewModel.files.value.first { !it.isDirectory }
        viewModel.toggleSelection(target)

        viewModel.createArchive("seven", ArchiveType.SEVEN_Z)

        // Either our initial emit or the engine's own "Creating 7Z" - both
        // carry the label; the old format.name produced "Создание SEVEN_Z".
        val expected = ApplicationProvider.getApplicationContext<Application>()
            .getString(R.string.op_creating, "7Z")
        val operation = viewModel.archiveProgress.value?.operation.orEmpty()
        assertTrue("unexpected operation: $operation", operation == expected || operation.contains("7Z"))
        assertFalse("raw enum name leaked: $operation", operation.contains("SEVEN_Z"))

        awaitUntil(30_000) { viewModel.files.value.any { it.name == "seven.7z" } }
    }

    @Test fun unreadableFolderFallsBackToTheEmptyStateNotASpinner() {
        viewModel.loadCurrentDirectory()
        awaitUntil { viewModel.files.value.isNotEmpty() }
        assertEquals(root, viewModel.filesDirectory.value)

        // The harness reports MANAGE granted: the probe would then spin on
        // virtual-time delays this test never advances. Fail fast instead.
        assumeFalse("harness grants all-files access", viewModel.hasStoragePermission.value)

        // Exists and is a folder, but the probe's canRead() fails - a mounted
        // volume without permission. The old code published the READY flag for
        // it, so DirectoryPage kept the spinner of the previous folder up
        // forever instead of showing the empty state.
        val locked = object : File(File(root, "locked"), "secret") {
            override fun exists(): Boolean = true
            override fun isDirectory(): Boolean = true
            override fun canRead(): Boolean = false
        }

        viewModel.navigateToDirectory(locked)
        awaitUntil {
            viewModel.filesDirectory.value?.absolutePath == locked.absolutePath &&
                !viewModel.isLoading.value
        }
        assertTrue(
            "the listing must belong to the locked folder, not the stale one",
            viewModel.files.value.isEmpty()
        )
        // The empty listing is an ACCESS failure, not a childless folder -
        // DirectoryPage needs the flag to say "no access" instead of
        // "Folder is empty".
        assertTrue(
            "fallback must flag the folder as unreadable",
            viewModel.unreadableFolder.value
        )
        // Back still walks the ancestor chain we built for it: one level up is
        // the (plain, on-disk) "locked" folder itself. It must really exist -
        // the probe reports a missing folder as unreadable too, which would
        // keep the flag set and mask a publish that never happens.
        File(root, "locked").mkdirs()
        assertTrue(viewModel.navigateBack())
        assertEquals(File(root, "locked"), viewModel.currentDirectory.value)
        awaitUntil { !viewModel.unreadableFolder.value }
        assertFalse(
            "a readable listing must clear the unreadable flag",
            viewModel.unreadableFolder.value
        )
    }

    @Test fun jumpingOutsideTheAppRootWalksUpNotBackIntoTheChild() {
        // Second volume: a sibling of the app root, not below it. No UI reaches
        // one today (no SD-card entry point), but the stack rule must hold there
        // anyway - the old else-branch pushed the CURRENT folder on any target
        // outside the root, so an upward jump put its own descendant underneath.
        val volume = File(root.parentFile, "VOL-AAAA").apply { mkdirs() }
        val volumeChild = File(volume, "DCIM").apply { mkdirs() }

        viewModel.navigateToDirectory(volume)
        assertEquals(volume, viewModel.currentDirectory.value)
        viewModel.navigateToDirectory(volumeChild)
        assertEquals(volumeChild, viewModel.currentDirectory.value)
        assertTrue(viewModel.hasBackStack)

        // Upward crumb jump: Back must continue UP the volume...
        viewModel.navigateToDirectory(volume)
        assertEquals(volume, viewModel.currentDirectory.value)
        assertTrue(viewModel.hasBackStack)
        assertTrue(viewModel.navigateBack())
        assertEquals(volume.parentFile, viewModel.currentDirectory.value)

        // ...and draining the stack never re-enters the child we left.
        while (viewModel.hasBackStack) {
            assertTrue(viewModel.navigateBack())
        }
        assertFalse(viewModel.currentDirectory.value.path.contains("DCIM"))
    }

    @Test fun hidingHiddenFilesPrunesThemFromTheSelection() {
        val hidden = File(root, ".hidden-note.txt").apply { writeText("shh") }
        viewModel.toggleShowHiddenFiles()
        awaitUntil { viewModel.files.value.any { it.path == hidden.absolutePath } }
        val hiddenItem = viewModel.files.value.first { it.path == hidden.absolutePath }
        val visible = viewModel.files.value.first { it.name == "alpha.txt" }
        viewModel.toggleSelection(hiddenItem)
        viewModel.toggleSelection(visible)
        assertEquals(2, viewModel.selectedPaths.value.size)

        viewModel.toggleShowHiddenFiles()
        // isLoading flips false only AFTER pruneSelection ran in the same
        // coroutine - waiting on both closes the poll window between them.
        awaitUntil {
            !viewModel.isLoading.value &&
                viewModel.files.value.none { it.path == hidden.absolutePath } &&
                viewModel.selectedPaths.value == setOf(visible.path)
        }
        // The listing shrank; the selection must shrink with it - otherwise
        // "2 selected" would silently act on 1 file.
        assertEquals(setOf(visible.path), viewModel.selectedPaths.value)
    }

    @Test fun refreshPrunesSelectionOfFilesGoneFromDisk() {
        viewModel.loadCurrentDirectory()
        awaitUntil { viewModel.files.value.isNotEmpty() }
        val doomed = viewModel.files.value.first { !it.isDirectory }
        viewModel.toggleSelection(doomed)
        assertEquals(setOf(doomed.path), viewModel.selectedPaths.value)

        File(doomed.path).delete()
        viewModel.refreshCurrentDirectory()
        awaitUntil {
            !viewModel.isLoading.value &&
                viewModel.files.value.none { it.path == doomed.path } &&
                viewModel.selectedPaths.value.isEmpty()
        }
        assertEquals(
            "selection must follow the listing, not the vanished path",
            emptySet<String>(),
            viewModel.selectedPaths.value
        )
    }

    @Test fun aCategorySelectionSurvivesAHomeRefresh() {
        val nested = File(child, "nested.jpg").apply { writeText("jpg-bytes") }
        viewModel.loadCurrentDirectory()
        awaitUntil { viewModel.files.value.isNotEmpty() }
        viewModel.loadCategoryFiles(FileType.IMAGE)
        awaitUntil { viewModel.categoryFiles.value.any { it.path == nested.absolutePath } }
        val image = viewModel.categoryFiles.value.first { it.path == nested.absolutePath }
        viewModel.toggleSelection(image)
        assertEquals(setOf(image.path), viewModel.selectedPaths.value)

        viewModel.refreshCurrentDirectory()
        // The drill item is NOT part of the home listing: pruneSelection must
        // union BOTH surfaces, or a refresh would silently drop the drill pick.
        // isLoading false again => the prune inside that load already ran.
        awaitUntil {
            !viewModel.isLoading.value && viewModel.files.value.isNotEmpty()
        }
        assertFalse(viewModel.files.value.any { it.path == nested.absolutePath })
        assertEquals(setOf(image.path), viewModel.selectedPaths.value)
    }

    @Test fun resumeRefreshSurfacesFilesWrittenWhileBackgrounded() {
        viewModel.loadCurrentDirectory()
        awaitUntil { viewModel.files.value.isNotEmpty() }

        // Written by another app while we were backgrounded: the session
        // cache still holds the old listing, so a plain reload must not show
        // it yet...
        val external = File(root, "written-elsewhere.txt").apply { writeText("hi") }
        viewModel.loadCurrentDirectory()
        awaitUntil { !viewModel.isLoading.value }
        assertFalse(
            "plain reload must still be served from the stale session cache",
            viewModel.files.value.any { it.path == external.absolutePath }
        )

        // ...but the onResume path (refreshPermission) must drop the cache and
        // surface it - otherwise an app switch hides every external change.
        viewModel.refreshPermission()
        awaitUntil {
            !viewModel.isLoading.value &&
                viewModel.files.value.any { it.path == external.absolutePath }
        }
    }
}
