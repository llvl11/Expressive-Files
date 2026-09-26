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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.baiel.expressivefiles.R
import com.baiel.expressivefiles.model.ArchiveType
import com.baiel.expressivefiles.model.FileItem
import com.baiel.expressivefiles.model.FileType
import com.baiel.expressivefiles.ui.theme.ChunkyIconShape
import com.baiel.expressivefiles.ui.theme.ChunkySheetShape
import com.baiel.expressivefiles.ui.theme.ChunkyTileShape
import com.baiel.expressivefiles.ui.theme.PillShape

private data class ActionSpec(
    val icon: ImageVector,
    val title: String,
    val action: (FileItem) -> Unit,
    val destructive: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileActionSheet(
    targetItem: FileItem,
    onDismiss: () -> Unit,
    onOpen: (FileItem) -> Unit,
    onOpenWithExternalApp: (FileItem) -> Unit,
    onCopy: (FileItem) -> Unit,
    onCut: (FileItem) -> Unit,
    onRename: (FileItem) -> Unit,
    onDelete: (FileItem) -> Unit,
    onShare: (FileItem) -> Unit,
    onCompress: (FileItem) -> Unit,
    onExtract: (FileItem) -> Unit,
    onInspectArchive: (FileItem) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val themedFolderColor = MaterialTheme.colorScheme.primary
    val (icon, color) = getFileIconAndColor(targetItem, themedFolderColor)
    // Extract/Inspect are offered only for formats the engine can actually
    // READ - same gate as FileViewModel.openFile. .gz/.xz/.iso/.zst are typed
    // ARCHIVE for the category filter but resolve to ArchiveType.OTHER, which
    // the inspector cannot list (it falls through to the ZIP reader and
    // throws) and extraction fails on (tar parse of compressed bytes).
    val isReadableArchive = targetItem.fileType == FileType.ARCHIVE &&
        targetItem.archiveType != null &&
        targetItem.archiveType != ArchiveType.OTHER


    val sheetOpenFolder = stringResource(R.string.sheet_open_folder)
    val sheetOpenWithApp = stringResource(R.string.sheet_open_with_app)
    val sheetOpenExternal = stringResource(R.string.sheet_open_external)
    // Shared meta date: localized while attributes stream in.
    val dateLoadingLabel = stringResource(R.string.list_date_loading)
    val infoDate = formatDate(targetItem.lastModified)
        .takeUnless { it == DATE_LOADING_SENTINEL } ?: dateLoadingLabel
    val infoMeta = if (targetItem.isDirectory) {
        "${targetItem.childCount?.let { pluralStringResource(R.plurals.clipboard_items_suffix, it, it) } ?: stringResource(R.string.list_empty_dir)} • $infoDate"
    } else {
        "${targetItem.size?.let { formatFileSize(it) } ?: "..."} • $infoDate"
    }
    // One uniform menu for every file kind; archive-only actions join the
    // same compact rows so nothing renders differently per type.
    val openTitle = if (targetItem.isDirectory) sheetOpenFolder else sheetOpenWithApp
    val actions = buildList {
        add(ActionSpec(Icons.AutoMirrored.Rounded.OpenInNew, openTitle, onOpen))
        // Explicit chooser entry so any capable app can be picked, even when a
        // default handler for this type is already remembered by the system.
        if (!targetItem.isDirectory) {
            add(ActionSpec(Icons.Rounded.Apps, sheetOpenExternal, onOpenWithExternalApp))
        }
        if (isReadableArchive) {
            add(ActionSpec(Icons.Rounded.Unarchive, stringResource(R.string.sheet_extract_all), onExtract))
            add(ActionSpec(Icons.Rounded.FolderZip, stringResource(R.string.sheet_inspect), onInspectArchive))
        }
        add(ActionSpec(Icons.Rounded.ContentCopy, stringResource(R.string.sheet_copy), onCopy))
        add(ActionSpec(Icons.Rounded.ContentCut, stringResource(R.string.sheet_cut), onCut))
        add(ActionSpec(Icons.Rounded.Archive, stringResource(R.string.sheet_compress), onCompress))
        add(ActionSpec(Icons.Rounded.DriveFileRenameOutline, stringResource(R.string.sheet_rename), onRename))
        if (!targetItem.isDirectory) {
            add(ActionSpec(Icons.Rounded.Share, stringResource(R.string.sheet_share), onShare))
        }
        add(ActionSpec(Icons.Rounded.Delete, stringResource(R.string.sheet_delete), onDelete, destructive = true))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = ChunkySheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        // Real handle slot keeps the sheet's settle-anchor math stable; a null
        // handle + hand-drawn pill made over-flicks jitter while anchors fought.
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .size(width = 40.dp, height = 5.dp)
                    .clip(PillShape)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp, start = 20.dp, end = 20.dp)
        ) {

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
                        text = infoMeta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                CloseIconButton(onClick = onDismiss)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                actions.forEach { spec ->
                    ActionItemRow(
                        icon = spec.icon,
                        title = spec.title,
                        tint = if (spec.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            onDismiss()
                            spec.action(targetItem)
                        }
                    )
                }
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
    // Tile shape, not the cookie: on a wide short row the cookie's lobes
    // bite into the leading icon (see the clipped action glyphs).
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ChunkyTileShape)
            .clickable(onClick = onClick),
        shape = ChunkyTileShape,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = tint,
                fontSize = 15.sp
            )
        }
    }
}
