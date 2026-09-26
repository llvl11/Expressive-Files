package com.baiel.expressivefiles

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.baiel.expressivefiles.data.FileManagerRepository
import com.baiel.expressivefiles.data.pruneNestedSources
import com.baiel.expressivefiles.model.SortMode
import com.baiel.expressivefiles.model.SortOrder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FileOperationsRegressionTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private lateinit var repository: FileManagerRepository

    @Before fun setUp(): Unit {
        repository = FileManagerRepository(ApplicationProvider.getApplicationContext())
    }

    @Test fun rejectsTraversalInCreateAndRename() = runBlocking {
        val parent = temporaryFolder.newFolder("parent")
        val original = File(parent, "original.txt").apply { writeText("keep me") }
        for (name in listOf("../escaped", "nested/file", "nested\\file", ".", "..", " ")) {
            assertTrue(repository.createDirectory(parent, name).isFailure)
            assertTrue(repository.createNewFile(parent, name).isFailure)
            assertTrue(repository.rename(original, name).isFailure)
        }
        assertEquals("keep me", original.readText())
        assertFalse(File(temporaryFolder.root, "escaped").exists())
        assertFalse(File(parent, "nested").exists())
    }

    @Test fun rejectsCopyAndMoveIntoOwnTreeBeforeCreatingDestination() = runBlocking {
        val source = temporaryFolder.newFolder("source")
        File(source, "data.txt").writeText("original")
        val child = File(source, "child")
        assertFalse(repository.copyFiles(listOf(source), source))
        assertFalse(repository.copyFiles(listOf(source), child))
        assertFalse(repository.moveFiles(listOf(source), child))
        assertFalse(child.exists())
        assertEquals("original", File(source, "data.txt").readText())
    }

    @Test fun sameDirectoryMoveDoesNotRenameOrDuplicate() = runBlocking {
        val parent = temporaryFolder.newFolder("parent")
        val source = File(parent, "note.txt").apply { writeText("note") }
        assertTrue(repository.moveFiles(listOf(source), parent))
        assertEquals(listOf("note.txt"), parent.list()!!.toList())
        assertEquals("note", source.readText())
    }

    @Test fun copyPreservesExistingDestinationAndSource() = runBlocking {
        val source = File(temporaryFolder.newFolder("source"), "note.txt").apply { writeText("new") }
        val destination = temporaryFolder.newFolder("destination")
        File(destination, "note.txt").writeText("existing")
        assertTrue(repository.copyFiles(listOf(source), destination))
        assertEquals("existing", File(destination, "note.txt").readText())
        assertEquals("new", source.readText())
        assertEquals(setOf("existing", "new"), destination.listFiles()!!.map { it.readText() }.toSet())
    }

    @Test fun invalidBatchDoesNotMoveEarlierSources() = runBlocking {
        val source = File(temporaryFolder.newFolder("source"), "note.txt").apply { writeText("keep") }
        val destination = File(temporaryFolder.root, "destination")
        assertFalse(repository.moveFiles(listOf(source, File(temporaryFolder.root, "missing")), destination))
        assertTrue(source.exists())
        assertFalse(destination.exists())
    }

    private suspend fun namesIn(dir: File): List<String> =
        repository.listFiles(dir, showHidden = false, sortMode = SortMode.NAME, sortOrder = SortOrder.ASCENDING)
            .map { it.name }

    @Test fun listingCacheIsInvalidatedAfterAMutation() = runBlocking {
        val dir = temporaryFolder.newFolder("cached")
        File(dir, "before.txt").writeText("b")
        assertEquals(listOf("before.txt"), namesIn(dir))

        // The cache is what made every create/rename/delete invisible until the
        // app restarted; the repository invalidates it on each mutation.
        assertTrue(repository.createDirectory(dir, "new folder").isSuccess)
        assertEquals(setOf("before.txt", "new folder"), namesIn(dir).toSet())
    }

    @Test fun externallyWrittenFilesAppearOnceTheCacheIsInvalidated() = runBlocking {
        val dir = temporaryFolder.newFolder("external")
        File(dir, "before.txt").writeText("b")
        assertEquals(listOf("before.txt"), namesIn(dir))

        // Compress writes its output through ArchiveEngine, bypassing the
        // repository - the ViewModel has to invalidate before reloading.
        File(dir, "bundle.zip").writeText("pk")
        repository.invalidateDirectoryCache()
        assertTrue(namesIn(dir).contains("bundle.zip"))
    }

    @Test fun caseOnlyRenameIsNotRejectedAsANameConflict() = runBlocking {
        val dir = temporaryFolder.newFolder("case")
        val file = File(dir, "notes.txt").apply { writeText("body") }

        val result = repository.rename(file, "NOTES.txt")

        assertTrue("case-only rename was rejected: ${result.exceptionOrNull()}", result.isSuccess)
        // Directory listing, not File.exists(): on a case-insensitive volume both
        // spellings resolve to the same entry and would hide a duplicate.
        assertEquals(listOf("NOTES.txt"), dir.list()!!.toList())
        assertEquals("body", File(dir, "NOTES.txt").readText())
    }

    @Test fun failedDirectoryCopyLeavesNoPartialDestination() = runBlocking {
        val source = temporaryFolder.newFolder("partial")
        File(source, "a-ok.txt").writeText("ok")
        // Two independent ways to make the copy die halfway: an entry that
        // cannot be read (POSIX) and one whose write is refused by the OS.
        val locked = File(source, "z-locked.txt").apply {
            writeText("secret")
            setReadable(false, false)
        }
        File(source, "reserved.txt").writeText("blocked on some platforms")

        val destination = temporaryFolder.newFolder("partial-destination")
        val copied = repository.copyFiles(listOf(source), destination)
        locked.setReadable(true, false)

        // Platforms where every write we can construct succeeds (no permission
        // bits, relaxed reserved names) cannot exercise this path at all.
        assumeTrue("platform could not induce a mid-copy failure", !copied)
        assertFalse(
            "a half-copied tree must not be left behind",
            File(destination, source.name).exists()
        )
    }

    @Test fun deletingAFolderTogetherWithItsChildStillCountsAsSuccess() = runBlocking {
        val parent = temporaryFolder.newFolder("batch")
        val folder = File(parent, "outer").apply { mkdirs() }
        val child = File(folder, "inner.txt").apply { writeText("in") }

        // The child's own delete() returns false once the recursive parent
        // delete has already removed it - that must not report the batch as
        // failed and toast "delete failed" after everything was deleted.
        assertTrue(repository.deleteFiles(listOf(folder, child)))
        assertFalse(folder.exists())
        assertFalse(child.exists())
    }

    @Test fun copyCollisionOnADotfileKeepsTheDotAndHiddenStatus() = runBlocking {
        val source = temporaryFolder.newFolder("dot-src")
        File(source, ".gitignore").writeText("ignored")
        val destination = temporaryFolder.newFolder("dot-dest")
        File(destination, ".gitignore").writeText("already there")

        assertTrue(repository.copyFiles(listOf(File(source, ".gitignore")), destination))

        // nameWithoutExtension is "" for dotfiles, so the old counter produced
        // "_1.gitignore" - a VISIBLE file with the dot (and the hidden status)
        // silently gone.
        assertEquals(
            setOf(".gitignore", ".gitignore_1"),
            destination.list()!!.toSet()
        )
        assertEquals("already there", File(destination, ".gitignore").readText())
        assertEquals("ignored", File(destination, ".gitignore_1").readText())
    }

    @Test fun copyCollisionOnAMultiDotNameCounterGoesBeforeTheLastExtension() = runBlocking {
        val source = temporaryFolder.newFolder("compound-src")
        File(source, "archive.tar.gz").writeText("gz-bytes")
        val destination = temporaryFolder.newFolder("compound-dest")
        File(destination, "archive.tar.gz").writeText("existing")

        assertTrue(repository.copyFiles(listOf(File(source, "archive.tar.gz")), destination))

        assertEquals(
            setOf("archive.tar.gz", "archive.tar_1.gz"),
            destination.list()!!.toSet()
        )
        assertEquals("existing", File(destination, "archive.tar.gz").readText())
        assertEquals("gz-bytes", File(destination, "archive.tar_1.gz").readText())
    }

    @Test fun pruneNestedSourcesDropsOnlyTheNestedChild() {
        val parent = File(temporaryFolder.root, "parent")
        val child = File(parent, "child.txt")
        val other = File(temporaryFolder.root, "other.txt")

        assertEquals(listOf(parent), pruneNestedSources(listOf(parent, child)))
        assertEquals(listOf(parent), pruneNestedSources(listOf(child, parent)))
        assertEquals(listOf(other, parent), pruneNestedSources(listOf(other, parent, child)))
        // Siblings sharing a prefix are NOT nested ("foo" vs "foobar").
        val foo = File(temporaryFolder.root, "foo")
        assertEquals(listOf(foo, other), pruneNestedSources(listOf(foo, other)))
    }

    @Test fun copyingParentAndChildTogetherDuplicatesOnlyTheFolder() = runBlocking {
        val parent = temporaryFolder.newFolder("nest-src")
        val child = File(parent, "child.txt").apply { writeText("payload") }
        val destination = temporaryFolder.newFolder("nest-copy-dest")

        assertTrue(repository.copyFiles(listOf(parent, child), destination))

        assertEquals(setOf("nest-src"), destination.list()!!.toSet())
        assertEquals("payload", File(destination, "nest-src/child.txt").readText())
        assertEquals("payload", child.readText())
    }

    @Test fun movingParentAndChildTogetherNeverStrandsTheChild() = runBlocking {
        val parent = temporaryFolder.newFolder("nest-move-src")
        val child = File(parent, "child.txt").apply { writeText("payload") }
        val destination = temporaryFolder.newFolder("nest-move-dest")

        // Child FIRST: without pruning it was renamed to the destination root
        // ahead of the folder, so the folder moved without it.
        assertTrue(repository.moveFiles(listOf(child, parent), destination))

        assertEquals(setOf("nest-move-src"), destination.list()!!.toSet())
        assertEquals("payload", File(destination, "nest-move-src/child.txt").readText())
        assertFalse(parent.exists())
        assertFalse(File(destination, "child.txt").exists())
    }
}