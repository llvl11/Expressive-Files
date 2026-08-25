package com.example.ui.components

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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ChunkyIconShape
import com.example.ui.theme.ChunkyTileShape
import com.example.ui.theme.FolderColor
import com.example.ui.theme.PillShape

enum class DialogActionType {
    NEW_FOLDER,
    NEW_FILE,
    RENAME
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewItemDialog(
    type: DialogActionType,
    initialValue: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textValue by remember { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val title = when (type) {
        DialogActionType.NEW_FOLDER -> "New Folder"
        DialogActionType.NEW_FILE -> "New File"
        DialogActionType.RENAME -> "Rename"
    }

    val icon = when (type) {
        DialogActionType.NEW_FOLDER -> Icons.Rounded.CreateNewFolder
        DialogActionType.NEW_FILE -> Icons.Rounded.NoteAdd
        DialogActionType.RENAME -> Icons.Rounded.DriveFileRenameOutline
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = ChunkyTileShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(0.92f)
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
                            backgroundColor = FolderColor,
                            size = 44.dp,
                            shape = ChunkyIconShape
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = FolderColor,
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

                    ChunkyIconButton(
                        icon = Icons.Rounded.Close,
                        onClick = onDismiss,
                        size = 36.dp,
                        shape = PillShape,
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    placeholder = {
                        Text(
                            when (type) {
                                DialogActionType.NEW_FOLDER -> "Folder name"
                                DialogActionType.NEW_FILE -> "file.txt"
                                DialogActionType.RENAME -> "New name"
                            }
                        )
                    },
                    singleLine = true,
                    shape = ChunkyIconShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (textValue.isNotBlank()) {
                                onConfirm(textValue.trim())
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChunkyButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    ChunkyButton(
                        text = if (type == DialogActionType.RENAME) "Rename" else "Create",
                        onClick = {
                            if (textValue.isNotBlank()) {
                                onConfirm(textValue.trim())
                            }
                        },
                        enabled = textValue.isNotBlank(),
                        modifier = Modifier.weight(1.3f)
                    )
                }
            }
        }
    }
}
