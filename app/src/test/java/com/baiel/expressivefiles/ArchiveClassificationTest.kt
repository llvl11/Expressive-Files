package com.baiel.expressivefiles

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.baiel.expressivefiles.data.FileManagerRepository
import com.baiel.expressivefiles.model.ArchiveType
import com.baiel.expressivefiles.model.FileItem
import com.baiel.expressivefiles.model.FileType
import com.baiel.expressivefiles.model.archiveEntryFileType
import com.baiel.expressivefiles.model.determineFileType
import com.baiel.expressivefiles.model.label
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * How an archive is NAMED decides what the UI offers for it: which badge text
 * the card shows, which icon an entry inside the inspector gets, whether the
 * file reaches the archive viewer at all, and which MIME type the share sheet
 * proposes. All of that used to derive raw enum names or case-sensitive
 * extensions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ArchiveClassificationTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun badgeLabelsAreFormatNamesNotEnumNames() {
        assertEquals("ZIP", ArchiveType.ZIP.label)
        assertEquals("7Z", ArchiveType.SEVEN_Z.label)
        assertEquals("TAR", ArchiveType.TAR.label)
        assertEquals("TAR.GZ", ArchiveType.TAR_GZ.label)
        assertEquals("RAR", ArchiveType.RAR.label)
        // A sentinel, not a format: callers skip the badge when the label is
        // empty instead of printing an English "OTHER" on a Russian card.
        assertEquals("OTHER must render no badge at all", "", ArchiveType.OTHER.label)
    }

    @Test fun txzIsClassifiedAsATarTheEngineCanRead() {
        assertEquals(ArchiveType.TAR, ArchiveType.fromFileName("dump.txz"))
        assertEquals(ArchiveType.TAR, ArchiveType.fromFileName("dump.tbz2"))
        assertEquals(FileType.ARCHIVE, determineFileType("dump.txz", "txz", false))
        // Single-extension compression is NOT a tar: extraction of raw .gz
        // bytes would fail, so it stays OTHER behind the archive gate.
        assertEquals(ArchiveType.OTHER, ArchiveType.fromFileName("dump.gz"))
    }

    @Test fun archiveEntriesMatchExtensionsCaseInsensitively() {
        // Entries never pass through FileItem.fromAttrs, which lowercases:
        // uppercase names must still get their real icon in the inspector.
        assertEquals(FileType.IMAGE, archiveEntryFileType("PHOTO.JPG", false))
        assertEquals(FileType.VIDEO, archiveEntryFileType("CLIP.MP4", false))
        assertEquals(FileType.DOCUMENT, archiveEntryFileType("REPORT.PDF", false))
        assertEquals(FileType.FOLDER, archiveEntryFileType("photos", true))
        assertEquals(FileType.OTHER, archiveEntryFileType("photo", false))
    }

    @Test fun dotfileNamesSurviveTheArchiveTypeLookup() {
        assertEquals(FileType.OTHER, FileItem.fromAttrs(File("/x/.gitignore"), false, 1L, 0L).fileType)
        // ".zip" inside a dotfile name is a real archive: fromFileName must
        // still match it rather than treating the whole name as extension-less.
        assertEquals(ArchiveType.ZIP, ArchiveType.fromFileName(".zip"))
    }

    @Test fun txzSharesTheXzMimeType() {
        val repository = FileManagerRepository(ApplicationProvider.getApplicationContext())
        assertEquals("application/x-xz", repository.getMimeType(File("/x/dump.txz")))
        assertEquals("application/x-xz", repository.getMimeType(File("/x/dump.xz")))
    }
}
