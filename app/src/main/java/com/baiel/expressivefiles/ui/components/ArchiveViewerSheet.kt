package com.baiel.expressivefiles.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.baiel.expressivefiles.R
import com.baiel.expressivefiles.archive.ArchiveEngine
import com.baiel.expressivefiles.model.ArchiveEntryItem
import com.baiel.expressivefiles.model.FileItem
import com.baiel.expressivefiles.model.archiveEntryFileType
import com.baiel.expressivefiles.ui.components.fileTypeIcon
import com.baiel.expressivefiles.ui.theme.ChunkyIconShape
import com.baiel.expressivefiles.ui.theme.ChunkySheetShape
import com.baiel.expressivefiles.ui.theme.ExpressiveRoundedShape
import com.baiel.expressivefiles.ui.theme.FolderColor
import com.baiel.expressivefiles.ui.theme.PillShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Archive inspector as a plain anchored Dialog instead of ModalBottomSheet.
 *
 * Why not M3's sheet: with skipPartiallyExpanded + a tall inner LazyColumn,
 * upward drags that reach the list's top edge are handed to the sheet, whose
 * anchor recalculation against the fractional-height content oscillated
 * forever ("endless jitter"). This overlay has NO draggable anchors at all -
 * the list scrolls inside, and dismissal happens only via the header drag or
 * tapping outside - so the failure mode cannot exist.
 */
@Composable
fun ArchiveViewerSheet(
    archiveItem: FileItem,
    onDismiss: () -> Unit,
    onExtractArchive: (FileItem) -> Unit
) {
    var entries by remember { mutableStateOf<List<ArchiveEntryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Central-directory parsing is blocking I/O and can take hundreds of ms
    // for large archives; it must never run on the main thread.
    LaunchedEffect(archiveItem.path) {
        isLoading = true
        try {
            entries = withContext(Dispatchers.IO) {
                ArchiveEngine.listArchiveEntries(archiveItem.file)
            }
            loadFailed = false
        } catch (_: Exception) {
            // Corrupt/password-protected/unreadable archives surface as an
            // error state instead of crashing the sheet coroutine.
            loadFailed = true
        } finally {
            isLoading = false
        }
    }

    val filteredEntries = remember(entries, searchQuery) {
        if (searchQuery.isBlank()) entries
        else entries.filter { it.path.contains(searchQuery, ignoreCase = true) }
    }

    val totalUncompressed = remember(entries) {
        entries.sumOf { it.size }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {

            // Drag-to-dismiss offset (header area only, so list scrolling is
            // never intercepted).
            var dragY by remember { mutableFloatStateOf(0f) }
            val smoothedY by animateFloatAsState(
                targetValue = dragY,
                animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
                label = "archive_sheet_drag"
            )
            val dismissThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }

            Surface(
                shape = ChunkySheetShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.87f)
                    .graphicsLayer { translationY = smoothedY.coerceAtLeast(0f) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 24.dp, start = 20.dp, end = 20.dp)
                ) {
                    // Handle + header form one dedicated drag region.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onVerticalDrag = { change, dy ->
                                        change.consume()
                                        dragY = (dragY + dy).coerceAtLeast(0f)
                                    },
                                    onDragEnd = {
                                        if (dragY > dismissThresholdPx) onDismiss() else dragY = 0f
                                    },
                                    onDragCancel = { dragY = 0f }
                                )
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 5.dp)
                                .clip(PillShape)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                val (_, color) = getFileIconAndColor(archiveItem)
                                CookieIconContainer(
                                    backgroundColor = color,
                                    size = 48.dp,
                                    shape = ChunkyIconShape
                                ) {
                                    Icon(
                                        imageVector = fileTypeIcon(archiveItem.fileType),
                                        contentDescription = null,
                                        tint = color,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = archiveItem.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${pluralStringResource(R.plurals.viewer_entries_count, entries.size, entries.size)} • ${
                                            stringResource(
                                                R.string.viewer_uncompressed_size,
                                                formatFileSize(totalUncompressed)
                                            )
                                        }",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            CloseIconButton(onClick = onDismiss)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.viewer_filter_hint)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            shape = ExpressiveRoundedShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when {
                            isLoading -> Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.viewer_reading_table),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            loadFailed -> Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.viewer_archive_error),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            filteredEntries.isEmpty() -> Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (entries.isEmpty()) stringResource(R.string.viewer_archive_empty) else stringResource(R.string.viewer_no_matches),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            else -> LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Key includes the index: ZIP/TAR containers may
                                // legally hold two entries with the same name, and
                                // a path-only key crashes LazyColumn with
                                // "Key ... was already used" during measure.
                                itemsIndexed(filteredEntries, key = { index, entry ->
                                    "$index:${entry.path}"
                                }) { _, entry ->
                                    Surface(
                                        shape = ChunkyIconShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (entry.isDirectory) Icons.Rounded.Folder
                                                // archiveEntryFileType lowercases the extension: entries
                                                // bypass FileItem.fromAttrs, so PHOTO.JPG used to miss the
                                                // lowercase extension sets and render generically.
                                                else fileTypeIcon(archiveEntryFileType(entry.name, false)),
                                                contentDescription = null,
                                                tint = if (entry.isDirectory) FolderColor else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = entry.path,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (!entry.isDirectory) {
                                                    // The "(x compressed)" half is only printed when the
                                                    // engine actually knows a smaller compressed size - TAR
                                                    // and 7z report -1 (unknown), which would otherwise read
                                                    // "12 MB (12 MB compressed)".
                                                    val compressed = entry.compressedSize
                                                    Text(
                                                        text = if (compressed in 1 until entry.size) {
                                                            stringResource(
                                                                R.string.viewer_entry_sizes,
                                                                formatFileSize(entry.size),
                                                                formatFileSize(compressed)
                                                            )
                                                        } else {
                                                            stringResource(R.string.viewer_entry_size, formatFileSize(entry.size))
                                                        },
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    ChunkyButton(
                        text = stringResource(R.string.viewer_extract_all),
                        onClick = {
                            onDismiss()
                            onExtractArchive(archiveItem)
                        },
                        icon = Icons.Rounded.Unarchive,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
