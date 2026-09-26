package com.baiel.expressivefiles.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baiel.expressivefiles.R
import com.baiel.expressivefiles.model.FileItem
import com.baiel.expressivefiles.model.RenameRejection
import com.baiel.expressivefiles.model.validateNewName
import com.baiel.expressivefiles.ui.theme.FolderColor
import com.baiel.expressivefiles.viewmodel.FileViewModel
import com.baiel.expressivefiles.viewmodel.NameError

// Amber tuned per theme so the extension warning stays readable on both
// (same luminance-based split DialogShell uses for shadows).
private val WarningAmberLight = Color(0xFF9A6A00)
private val WarningAmberDark = Color(0xFFFFC24B)

/**
 * Dedicated rename dialog, matching the behavior of desktop and mobile file
 * managers:
 *
 *  - opens with the keyboard up and the BASENAME selected for files (the
 *    extension survives a quick retype; ".gitignore"-style dotfiles and
 *    folders select the whole name),
 *  - validates LIVE on every keystroke: blank name, illegal characters,
 *    reserved "."/"..", unchanged name and same-directory conflicts are
 *    flagged in place and disable Rename - the user never has to press the
 *    button to discover a rejection,
 *  - warns - without blocking, Windows-style - when a file's extension would
 *    change,
 *  - Enter confirms, back/outside-tap cancels, double taps are buffered, and
 *    the repository's typed failures still surface inline if a race slips
 *    past the live checks (e.g. a file created after the sibling snapshot).
 */
@Composable
fun RenameDialog(
    viewModel: FileViewModel,
    item: FileItem,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val siblingNames by viewModel.renameSiblingNames.collectAsStateWithLifecycle()
    val nameError by viewModel.nameError.collectAsStateWithLifecycle()

    // Keyed on the target: a dialog reused for a second file must never show
    // the previous file's name or selection.
    var text by remember(item.path) { mutableStateOf(item.name) }
    var everEdited by remember(item.path) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Live verdict, recomputed per keystroke and again when the sibling
    // snapshot lands from the background listing.
    val validation = validateNewName(text, item.name, item.isDirectory, siblingNames)
    val canConfirm = validation.rejection == null

    // Buffered actions: the first tap/IME Done wins, its immediate follower
    // (double-tap, Done racing the tap) is ignored so the rename can never fire
    // twice. The window - not a one-shot latch - matters: the repository can
    // reject the rename and keep this dialog open on purpose, and a permanent
    // latch would swallow Cancel/X/back and strand the user here.
    var lastActionAt by remember(item.path) { mutableLongStateOf(0L) }
    fun buffered(action: () -> Unit) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastActionAt < 500L) return
        lastActionAt = now
        action()
    }
    // A rename the repository REJECTED re-arms the window immediately: the
    // 500 ms guard exists to eat the second half of a double-tap, not a
    // deliberate Cancel/X tap right after the error appeared.
    LaunchedEffect(nameError) {
        if (nameError != null) lastActionAt = 0L
    }

    fun confirmIfValid() {
        if (canConfirm) buffered { onConfirm(validation.trimmedName) }
    }

    LaunchedEffect(item.path) {
        runCatching { focusRequester.requestFocus() }
        // Focus alone doesn't reliably pop the keyboard - ask for it.
        keyboardController?.show()
    }

    ChunkyDialogShell(
        onDismiss = { buffered(onDismiss) },
        icon = Icons.Rounded.DriveFileRenameOutline,
        iconBackgroundColor = FolderColor,
        iconTint = FolderColor,
        title = stringResource(R.string.rename_title),
        subtitle = item.file.parent,
        confirmText = stringResource(R.string.dialog_rename),
        confirmEnabled = canConfirm,
        onConfirm = { confirmIfValid() }
    ) {
        // Files pre-select everything before the last dot (a dot at index 0
        // doesn't count), folders select the whole name.
        val initialSelection = remember(item.path) {
            val name = item.name
            val dot = if (item.isDirectory) -1 else name.lastIndexOf('.')
            if (dot > 0) TextRange(0, dot) else TextRange(0, name.length)
        }

        CursorScrollingTextField(
            value = text,
            onValueChange = {
                text = it
                everEdited = true
                // Any edit invalidates a previous server-side rejection: the
                // message would otherwise linger over a fixed name.
                if (nameError != null) viewModel.clearNameError()
            },
            placeholderText = stringResource(R.string.rename_placeholder),
            focusRequester = focusRequester,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { confirmIfValid() }),
            initialSelection = initialSelection,
            fieldKey = item.path
        )

        // Supporting text, in priority order: server-side rejection (race
        // safety net) > live rejection > extension-change warning. Live
        // messages only appear once the user actually edited the field, so a
        // freshly opened dialog never nags with "name unchanged".
        val liveRejectionText = when (validation.rejection) {
            RenameRejection.EMPTY -> stringResource(R.string.rename_error_empty)
            RenameRejection.INVALID_CHARACTERS -> stringResource(R.string.rename_error_invalid)
            RenameRejection.RESERVED_NAME -> stringResource(R.string.rename_error_reserved)
            RenameRejection.UNCHANGED -> stringResource(R.string.rename_hint_unchanged)
            RenameRejection.NAME_TAKEN -> stringResource(R.string.rename_error_exists)
            null -> null
        }
        val serverErrorText = when (nameError) {
            NameError.EMPTY -> stringResource(R.string.rename_error_empty)
            NameError.INVALID -> stringResource(R.string.rename_error_invalid)
            NameError.EXISTS -> stringResource(R.string.rename_error_exists)
            NameError.FAILED -> stringResource(R.string.rename_error_failed)
            null -> null
        }
        val supportingText: String? = when {
            serverErrorText != null -> serverErrorText
            !everEdited -> null
            validation.rejection != null -> liveRejectionText
            validation.extensionChanged -> stringResource(R.string.rename_warning_extension)
            else -> null
        }
        if (supportingText != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    serverErrorText != null -> MaterialTheme.colorScheme.error
                    validation.rejection == RenameRejection.UNCHANGED ->
                        MaterialTheme.colorScheme.onSurfaceVariant
                    validation.rejection != null -> MaterialTheme.colorScheme.error
                    else -> if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
                        WarningAmberDark
                    } else {
                        WarningAmberLight
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
