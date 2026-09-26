package com.baiel.expressivefiles

import com.baiel.expressivefiles.viewmodel.stripTypedExtension
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The dialog chips the selected format's extension onto the typed name, so a
 * user who TYPES the extension must not end up with "bundle.zip.zip". The
 * strip only removes a trailing copy of the SELECTED format as its own
 * segment - anything else the user typed stays part of the stem.
 */
class StripTypedExtensionTest {

    @Test fun typedSelectedExtensionIsStrippedSoTheSuffixIsNotDoubled() {
        assertEquals("bundle", stripTypedExtension("bundle.zip", "zip"))
        assertEquals("bundle", stripTypedExtension("bundle.ZIP", "zip"))
        assertEquals("bundle", stripTypedExtension("bundle.tar.gz", "tar.gz"))
        assertEquals("my", stripTypedExtension("my.zip", "zip"))
        assertEquals("release.v2", stripTypedExtension("release.v2.zip", "zip"))
    }

    @Test fun otherExtensionsAndPlainNamesSurvive() {
        // A different typed extension is part of the stem the user wants.
        assertEquals("backup.tar", stripTypedExtension("backup.tar", "zip"))
        // No dot before the suffix: "myzip" is not "my" + ".zip".
        assertEquals("myzip", stripTypedExtension("myzip", "zip"))
        assertEquals("zip", stripTypedExtension("zip", "zip"))
        assertEquals("", stripTypedExtension("", "zip"))
        // Degenerate input: only the extension was typed - the stem is blank,
        // which createArchive rejects with the EMPTY error instead of naming
        // the archive ".zip.zip".
        assertEquals("", stripTypedExtension(".zip", "zip"))
    }

    @Test fun onlyOneTrailingCopyIsStripped() {
        // "bundle.zip.zip" typed + ZIP -> "bundle.zip", never a loop.
        assertEquals("bundle.zip", stripTypedExtension("bundle.zip.zip", "zip"))
    }
}
