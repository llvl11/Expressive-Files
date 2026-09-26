package com.baiel.expressivefiles.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baiel.expressivefiles.R
import com.baiel.expressivefiles.ui.theme.ChunkyTileShape
import com.baiel.expressivefiles.ui.theme.ExpressiveRoundedShape
import com.baiel.expressivefiles.ui.theme.FolderColor
import com.baiel.expressivefiles.viewmodel.FileViewModel
import com.baiel.expressivefiles.viewmodel.NameError

enum class DialogActionType {
    NEW_FOLDER,
    NEW_FILE,
    DELETE_CONFIRM
}

@Composable
fun NewItemDialog(
    viewModel: FileViewModel,
    type: DialogActionType,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val pendingItems by viewModel.pendingDeleteItems.collectAsStateWithLifecycle()
    var textValue by remember(type) { mutableStateOf("") }
    // Rejection reason from the last confirm attempt (duplicate name, illegal
    // characters, ...). Cleared as soon as the user edits the field.
    val nameError by viewModel.nameError.collectAsStateWithLifecycle()
    val nameErrorText = when (nameError) {
        null -> null
        NameError.EMPTY -> stringResource(R.string.rename_error_empty)
        NameError.INVALID -> stringResource(R.string.rename_error_invalid)
        NameError.EXISTS -> stringResource(R.string.rename_error_exists)
        NameError.FAILED -> stringResource(R.string.rename_error_failed)
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Buffered actions: a tap/IME-Done within the window wins and tears the
    // dialog down; the immediate follower of a double-tap (or Done racing the
    // tap) is ignored so an action can never fire twice. Unlike a one-shot
    // latch this cannot deadlock: a confirm the ViewModel REJECTS keeps the
    // dialog open, and the window below re-arms so Cancel/X/back keep working.
    var lastActionAt by remember { mutableLongStateOf(0L) }
    fun buffered(action: () -> Unit) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastActionAt < 500L) return
        lastActionAt = now
        action()
    }
    // A confirm the ViewModel REJECTED re-arms the window immediately: the
    // 500 ms guard exists to eat the second half of a double-tap, not a
    // deliberate Cancel/X tap right after the error appeared.
    LaunchedEffect(nameError) {
        if (nameError != null) lastActionAt = 0L
    }

    val title = when (type) {
        DialogActionType.NEW_FOLDER -> stringResource(R.string.new_folder_title)
        DialogActionType.NEW_FILE -> stringResource(R.string.new_file_title)
        DialogActionType.DELETE_CONFIRM -> stringResource(R.string.delete_confirm_title)
    }

    val icon = when (type) {
        DialogActionType.NEW_FOLDER -> Icons.Rounded.CreateNewFolder
        DialogActionType.NEW_FILE -> Icons.AutoMirrored.Rounded.NoteAdd
        DialogActionType.DELETE_CONFIRM -> Icons.Rounded.Delete
    }

    val isDelete = type == DialogActionType.DELETE_CONFIRM

    if (!isDelete) {
        LaunchedEffect(Unit) {
            runCatching { focusRequester.requestFocus() }
            // Focus alone doesn't reliably pop the keyboard - ask for it.
            keyboardController?.show()
        }
    }

    Dialog(onDismissRequest = { buffered(onDismiss) }) {
        Surface(
            shape = ChunkyTileShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(if (isDelete) 0.95f else 0.92f)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CookieIconContainer(
                            backgroundColor = if (isDelete) MaterialTheme.colorScheme.error else FolderColor,
                            size = 44.dp,
                            shape = ExpressiveRoundedShape
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isDelete) MaterialTheme.colorScheme.error else FolderColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    CloseIconButton(onClick = { buffered(onDismiss) })
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (isDelete) {
                    val message = when (pendingItems.size) {
                        0 -> pluralStringResource(R.plurals.delete_many_message, 0, 0)
                        1 -> stringResource(R.string.delete_single_message, pendingItems.first().name)
                        else -> pluralStringResource(
                            R.plurals.delete_many_message,
                            pendingItems.size,
                            pendingItems.size
                        )
                    }
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    CursorScrollingTextField(
                        value = textValue,
                        onValueChange = {
                            textValue = it
                            // Any edit invalidates the previous rejection: the
                            // message would otherwise linger over a fixed name.
                            if (nameError != null) viewModel.clearNameError()
                        },
                        placeholderText = when (type) {
                            DialogActionType.NEW_FOLDER -> stringResource(R.string.new_folder_placeholder)
                            DialogActionType.NEW_FILE -> stringResource(R.string.new_file_placeholder)
                            // Unreachable: the delete branch never renders a text field.
                            DialogActionType.DELETE_CONFIRM -> ""
                        },
                        focusRequester = focusRequester,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (textValue.isNotBlank()) {
                                    buffered { onConfirm(textValue.trim()) }
                                }
                            }
                        )
                    )
                    if (nameErrorText != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = nameErrorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChunkyButton(
                        text = stringResource(R.string.dialog_cancel),
                        onClick = { buffered(onDismiss) },
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    ChunkyButton(
                        text = when {
                            isDelete -> stringResource(R.string.dialog_delete)
                            else -> stringResource(R.string.dialog_create)
                        },
                        onClick = {
                            if (isDelete || textValue.isNotBlank()) {
                                buffered { onConfirm(textValue.trim()) }
                            }
                        },
                        backgroundColor = if (isDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        contentColor = if (isDelete) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                        enabled = isDelete || textValue.isNotBlank(),
                        modifier = Modifier.weight(if (isDelete) 1.5f else 1.3f)
                    )
                }
            }
        }
    }
}
