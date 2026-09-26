package com.baiel.expressivefiles

import com.baiel.expressivefiles.viewmodel.extractStemOf
import com.baiel.expressivefiles.viewmodel.uniqueExtractTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The auto-extract folder suffix rules: re-extracting an archive must never
 * write into a directory that already holds content - and the folder is named
 * after the archive with its FULL extension stripped, not just the last dot.
 */
class UniqueExtractTargetTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun unusedNameIsTakenAsIs() {
        val preferred = File(temporaryFolder.root, "photos")
        assertEquals(preferred, uniqueExtractTarget(preferred))
    }

    @Test fun existingEmptyFolderIsReused() {
        val preferred = File(temporaryFolder.root, "photos").apply { mkdirs() }
        assertEquals(preferred, uniqueExtractTarget(preferred))
    }

    @Test fun nonEmptyFolderGetsTheFirstSuffix() {
        val preferred = File(temporaryFolder.root, "photos").apply {
            mkdirs()
            File(this, "IMG_0001.jpg").writeText("x")
        }
        val target = uniqueExtractTarget(preferred)
        assertEquals(File(preferred.parentFile, "photos (1)"), target)
        assertEquals(false, target.exists())
    }

    @Test fun plainFileInTheWayIsSkippedAsWell() {
        val preferred = File(temporaryFolder.root, "photos").apply { writeText("not a folder") }
        assertEquals(File(preferred.parentFile, "photos (1)"), uniqueExtractTarget(preferred))
    }

    @Test fun suffixWalksPastTakenCandidates() {
        val root = temporaryFolder.root
        File(root, "photos").apply {
            mkdirs()
            File(this, "IMG_0001.jpg").writeText("x")
        }
        File(root, "photos (1)").apply {
            mkdirs()
            File(this, "IMG_0002.jpg").writeText("x")
        }
        val target = uniqueExtractTarget(File(root, "photos"))
        assertEquals(File(root, "photos (2)"), target)
        assertTrue(!target.exists())
    }

    @Test fun compoundExtensionsAreStrippedWhole() {
        // substringBeforeLast('.') would have produced "backup.tar" folders
        // for every multi-dot archive - the suffix the user must then clean up.
        assertEquals("backup", extractStemOf("backup.tar.gz"))
        assertEquals("backup", extractStemOf("backup.tar.bz2"))
        assertEquals("backup", extractStemOf("backup.tar.xz"))
        // The suffix match is case-insensitive but the stem keeps the input's
        // own casing - the folder is named after the archive as written.
        assertEquals("BACKUP", extractStemOf("BACKUP.TAR.GZ"))
    }

    @Test fun singleExtensionLosesOnlyTheLastSegment() {
        assertEquals("photos", extractStemOf("photos.zip"))
        assertEquals("photos", extractStemOf("photos.7z"))
        assertEquals("photos", extractStemOf("photos.tar"))
        assertEquals("photos", extractStemOf("photos.tgz"))
        assertEquals("release.v2", extractStemOf("release.v2.zip"))
        assertEquals("noext", extractStemOf("noext"))
    }

    @Test fun dotOnlyNamesKeepTheirLiteralName() {
        // An empty stem made File(parent, "") normalize to the PARENT: the
        // extract target escaped into a sibling of the container folder and
        // the progress label read "«»".
        assertEquals(".zip", extractStemOf(".zip"))
        assertEquals(".tar.gz", extractStemOf(".tar.gz"))
        assertEquals(".7z", extractStemOf(".7z"))
    }
}
