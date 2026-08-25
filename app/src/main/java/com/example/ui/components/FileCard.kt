package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.ArchiveType
import com.example.model.FileItem
import com.example.model.FileType
import com.example.ui.theme.ApkColor
import com.example.ui.theme.Archive7zColor
import com.example.ui.theme.ArchiveRarColor
import com.example.ui.theme.ArchiveTarColor
import com.example.ui.theme.ArchiveZipColor
import com.example.ui.theme.AudioColor
import com.example.ui.theme.ChunkyIconShape
import com.example.ui.theme.ChunkyTileShape
import com.example.ui.theme.CodeColor
import com.example.ui.theme.DocumentColor
import com.example.ui.theme.FolderColor
import com.example.ui.theme.ImageColor
import com.example.ui.theme.OtherFileColor
import com.example.ui.theme.PillShape
import com.example.ui.theme.VideoColor
import androidx.compose.foundation.ExperimentalFoundationApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape

fun getFileIconAndColor(item: FileItem, themedFolderColor: Color? = null): Pair<ImageVector, Color> {
    if (item.isDirectory) {
        return Pair(Icons.Rounded.Folder, themedFolderColor ?: FolderColor)
    }

    return when (item.fileType) {
        FileType.ARCHIVE -> {
            when (item.archiveType) {
                ArchiveType.ZIP -> Pair(Icons.Rounded.FolderZip, ArchiveZipColor)
                ArchiveType.SEVEN_Z -> Pair(Icons.Rounded.Archive, Archive7zColor)
                ArchiveType.TAR, ArchiveType.TAR_GZ -> Pair(Icons.Rounded.Unarchive, ArchiveTarColor)
                ArchiveType.RAR -> Pair(Icons.Rounded.Archive, ArchiveRarColor)
                else -> Pair(Icons.Rounded.FolderZip, ArchiveZipColor)
            }
        }
        FileType.IMAGE -> Pair(Icons.Rounded.Image, ImageColor)
        FileType.VIDEO -> Pair(Icons.Rounded.Movie, VideoColor)
        FileType.AUDIO -> Pair(Icons.Rounded.Audiotrack, AudioColor)
        FileType.DOCUMENT -> Pair(Icons.Rounded.Description, DocumentColor)
        FileType.CODE -> Pair(Icons.Rounded.Code, CodeColor)
        FileType.APK -> Pair(Icons.Rounded.Android, ApkColor)
        else -> Pair(Icons.Rounded.InsertDriveFile, OtherFileColor)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.getDefault(), "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}

fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val sdf = SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListCard(
    item: FileItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier
) {
    val themedFolderColor = MaterialTheme.colorScheme.primary
    val (icon, color) = getFileIconAndColor(item, themedFolderColor)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(24.dp),
        color = if (item.isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (item.isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        shadowElevation = if (item.isSelected) 3.dp else 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon or Image Thumbnail
            if (item.fileType == FileType.IMAGE && item.file.exists()) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(color.copy(alpha = 0.16f))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.file)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                CookieIconContainer(
                    backgroundColor = color,
                    size = 48.dp,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // File Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Show Archive Format badge if archive
                    if (item.fileType == FileType.ARCHIVE && item.archiveType != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        CookieBadge(
                            text = item.archiveType.name.replace('_', '.'),
                            backgroundColor = color.copy(alpha = 0.15f),
                            contentColor = color
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (item.isDirectory) "${item.childCount} items" else formatFileSize(item.size),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = formatDate(item.lastModified),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Selection Checkbox or Action Menu Button
            if (isSelectionMode) {
                Checkbox(
                    checked = item.isSelected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            } else {
                ChunkyIconButton(
                    icon = Icons.Rounded.MoreVert,
                    onClick = onMoreClick,
                    size = 36.dp,
                    shape = PillShape,
                    backgroundColor = Color.Transparent,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridCard(
    item: FileItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier
) {
    val themedFolderColor = MaterialTheme.colorScheme.primary
    val (icon, color) = getFileIconAndColor(item, themedFolderColor)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(24.dp),
        color = if (item.isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (item.isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        shadowElevation = if (item.isSelected) 3.dp else 1.dp
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Large Thumbnail / Cookie Icon
                if (item.fileType == FileType.IMAGE && item.file.exists()) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(color.copy(alpha = 0.16f))
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(item.file)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    CookieIconContainer(
                        backgroundColor = color,
                        size = 68.dp,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Name
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Details (Size or items)
                Text(
                    text = if (item.isDirectory) "${item.childCount} items" else formatFileSize(item.size),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Top right badge/selection indicator
            if (isSelectionMode) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Checkbox(
                        checked = item.isSelected,
                        onCheckedChange = { onClick() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            } else if (item.fileType == FileType.ARCHIVE && item.archiveType != null) {
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    CookieBadge(
                        text = item.archiveType.name.replace('_', '.'),
                        backgroundColor = color.copy(alpha = 0.15f),
                        contentColor = color
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileExpressiveCard(
    item: FileItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier
) {
    val themedFolderColor = MaterialTheme.colorScheme.primary
    val (icon, color) = getFileIconAndColor(item, themedFolderColor)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(26.dp),
        color = if (item.isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (item.isSelected) 2.dp else 1.dp,
            if (item.isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        shadowElevation = if (item.isSelected) 4.dp else 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Hero Visual Canvas / Thumbnail Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (item.fileType == FileType.IMAGE && item.file.exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.file)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CookieIconContainer(
                        backgroundColor = color,
                        size = 60.dp,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Top start selection indicator or item badge
                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Checkbox(
                            checked = item.isSelected,
                            onCheckedChange = { onClick() },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                checkmarkColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                // Top end badge
                if (item.fileType == FileType.ARCHIVE && item.archiveType != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        CookieBadge(
                            text = item.archiveType.name.replace('_', '.'),
                            backgroundColor = color.copy(alpha = 0.2f),
                            contentColor = color
                        )
                    }
                } else if (item.isDirectory) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        CookieBadge(
                            text = "FOLDER",
                            backgroundColor = color.copy(alpha = 0.18f),
                            contentColor = color
                        )
                    }
                }
            }

            // Text Info & Details Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (!isSelectionMode) {
                        ChunkyIconButton(
                            icon = Icons.Rounded.MoreVert,
                            onClick = onMoreClick,
                            size = 32.dp,
                            shape = PillShape,
                            backgroundColor = Color.Transparent,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (item.isDirectory) "${item.childCount} items" else formatFileSize(item.size),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = formatDate(item.lastModified),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}
