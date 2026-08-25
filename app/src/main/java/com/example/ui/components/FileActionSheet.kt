package com.example.ui.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FileItem
import com.example.model.FileType
import com.example.ui.theme.ChunkyIconShape
import com.example.ui.theme.ChunkySheetShape
import com.example.ui.theme.ChunkyTileShape
import com.example.ui.theme.PillShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileActionSheet(
    targetItem: FileItem,
    onDismiss: () -> Unit,
    onOpen: (FileItem) -> Unit,
    onCopy: (FileItem) -> Unit,
    onCut: (FileItem) -> Unit,
    onRename: (FileItem) -> Unit,
    onDelete: (FileItem) -> Unit,
    onShare: (FileItem) -> Unit,
    onCompress: (FileItem) -> Unit,
    onExtract: (FileItem) -> Unit,
    onInspectArchive: (FileItem) -> Unit,
    onDetails: (FileItem) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val themedFolderColor = MaterialTheme.colorScheme.primary
    val (icon, color) = getFileIconAndColor(targetItem, themedFolderColor)
    val isArchive = targetItem.fileType == FileType.ARCHIVE

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = ChunkySheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 28.dp, start = 20.dp, end = 20.dp)
        ) {
            // Drag Indicator
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 5.dp)
                    .clip(PillShape)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // File Info Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                CookieIconContainer(
                    backgroundColor = color,
                    size = 52.dp,
                    shape = ChunkyIconShape
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = targetItem.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (targetItem.isDirectory) "${targetItem.childCount} items • ${formatDate(targetItem.lastModified)}"
                               else "${formatFileSize(targetItem.size)} • ${formatDate(targetItem.lastModified)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

            // Archive-Specific Highlight Actions
            if (isArchive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChunkyButton(
                        text = "Extract All",
                        onClick = {
                            onDismiss()
                            onExtract(targetItem)
                        },
                        icon = Icons.Rounded.Unarchive,
                        modifier = Modifier.weight(1f)
                    )
                    ChunkyButton(
                        text = "Inspect",
                        onClick = {
                            onDismiss()
                            onInspectArchive(targetItem)
                        },
                        icon = Icons.Rounded.FolderZip,
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Grid of Actions
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionItemRow(
                    icon = Icons.Rounded.OpenInNew,
                    title = if (targetItem.isDirectory) "Open Folder" else "Open / Preview",
                    onClick = {
                        onDismiss()
                        onOpen(targetItem)
                    }
                )

                ActionItemRow(
                    icon = Icons.Rounded.ContentCopy,
                    title = "Copy to Clipboard",
                    onClick = {
                        onDismiss()
                        onCopy(targetItem)
                    }
                )

                ActionItemRow(
                    icon = Icons.Rounded.ContentCut,
                    title = "Move / Cut",
                    onClick = {
                        onDismiss()
                        onCut(targetItem)
                    }
                )

                ActionItemRow(
                    icon = Icons.Rounded.Archive,
                    title = "Compress to Archive (.zip, .7z, .tar)",
                    onClick = {
                        onDismiss()
                        onCompress(targetItem)
                    }
                )

                ActionItemRow(
                    icon = Icons.Rounded.DriveFileRenameOutline,
                    title = "Rename",
                    onClick = {
                        onDismiss()
                        onRename(targetItem)
                    }
                )

                if (!targetItem.isDirectory) {
                    ActionItemRow(
                        icon = Icons.Rounded.Share,
                        title = "Share File",
                        onClick = {
                            onDismiss()
                            onShare(targetItem)
                        }
                    )
                }

                ActionItemRow(
                    icon = Icons.Rounded.Info,
                    title = "File Details & Info",
                    onClick = {
                        onDismiss()
                        onDetails(targetItem)
                    }
                )

                ActionItemRow(
                    icon = Icons.Rounded.Delete,
                    title = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = {
                        onDismiss()
                        onDelete(targetItem)
                    }
                )
            }
        }
    }
}

@Composable
private fun ActionItemRow(
    icon: ImageVector,
    title: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ChunkyIconShape)
            .clickable(onClick = onClick),
        shape = ChunkyIconShape,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = tint
            )
        }
    }
}
