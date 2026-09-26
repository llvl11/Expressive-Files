package com.baiel.expressivefiles

import com.baiel.expressivefiles.archive.ArchiveEngine
import com.baiel.expressivefiles.model.ArchiveProgress
import com.baiel.expressivefiles.model.ArchiveType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Pins the archive engine's safety rules: never destroy existing data, never
 * emit duplicate entry names, never pretend a broken archive is an empty one,
 * and report real totals for ZIP extraction.
 */
class ArchiveEngineRegressionTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    private val quietProgress: (ArchiveProgress) -> Unit = {}

    private fun zipEntryNames(archive: File): List<String> =
        ZipFile(archive).use { zip -> zip.entries().asSequence().map { it.name }.toList() }

    @Test fun createArchiveRefusesAnExistingDestination() = runBlocking {
        val source = temporaryFolder.newFolder("source")
        File(source, "data.txt").writeText("payload")
        val destination = File(temporaryFolder.root, "existing.zip").apply { writeText("precious") }

        assertFalse(
            ArchiveEngine.createArchive(listOf(source), destination, ArchiveType.ZIP, quietProgress)
        )

        // The writers open the destination for writing, so a refusal that still
        // touched it would have truncated the user's original archive.
        assertEquals("precious", destination.readText())
    }

    @Test fun destinationIsNeverIncludedInItsOwnSourceSet() = runBlocking {
        val bundle = temporaryFolder.newFolder("bundle")
        File(bundle, "a.txt").writeText("a")
        // The archive is written INTO the folder being compressed.
        val destination = File(bundle, "bundle.zip")

        assertTrue(
            ArchiveEngine.createArchive(listOf(bundle), destination, ArchiveType.ZIP, quietProgress)
        )

        val names = zipEntryNames(destination)
        assertFalse(
            "self-referential archive contains its own output: $names",
            names.any { it.endsWith("bundle.zip") }
        )
        assertTrue(names.any { it.endsWith("a.txt") })
    }

    @Test fun sameFileNamesFromDifferentFoldersBothSurvive() = runBlocking {
        val left = temporaryFolder.newFolder("left")
        val right = temporaryFolder.newFolder("right")
        val first = File(left, "same.txt").apply { writeText("left") }
        val second = File(right, "same.txt").apply { writeText("right") }
        val destination = File(temporaryFolder.root, "merged.zip")

        val created = ArchiveEngine.createArchive(
            listOf(first, second), destination, ArchiveType.ZIP, quietProgress
        )
        assertTrue("duplicate entry names aborted the compression", created)

        val files = zipEntryNames(destination).filter { !it.endsWith("/") }
        assertEquals("both sources must land in the archive", 2, files.size)
        assertEquals("entry names must be unique", 2, files.toSet().size)
    }

    @Test fun listArchiveEntriesPropagatesCorruptionInsteadOfPretendingEmpty() {
        val corrupt = File(temporaryFolder.root, "corrupt.zip").apply {
            writeText("definitely not a zip archive")
        }
        // ArchiveViewerSheet maps a throw to its error state; returning an
        // empty list showed a broken archive as a valid one with 0 entries.
        assertThrows(Exception::class.java) {
            runBlocking { ArchiveEngine.listArchiveEntries(corrupt) }
        }
    }

    @Test fun zipExtractionReportsTheRealEntryTotal() = runBlocking {
        val payload = temporaryFolder.newFolder("payload")
        repeat(3) { index -> File(payload, "file$index.txt").writeText("content $index") }
        val archive = File(temporaryFolder.root, "totals.zip")
        ZipOutputStream(archive.outputStream()).use { out ->
            repeat(3) { index ->
                out.putNextEntry(ZipEntry("file$index.txt"))
                out.write("content $index".toByteArray())
                out.closeEntry()
            }
        }
        val target = temporaryFolder.newFolder("extracted")

        val totals = mutableListOf<Int>()
        assertTrue(
            ArchiveEngine.extractArchive(archive, target) { progress ->
                if (progress.operation == "Extracting ZIP" && progress.totalFiles > 0) {
                    totals += progress.totalFiles
                }
            }
        )

        assertEquals(
            "ZIP extraction must report the central-directory total",
            listOf(3, 3, 3),
            totals
        )
    }

    @Test fun tarEntriesReportUnknownCompressedSize() = runBlocking {
        val source = temporaryFolder.newFolder("tar-src")
        File(source, "data.txt").writeText("x".repeat(4096))
        val archive = File(temporaryFolder.root, "plain.tar")
        assertTrue(
            ArchiveEngine.createArchive(listOf(source), archive, ArchiveType.TAR, quietProgress)
        )

        val file = ArchiveEngine.listArchiveEntries(archive).single { !it.isDirectory }
        // TAR stores no per-entry compressed size: -1 = unknown, so the viewer
        // prints the plain size instead of "(x same-size compressed)".
        assertEquals("TAR entry must report unknown compressed size", -1L, file.compressedSize)
        assertTrue(file.size > 0)
    }

    @Test fun sevenZipEntriesReportUnknownCompressedSize() = runBlocking {
        val source = temporaryFolder.newFolder("7z-src")
        File(source, "data.txt").writeText("x".repeat(4096))
        val archive = File(temporaryFolder.root, "plain.7z")
        assertTrue(
            ArchiveEngine.createArchive(listOf(source), archive, ArchiveType.SEVEN_Z, quietProgress)
        )

        val file = ArchiveEngine.listArchiveEntries(archive).single { !it.isDirectory }
        // commons-compress 1.21 keeps SevenZArchiveEntry.compressedSize package-
        // private; copying size into it made every row read "16 KB (16 KB
        // compressed)".
        assertEquals("7z entry must report unknown compressed size", -1L, file.compressedSize)
        assertTrue(file.size > 0)
    }

    @Test fun zipEntriesReportARealCompressedSize() = runBlocking {
        val source = temporaryFolder.newFolder("zip-src")
        File(source, "data.txt").writeText("compressible ".repeat(512))
        val archive = File(temporaryFolder.root, "real.zip")
        assertTrue(
            ArchiveEngine.createArchive(listOf(source), archive, ArchiveType.ZIP, quietProgress)
        )

        val file = ArchiveEngine.listArchiveEntries(archive).single { !it.isDirectory }
        // ZIP's central directory carries real per-entry numbers - the viewer
        // shows "(x compressed)" only while 0 < compressed < size.
        assertTrue("real compressed size expected, got ${file.compressedSize}", file.compressedSize > 0)
        assertTrue("compressible content must shrink, got ${file.compressedSize}", file.compressedSize < file.size)
    }

    @Test fun extractionKeepsAnExistingFileAndStillAddsMissingOnes() = runBlocking {
        val archive = File(temporaryFolder.root, "keep.zip")
        ZipOutputStream(archive.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("data.txt"))
            out.write("fresh content".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("missing.txt"))
            out.write("brand new".toByteArray())
            out.closeEntry()
        }
        val target = temporaryFolder.newFolder("keep-target")
        File(target, "data.txt").writeText("precious user data")

        var last: ArchiveProgress? = null
        assertTrue(ArchiveEngine.extractArchive(archive, target) { last = it })

        // The pre-existing file wins byte for byte; the missing one still lands.
        assertEquals(
            "extraction truncated an existing file",
            "precious user data",
            File(target, "data.txt").readText()
        )
        assertEquals("brand new", File(target, "missing.txt").readText())
        assertEquals(
            "completion report must say how many existing files were kept",
            1, last?.skippedExisting
        )
    }

    @Test fun sevenZipReExtractionNeverTruncates() = runBlocking {
        val source = temporaryFolder.newFolder("7z-keep-src")
        File(source, "data.txt").writeText("archived payload")
        val archive = File(temporaryFolder.root, "keep.7z")
        assertTrue(
            ArchiveEngine.createArchive(listOf(source), archive, ArchiveType.SEVEN_Z, quietProgress)
        )

        assertEquals(1, reExtractOverAPreciousFile(archive))
    }

    @Test fun tarReExtractionNeverTruncates() = runBlocking {
        val source = temporaryFolder.newFolder("tar-keep-src")
        File(source, "data.txt").writeText("archived payload")
        val archive = File(temporaryFolder.root, "keep.tar")
        assertTrue(
            ArchiveEngine.createArchive(listOf(source), archive, ArchiveType.TAR, quietProgress)
        )

        assertEquals(1, reExtractOverAPreciousFile(archive))
    }

    /**
     * Probes the engine once to learn where it lands the archive's single
     * entry, then extracts a second time into a target holding "precious user
     * data" at that exact spot. Returns the completion report's
     * [ArchiveProgress.skippedExisting].
     */
    private suspend fun reExtractOverAPreciousFile(archive: File): Int {
        val probe = temporaryFolder.newFolder()
        assertTrue(ArchiveEngine.extractArchive(archive, probe) { })
        val relative = probe.walkTopDown().single { it.isFile }.relativeTo(probe)

        val target = temporaryFolder.newFolder()
        File(target, relative.path).apply { parentFile?.mkdirs() }
            .writeText("precious user data")

        var last: ArchiveProgress? = null
        assertTrue(ArchiveEngine.extractArchive(archive, target) { last = it })
        assertEquals(
            "re-extraction truncated an existing file",
            "precious user data",
            File(target, relative.path).readText()
        )
        return last?.skippedExisting ?: -1
    }
}
