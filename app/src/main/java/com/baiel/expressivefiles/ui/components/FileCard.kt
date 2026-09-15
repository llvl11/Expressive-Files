@file:Suppress("SpellCheckingInspection")

package com.baiel.expressivefiles.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMicros
import coil.size.Precision
import com.baiel.expressivefiles.R
import com.baiel.expressivefiles.model.ArchiveType
import com.baiel.expressivefiles.model.FileItem
import com.baiel.expressivefiles.model.FileType
import com.baiel.expressivefiles.model.OsType
import com.baiel.expressivefiles.ui.theme.ApkColor
import com.baiel.expressivefiles.ui.theme.Archive7zColor
import com.baiel.expressivefiles.ui.theme.ArchiveColor
import com.baiel.expressivefiles.ui.theme.ArchiveRarColor
import com.baiel.expressivefiles.ui.theme.ArchiveTarColor
import com.baiel.expressivefiles.ui.theme.ArchiveZipColor
import com.baiel.expressivefiles.ui.theme.AudioColor
import com.baiel.expressivefiles.ui.theme.CodeColor
import com.baiel.expressivefiles.ui.theme.DocumentColor
import com.baiel.expressivefiles.ui.theme.FolderColor
import com.baiel.expressivefiles.ui.theme.ImageColor
import com.baiel.expressivefiles.ui.theme.LocalOsType
import com.baiel.expressivefiles.ui.theme.OsIcons
import com.baiel.expressivefiles.ui.theme.OtherFileColor
import com.baiel.expressivefiles.ui.theme.PillShape
import com.baiel.expressivefiles.ui.theme.VideoColor
import com.baiel.expressivefiles.ui.theme.osCardShape
import com.baiel.expressivefiles.util.DirectVideoDecoderFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

fun getFileIconAndColor(
    item: FileItem,
    themedFolderColor: Color? = null,
    osType: OsType = OsType.PIXEL
): Pair<ImageVector, Color> {
    if (item.isDirectory) {
        return Pair(OsIcons.folder(osType), themedFolderColor ?: FolderColor)
    }

    return when (item.fileType) {
        FileType.ARCHIVE -> {
            when (osType) {
                // MagicOS skin uses one uniform glyph for every format.
                OsType.MAGIC_OS -> Pair(OsIcons.archive(osType), ArchiveColor)
                else -> when (item.archiveType) {
                    ArchiveType.ZIP -> Pair(Icons.Rounded.FolderZip, ArchiveZipColor)
                    ArchiveType.SEVEN_Z -> Pair(Icons.Rounded.Archive, Archive7zColor)
                    ArchiveType.TAR, ArchiveType.TAR_GZ -> Pair(Icons.Rounded.Unarchive, ArchiveTarColor)
                    ArchiveType.RAR -> Pair(Icons.Rounded.Archive, ArchiveRarColor)
                    else -> Pair(Icons.Rounded.FolderZip, ArchiveZipColor)
                }
            }
        }
        FileType.IMAGE -> Pair(OsIcons.fileType(FileType.IMAGE, osType), ImageColor)
        FileType.VIDEO -> Pair(OsIcons.fileType(FileType.VIDEO, osType), VideoColor)
        FileType.AUDIO -> Pair(OsIcons.fileType(FileType.AUDIO, osType), AudioColor)
        FileType.DOCUMENT -> Pair(OsIcons.fileType(FileType.DOCUMENT, osType), DocumentColor)
        FileType.CODE -> Pair(OsIcons.fileType(FileType.CODE, osType), CodeColor)
        FileType.APK -> Pair(OsIcons.fileType(FileType.APK, osType), ApkColor)
        else -> Pair(OsIcons.fileType(FileType.OTHER, osType), OtherFileColor)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.getDefault(), "%.1f %sB", bytes / 1024.0.pow(exp.toDouble()), pre)
}

/** Sentinel returned while a stat has not arrived yet; callers localize it. */
const val DATE_LOADING_SENTINEL = "Loading..."

fun formatDate(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0) return DATE_LOADING_SENTINEL
    // Compose UI is main-thread confined, so a single shared formatter is safe
    // and avoids allocating a SimpleDateFormat on every recomposition while scrolling.
    return dateFormat().format(Date(timestamp))
}

private const val DATE_PATTERN = "MMM d, yyyy  h:mm a"

// Rebuilt whenever the system locale changes so month/day names follow the
// active locale instead of freezing the one captured at process start.
private var sharedDateLocale: Locale? = null
private var sharedDateFormat: SimpleDateFormat? = null

private fun dateFormat(): SimpleDateFormat {
    val locale = Locale.getDefault()
    val current = sharedDateFormat
    if (current == null || sharedDateLocale != locale) {
        // Freshly built and returned directly, so the shared field is never
        // read back through a non-null assertion.
        val rebuilt = SimpleDateFormat(DATE_PATTERN, locale)
        sharedDateFormat = rebuilt
        sharedDateLocale = locale
        return rebuilt
    }
    return current
}

/** Badge label underlines: TAR_GZ -> TAR.GZ (single source, previously inline in 5 places). */
val ArchiveType.label: String get() = name.replace('_', '.')

/** Formats every archive type that can be produced by this app. */
val CREATABLE_ARCHIVE_FORMATS = listOf(
    ArchiveType.ZIP,
    ArchiveType.SEVEN_Z,
    ArchiveType.TAR_GZ,
    ArchiveType.TAR
)

// Single source of truth for which entries load a real thumbnail through Coil.
private fun thumbnailData(item: FileItem): File? = when (item.fileType) {
    FileType.IMAGE, FileType.VIDEO, FileType.APK -> item.file // Coil's custom Fetchers handle these
    else -> null
}

/**
 * Builds a tightly-capped thumbnail request:
 * - INEXACT precision lets Coil decode smaller than requested instead of rounding up.
 * - Video frames are sampled at 0.5 s: frame 0 of many MP4/MKV files is black,
 *   which made video thumbnails render as solid dark squares.
 */
@Composable
private fun rememberThumbModel(item: FileItem, sizePx: Int): ImageRequest {
    val context = LocalContext.current
    return remember(item.path, sizePx) {
        val frameMicros = when {
            item.fileType != FileType.VIDEO -> 0L
            item.duration != null -> item.duration / 2
            else -> 1_000_000L // Default to 1s if duration unknown
        }

        ImageRequest.Builder(context)
            .data(item.file)
            .size(sizePx * 2)
            .precision(Precision.INEXACT)
            .crossfade(true)
            .apply {
                if (item.fileType == FileType.VIDEO) {
                    videoFrameMicros(frameMicros)
                    // Bypasses MimeTypeMap gaps (mkv/avi/etc. previously decoded
                    // to nothing) by forcing the frame decoder for video loads.
                    decoderFactory(DirectVideoDecoderFactory)
                }
            }
            .build()
    }
}

/**
 * Thumbnail container with the file-type icon painted underneath the image, so
 * a failed/slow decode shows a meaningful icon instead of an empty square.
 * The caller owns the container size through [modifier].
 */
@Composable
private fun ThumbnailSlot(
    fallbackIcon: ImageVector,
    tint: Color,
    item: FileItem,
    decodeSizePx: Int,
    corner: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(tint.copy(alpha = 0.16f))
    ) {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = null,
            tint = tint.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.Center)
                .size(36.dp)
        )
        AsyncImage(
            model = rememberThumbModel(item, decodeSizePx),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ---------------------------------------------------------------------------
// Shared card plumbing (deduplicated across list/grid/expressive cards)
// ---------------------------------------------------------------------------

/** Icon + category color resolved once per item, folder tint themed per palette. */
@Composable
private fun rememberIconTint(item: FileItem): Pair<ImageVector, Color> {
    val themedFolderColor = MaterialTheme.colorScheme.primary
    val osType = LocalOsType.current
    return remember(item.fileType, item.archiveType, themedFolderColor, osType) {
        getFileIconAndColor(item, themedFolderColor, osType)
    }
}

/** Haptic-wrapped click handlers kept identity-stable across recompositions. */
private class CardGestures(val click: () -> Unit, val longClick: () -> Unit)

@Composable
private fun rememberCardGestures(onClick: () -> Unit, onLongClick: () -> Unit): CardGestures {
    val view = LocalView.current
    val latestClick by rememberUpdatedState(onClick)
    val latestLongClick by rememberUpdatedState(onLongClick)
    return remember {
        CardGestures(
            click = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                latestClick()
            },
            longClick = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                latestLongClick()
            }
        )
    }
}

@Composable
private fun rememberMetaSizeText(item: FileItem): String {
    val empty = stringResource(R.string.list_empty_dir)
    val unknown = stringResource(R.string.list_size_unknown)
    return if (item.isDirectory) {
        item.childCount?.let { pluralStringResource(R.plurals.list_items_suffix, it, it) } ?: empty
    } else {
        remember(item.size, unknown) { item.size?.let { formatFileSize(it) } ?: unknown }
    }
}

@Composable
private fun rememberMetaDateText(item: FileItem): String {
    val loading = stringResource(R.string.list_date_loading)
    return remember(item.lastModified, loading) {
        formatDate(item.lastModified).takeUnless { it == DATE_LOADING_SENTINEL } ?: loading
    }
}

@Composable
private fun Modifier.cardTap(shape: Shape, gestures: CardGestures): Modifier =
    this
        // Clip before the click modifier so the ripple follows the card's
        // rounded corners instead of spilling as a sharp rectangle.
        .clip(shape)
        .combinedClickable(
            onClick = gestures.click,
            onLongClick = gestures.longClick
        )

@Composable
private fun ArchiveBadge(color: Color, typeLabel: String) {
    CookieBadge(
        text = typeLabel,
        backgroundColor = color.copy(alpha = 0.15f),
        contentColor = color
    )
}

@Composable
private fun SelectionCheckbox(isSelected: Boolean, onTap: () -> Unit) {
    Checkbox(
        checked = isSelected,
        onCheckedChange = { onTap() },
        colors = CheckboxDefaults.colors(
            checkedColor = MaterialTheme.colorScheme.primary,
            checkmarkColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListCard(
    item: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier,
    showParentPath: Boolean = false
) {
    val (icon, color) = rememberIconTint(item)
    val gestures = rememberCardGestures(onClick, onLongClick)
    // MagicOS: rounder, borderless, flat cards.
    val magic = LocalOsType.current == OsType.MAGIC_OS
    val cardShape = osCardShape()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .cardTap(cardShape, gestures),
        shape = cardShape,
        color = if (isSelected) lerp(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface, 0.3f)
                else MaterialTheme.colorScheme.surface,
        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        shadowElevation = if (magic) 0.dp else if (isSelected) 3.dp else 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon or Image Thumbnail (icon stays underneath as error fallback)
            val thumbnail = thumbnailData(item)
            if (thumbnail != null) {
                ThumbnailSlot(
                    fallbackIcon = icon,
                    tint = color,
                    item = item,
                    decodeSizePx = 64,
                    corner = 16.dp,
                    modifier = Modifier.size(48.dp)
                )
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

                    if (item.fileType == FileType.ARCHIVE && item.archiveType != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        ArchiveBadge(color, item.archiveType.label)
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = rememberMetaSizeText(item),
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
                        text = rememberMetaDateText(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (showParentPath) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.file.parentFile?.absolutePath
                            ?: item.path.substringBeforeLast('/', "/"),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isSelectionMode) {
                SelectionCheckbox(isSelected) { gestures.click() }
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
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier
) {
    val (icon, color) = rememberIconTint(item)
    val gestures = rememberCardGestures(onClick, onLongClick)
    val magic = LocalOsType.current == OsType.MAGIC_OS
    val cardShape = osCardShape()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .cardTap(cardShape, gestures),
        shape = cardShape,
        color = if (isSelected) lerp(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface, 0.3f)
                else MaterialTheme.colorScheme.surface,
        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        shadowElevation = if (magic) 0.dp else if (isSelected) 3.dp else 1.dp
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                val thumbnail = thumbnailData(item)
                if (thumbnail != null) {
                    ThumbnailSlot(
                        fallbackIcon = icon,
                        tint = color,
                        item = item,
                        decodeSizePx = 88,
                        corner = 20.dp,
                        modifier = Modifier.size(68.dp)
                    )
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

                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = rememberMetaSizeText(item),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelectionMode) {
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    SelectionCheckbox(isSelected) { gestures.click() }
                }
            } else if (item.fileType == FileType.ARCHIVE && item.archiveType != null) {
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    ArchiveBadge(color, item.archiveType.label)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileExpressiveCard(
    item: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier
) {
    val (icon, color) = rememberIconTint(item)
    val gestures = rememberCardGestures(onClick, onLongClick)

    val cardShadow by animateDpAsState(
        targetValue = if (isSelected) 4.dp else 2.dp,
        label = "expressive_shadow"
    )

    val magic = LocalOsType.current == OsType.MAGIC_OS
    val cardShape = osCardShape(expressive = true)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .cardTap(cardShape, gestures),
        shape = cardShape,
        color = if (isSelected) lerp(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface, 0.25f)
                else MaterialTheme.colorScheme.surface,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shadowElevation = if (magic) 0.dp else cardShadow
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                val thumbnail = thumbnailData(item)
                if (thumbnail != null) {
                    ThumbnailSlot(
                        fallbackIcon = icon,
                        tint = color,
                        item = item,
                        decodeSizePx = 144,
                        corner = 24.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(115.dp)
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

                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        SelectionCheckbox(isSelected) { gestures.click() }
                    }
                }

                if (item.fileType == FileType.ARCHIVE && item.archiveType != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        ArchiveBadge(color, item.archiveType.label)
                    }
                } else if (item.isDirectory) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        CookieBadge(
                            text = stringResource(R.string.badge_folder),
                            backgroundColor = color.copy(alpha = 0.18f),
                            contentColor = color
                        )
                    }
                }
            }

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
                        text = rememberMetaSizeText(item),
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
                        text = rememberMetaDateText(item),
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
