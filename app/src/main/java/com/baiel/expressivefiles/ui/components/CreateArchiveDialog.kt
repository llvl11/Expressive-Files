package com.baiel.expressivefiles.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baiel.expressivefiles.R
import com.baiel.expressivefiles.model.ArchiveType
import com.baiel.expressivefiles.model.FileItem
import com.baiel.expressivefiles.model.OsType
import com.baiel.expressivefiles.ui.theme.ArchiveZipColor
import com.baiel.expressivefiles.ui.theme.ChunkyIconShape
import com.baiel.expressivefiles.ui.theme.ChunkyTileShape
import com.baiel.expressivefiles.ui.theme.ExpressiveRoundedShape
import com.baiel.expressivefiles.ui.theme.LocalOsType
import com.baiel.expressivefiles.ui.theme.OsIcons
import com.baiel.expressivefiles.ui.theme.PillShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateArchiveDialog(
    selectedFiles: List<FileItem>,
    defaultFormat: ArchiveType,
    onDismiss: () -> Unit,
    onCompress: (name: String, format: ArchiveType) -> Unit
) {
    val initialName = if (selectedFiles.size == 1) {
        selectedFiles.first().name.substringBeforeLast('.') + "_archive"
    } else {
        "archive_${System.currentTimeMillis() / 1000}"
    }

    var archiveName by remember { mutableStateOf(initialName) }
    var selectedFormat by remember {
        mutableStateOf(if (defaultFormat.canCreate) defaultFormat else ArchiveType.ZIP)
    }

    // Buffered actions: the first tap on Cancel/Compress wins and tears the
    // dialog down immediately (compression continues in the archive popup);
    // further taps are ignored so an action can never fire twice.
    var actionHandled by remember { mutableStateOf(false) }
    fun buffered(action: () -> Unit) {
        if (!actionHandled) {
            actionHandled = true
            action()
        }
    }

    BasicAlertDialog(onDismissRequest = { buffered(onDismiss) }) {
        val dialogShadow = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.dp else 16.dp
        Surface(
            shape = ChunkyTileShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = dialogShadow,
            modifier = Modifier
                .fillMaxWidth(0.98f)
                .dialogEnterMotion()
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
                            backgroundColor = ArchiveZipColor,
                            size = 44.dp,
                            shape = ChunkyIconShape
                        ) {
                            Icon(
                                imageVector = OsIcons.archive(LocalOsType.current),
                                contentDescription = null,
                                tint = ArchiveZipColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.archive_create_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = pluralStringResource(
                                    R.plurals.archive_items_selected,
                                    selectedFiles.size,
                                    selectedFiles.size
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    CloseIconButton(onClick = { buffered(onDismiss) })
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Archive Name Input
                Text(
                    text = stringResource(R.string.archive_name_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (LocalOsType.current == OsType.MAGIC_OS) {
                    // MagicOS: borderless filled field.
                    TextField(
                        value = archiveName,
                        onValueChange = { archiveName = it },
                        singleLine = true,
                        shape = ExpressiveRoundedShape,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        trailingIcon = {
                            Text(
                                text = ".${selectedFormat.extension}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (archiveName.isNotBlank()) {
                                    buffered { onCompress(archiveName.trim(), selectedFormat) }
                                }
                            }
                        )
                    )
                } else {
                    OutlinedTextField(
                        value = archiveName,
                        onValueChange = { archiveName = it },
                        singleLine = true,
                        shape = ExpressiveRoundedShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        trailingIcon = {
                            Text(
                                text = ".${selectedFormat.extension}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (archiveName.isNotBlank()) {
                                    buffered { onCompress(archiveName.trim(), selectedFormat) }
                                }
                            }
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Compression Format Selection
                Text(
                    text = stringResource(R.string.archive_format_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CREATABLE_ARCHIVE_FORMATS.forEach { format ->
                        val isSelected = selectedFormat == format
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(PillShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .clickable { selectedFormat = format },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = format.label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChunkyButton(
                        text = stringResource(R.string.dialog_cancel),
                        onClick = { buffered(onDismiss) },
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1.3f)
                    )

                    ChunkyButton(
                        text = stringResource(R.string.archive_compress),
                        onClick = {
                            if (archiveName.isNotBlank()) {
                                buffered { onCompress(archiveName.trim(), selectedFormat) }
                            }
                        },
                        icon = Icons.Rounded.Archive,
                        enabled = archiveName.isNotBlank(),
                        modifier = Modifier.weight(2f)
                    )
                }
            }
        }
    }
}

