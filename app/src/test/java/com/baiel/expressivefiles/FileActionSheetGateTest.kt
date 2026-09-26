package com.baiel.expressivefiles

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.baiel.expressivefiles.model.FileItem
import com.baiel.expressivefiles.ui.components.FileActionSheet
import com.baiel.expressivefiles.ui.theme.ExpressiveFilesTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The action sheet must offer Extract/Inspect only for formats the engine can
 * actually READ. .gz/.xz/.iso are typed ARCHIVE for the category filter but
 * resolve to ArchiveType.OTHER: the inspector fell through to the ZIP reader
 * and threw, and extraction fed compressed bytes to the TAR parser.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FileActionSheetGateTest {

    @get:Rule val compose = createComposeRule()

    private fun labelOf(resourceId: Int): String =
        RuntimeEnvironment.getApplication().resources.getString(resourceId)

    private fun item(name: String): FileItem =
        FileItem.fromAttrs(File("/storage/emulated/0/$name"), false, 10L, 0L)

    private fun show(target: FileItem): Unit {
        compose.setContent {
            ExpressiveFilesTheme {
                FileActionSheet(
                    targetItem = target,
                    onDismiss = {},
                    onOpen = {},
                    onOpenWithExternalApp = {},
                    onCopy = {},
                    onCut = {},
                    onRename = {},
                    onDelete = {},
                    onShare = {},
                    onCompress = {},
                    onExtract = {},
                    onInspectArchive = {}
                )
            }
        }
    }

    @Test fun readableArchiveOffersExtractAndInspect() {
        show(item("bundle.zip"))

        compose.onNodeWithText(labelOf(R.string.sheet_extract_all)).assertExists()
        compose.onNodeWithText(labelOf(R.string.sheet_inspect)).assertExists()
    }

    @Test fun tarGzArchiveOffersExtractAndInspect() {
        show(item("backup.tar.gz"))

        compose.onNodeWithText(labelOf(R.string.sheet_extract_all)).assertExists()
        compose.onNodeWithText(labelOf(R.string.sheet_inspect)).assertExists()
    }

    @Test fun unreadableArchiveHidesExtractAndInspectButKeepsTheRest() {
        show(item("dump.gz"))

        compose.onNodeWithText(labelOf(R.string.sheet_extract_all)).assertDoesNotExist()
        compose.onNodeWithText(labelOf(R.string.sheet_inspect)).assertDoesNotExist()
        // The common actions stay: the file is still a file.
        compose.onNodeWithText(labelOf(R.string.sheet_copy)).assertExists()
        compose.onNodeWithText(labelOf(R.string.sheet_rename)).assertExists()
    }

    @Test fun plainFileNeverOffersArchiveActions() {
        show(item("notes.txt"))

        compose.onNodeWithText(labelOf(R.string.sheet_extract_all)).assertDoesNotExist()
        compose.onNodeWithText(labelOf(R.string.sheet_inspect)).assertDoesNotExist()
        compose.onNodeWithText(labelOf(R.string.sheet_compress)).assertExists()
    }
}
