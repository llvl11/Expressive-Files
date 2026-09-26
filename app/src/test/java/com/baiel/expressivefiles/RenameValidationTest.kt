package com.baiel.expressivefiles

import com.baiel.expressivefiles.model.RenameRejection
import com.baiel.expressivefiles.model.fileExtensionOf
import com.baiel.expressivefiles.model.validateNewName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the rename dialog's live validation rules: blank names, illegal
 * characters, reserved "."/"..", unchanged names, same-directory conflicts
 * (case-insensitive) and the non-blocking extension-change warning.
 */
class RenameValidationTest {

    private val siblings = setOf("readme.md", "photo.jpg", "downloads")

    @Test fun blankNamesAreRejected() {
        assertEquals(RenameRejection.EMPTY, validateNewName("   ", "notes.txt", false, siblings).rejection)
        assertEquals(RenameRejection.EMPTY, validateNewName("", "notes.txt", false, siblings).rejection)
    }

    @Test fun illegalCharactersAreRejected() {
        assertEquals(
            RenameRejection.INVALID_CHARACTERS,
            validateNewName("a/b", "notes.txt", false, siblings).rejection
        )
        assertEquals(
            RenameRejection.INVALID_CHARACTERS,
            validateNewName("a\\b", "notes.txt", false, siblings).rejection
        )
        assertEquals(
            RenameRejection.INVALID_CHARACTERS,
            validateNewName("a\u0000b", "notes.txt", false, siblings).rejection
        )
    }

    @Test fun reservedNamesAreRejected() {
        assertEquals(RenameRejection.RESERVED_NAME, validateNewName(".", "notes.txt", false, siblings).rejection)
        assertEquals(RenameRejection.RESERVED_NAME, validateNewName("..", "notes.txt", false, siblings).rejection)
    }

    @Test fun unchangedNameIsRejectedButCaseOnlyRenamePasses() {
        assertEquals(RenameRejection.UNCHANGED, validateNewName("notes.txt", "notes.txt", false, siblings).rejection)
        // Trailing whitespace is trimmed first, so this is also unchanged.
        assertEquals(
            RenameRejection.UNCHANGED,
            validateNewName("  notes.txt  ", "notes.txt", false, siblings).rejection
        )
        // Case-only change is a legitimate rename (case-insensitive storage).
        assertNull(validateNewName("Notes.txt", "notes.txt", false, siblings).rejection)
    }

    @Test fun siblingConflictsAreRejectedCaseInsensitively() {
        assertEquals(
            RenameRejection.NAME_TAKEN,
            validateNewName("readme.md", "notes.txt", false, siblings).rejection
        )
        assertEquals(
            RenameRejection.NAME_TAKEN,
            validateNewName("PHOTO.JPG", "notes.txt", false, siblings).rejection
        )
    }

    @Test fun freshValidNamePassesWithTrimmedName() {
        val result = validateNewName("  new name.txt  ", "notes.txt", false, siblings)
        assertNull(result.rejection)
        assertEquals("new name.txt", result.trimmedName)
        assertFalse(result.extensionChanged)
    }

    @Test fun extensionChangeIsAWarningNotARejection() {
        val changed = validateNewName("photo.png", "photo.jpg", false, siblings)
        assertNull(changed.rejection)
        assertTrue(changed.extensionChanged)

        // Gaining an extension entirely also warns.
        assertTrue(validateNewName("notes.txt", "notes", false, siblings).extensionChanged)

        // Same extension in different case does not warn (case-only rename).
        assertFalse(validateNewName("photo.JPG", "photo.jpg", false, siblings).extensionChanged)

        // Folders never trigger the extension warning.
        val folder = validateNewName("docs.txt", "docs", true, emptySet())
        assertNull(folder.rejection)
        assertFalse(folder.extensionChanged)
    }

    @Test fun fileExtensionOfMatchesFileExtensionSemantics() {
        assertEquals("jpg", fileExtensionOf("photo.JPG"))
        assertEquals("gz", fileExtensionOf("archive.tar.gz"))
        // A leading dot counts - this is kotlin.io.File.extension's rule, which
        // getMimeType/determineFileType use. The old `dot > 0` variant gave
        // dotfiles "no extension", so the rename warning disagreed with the type
        // the listing showed (".zip" is an archive everywhere but here).
        assertEquals("gitignore", fileExtensionOf(".gitignore"))
        assertEquals("zip", fileExtensionOf(".zip"))
        assertEquals("", fileExtensionOf("noext"))
        assertEquals("", fileExtensionOf(""))
    }
}
