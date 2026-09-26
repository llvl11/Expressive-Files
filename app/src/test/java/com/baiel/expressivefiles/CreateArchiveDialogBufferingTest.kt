package com.baiel.expressivefiles

import android.app.Application
import android.os.Environment
import android.os.Looper
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.baiel.expressivefiles.model.ArchiveType
import com.baiel.expressivefiles.model.FileItem
import com.baiel.expressivefiles.model.FileType
import com.baiel.expressivefiles.ui.components.CreateArchiveDialog
import com.baiel.expressivefiles.ui.theme.ExpressiveFilesTheme
import com.baiel.expressivefiles.viewmodel.FileViewModel
import com.baiel.expressivefiles.viewmodel.NameError
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The action buffer that replaced the one-shot latch: a double tap fires the
 * action exactly once, the window re-arms afterwards (the latch never
 * swallowed the NEXT Compress or Cancel), and the rejected-name flow - which
 * keeps this dialog open - cannot end up permanently deaf.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CreateArchiveDialogBufferingTest {

    @get:Rule val compose = createComposeRule()

    private fun labelOf(resourceId: Int): String =
        RuntimeEnvironment.getApplication().resources.getString(resourceId)

    @Test fun doubleTapFiresOnceAndTheBufferReArms() {
        val viewModel = FileViewModel(ApplicationProvider.getApplicationContext())
        val item = FileItem(
            file = File("/tmp/notes.txt"),
            name = "notes.txt",
            path = "/tmp/notes.txt",
            isDirectory = false,
            isHidden = false,
            extension = "txt",
            fileType = FileType.DOCUMENT
        )
        var compressions = 0
        var dismisses = 0

        compose.setContent {
            ExpressiveFilesTheme {
                CreateArchiveDialog(
                    viewModel = viewModel,
                    selectedFiles = listOf(item),
                    defaultFormat = ArchiveType.ZIP,
                    onDismiss = { dismisses++ },
                    onCompress = { _, _ -> compressions++ }
                )
            }
        }

        val compress = compose.onNodeWithText(labelOf(R.string.archive_compress))
        // The buffer is wall-clock based and Robolectric's clock starts at ~0,
        // so the very first tap would otherwise look like a "second" tap.
        ShadowSystemClock.advanceBy(2, TimeUnit.SECONDS)
        compress.performClick()
        compress.performClick()
        compose.runOnIdle { assertEquals("rapid taps must fire once", 1, compressions) }

        // Let the window expire, then prove the action re-armed.
        ShadowSystemClock.advanceBy(600, TimeUnit.MILLISECONDS)
        compress.performClick()
        compose.runOnIdle { assertEquals("buffer must re-arm", 2, compressions) }

        // The same window shields Cancel from the follower of a double tap -
        // the old latch swallowed it and stranded the open dialog.
        val cancel = compose.onNodeWithText(labelOf(R.string.dialog_cancel))
        cancel.performClick()
        compose.runOnIdle { assertEquals("cancel inside the window is ignored", 0, dismisses) }

        ShadowSystemClock.advanceBy(600, TimeUnit.MILLISECONDS)
        cancel.performClick()
        compose.runOnIdle { assertEquals("cancel re-arms too", 1, dismisses) }
    }

    private fun removeFixtures() {
        val root = Environment.getExternalStorageDirectory()
        File(root, "alpha.txt").delete()
        File(root, "alpha_archive.zip").delete()
    }

    @After fun cleanUpExternalStorage() = removeFixtures()

    /**
     * A name the ViewModel REJECTED (already exists) keeps this dialog open -
     * so the 500 ms double-tap window must re-arm the moment the error appears,
     * or the deliberate Cancel right after the rejection is swallowed and the
     * dialog can only be escaped through X/back.
     */
    @Test fun rejectedNameRearmsTheWindowSoCancelIsNotSwallowed() {
        val root = Environment.getExternalStorageDirectory()
        root.mkdirs()
        // The dialog pre-fills "alpha_archive" for a single "alpha.txt" pick;
        // make that exact name collide so the CONFIRM is rejected inline.
        File(root, "alpha.txt").writeText("alpha")
        File(root, "alpha_archive.zip").writeText("existing")

        val viewModel = FileViewModel(ApplicationProvider.getApplicationContext())
        viewModel.loadCurrentDirectory()
        // The load posts to the (paused) main looper; drain it while polling.
        val deadline = System.currentTimeMillis() + 15_000
        while (viewModel.files.value.none { it.name == "alpha.txt" }) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("root listing never loaded")
            }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(25)
        }
        val target = viewModel.files.value.first { it.name == "alpha.txt" }
        viewModel.toggleSelection(target)

        var dismisses = 0
        compose.setContent {
            ExpressiveFilesTheme {
                CreateArchiveDialog(
                    viewModel = viewModel,
                    selectedFiles = listOf(target),
                    defaultFormat = ArchiveType.ZIP,
                    onDismiss = { dismisses++ },
                    // Production wiring: the confirm validates in the ViewModel.
                    onCompress = { name, format -> viewModel.createArchive(name, format) }
                )
            }
        }

        ShadowSystemClock.advanceBy(2, TimeUnit.SECONDS)
        val compress = compose.onNodeWithText(labelOf(R.string.archive_compress))
        compress.performClick()

        // The rejection keeps the dialog open with an inline error...
        compose.runOnIdle { assertEquals(NameError.EXISTS, viewModel.nameError.value) }

        // ...and Cancel immediately after it must still land: the rejection
        // re-arms the buffer (without the fix, lastActionAt is still the
        // Compress tap and this click is eaten).
        compose.onNodeWithText(labelOf(R.string.dialog_cancel)).performClick()
        compose.runOnIdle { assertEquals("cancel after a rejected confirm must work", 1, dismisses) }
    }
}
