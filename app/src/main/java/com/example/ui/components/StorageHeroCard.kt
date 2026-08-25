package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FileType
import com.example.model.StorageStats
import com.example.ui.theme.ArchiveZipColor
import com.example.ui.theme.AudioColor
import com.example.ui.theme.ChunkyIconShape
import com.example.ui.theme.ChunkyTileShape
import com.example.ui.theme.DocumentColor
import com.example.ui.theme.ImageColor
import com.example.ui.theme.PillShape
import com.example.ui.theme.VideoColor
import com.example.ui.theme.VibrantCoralContainer
import com.example.ui.theme.VibrantCoralDark
import com.example.ui.theme.VibrantLavenderContainer
import com.example.ui.theme.VibrantLavenderDark
import com.example.ui.theme.VibrantSkyBlueContainer
import com.example.ui.theme.VibrantSkyBlueDark
import com.example.ui.theme.VibrantAzureContainer
import com.example.ui.theme.VibrantAzureDark

@Composable
fun StorageHeroCard(
    stats: StorageStats,
    onCategoryClick: (FileType?) -> Unit,
    onAnalyzeStorageClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCollapsed: Boolean = false,
    onToggleCollapse: () -> Unit = {}
) {
    val usedRatio = if (stats.totalBytes > 0) {
        (stats.usedBytes.toFloat() / stats.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0.5f

    val animatedUsedRatio by animateFloatAsState(
        targetValue = usedRatio,
        animationSpec = tween(durationMillis = 800),
        label = "storage_ratio"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Vibrant Lilac Storage Hero Box (rounded-[32px] with #EADDFF and #21005D text)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp)),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 20.dp,
                        vertical = if (isCollapsed) 14.dp else 20.dp
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onToggleCollapse() }
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = formatFileSize(stats.usedBytes).substringBefore(' '),
                                fontSize = if (isCollapsed) 24.sp else 32.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                letterSpacing = (-0.5).sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${formatFileSize(stats.usedBytes).substringAfter(' ')} used",
                                fontSize = if (isCollapsed) 14.sp else 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Circular ring button to open analytics
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .size(if (isCollapsed) 44.dp else 50.dp)
                                .cookieClickable(onClick = onAnalyzeStorageClick)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { animatedUsedRatio },
                                    modifier = Modifier.size(if (isCollapsed) 22.dp else 26.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                    strokeWidth = 3.dp,
                                    strokeCap = StrokeCap.Round
                                )
                            }
                        }

                        // Collapse / Expand Toggle Button
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                            modifier = Modifier
                                .size(if (isCollapsed) 44.dp else 50.dp)
                                .cookieClickable(onClick = onToggleCollapse)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isCollapsed) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = if (isCollapsed) "Expand storage overview" else "Collapse storage overview",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // Expanded Section: Progress Bar & Detailed Free Space
                AnimatedVisibility(
                    visible = !isCollapsed,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(14.dp))

                        // Vibrant Progress Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(PillShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(animatedUsedRatio)
                                    .clip(PillShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "${formatFileSize(stats.freeBytes)} remaining of ${formatFileSize(stats.totalBytes)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }

        // Expanded Section: Vibrant 2-Grid Quick Category Cards
        AnimatedVisibility(
            visible = !isCollapsed,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Archives Card
                    VibrantCategoryCard(
                        title = "ARCHIVES",
                        subtitle = "7z, ZIP, RAR, TAR",
                        icon = Icons.Rounded.FolderZip,
                        iconBg = VibrantSkyBlueContainer,
                        iconTint = VibrantSkyBlueDark,
                        onClick = { onCategoryClick(FileType.ARCHIVE) },
                        modifier = Modifier.weight(1f)
                    )

                    // Images Card
                    VibrantCategoryCard(
                        title = "IMAGES",
                        subtitle = "${formatFileSize(stats.imagesBytes)}",
                        icon = Icons.Rounded.Image,
                        iconBg = VibrantCoralContainer,
                        iconTint = VibrantCoralDark,
                        onClick = { onCategoryClick(FileType.IMAGE) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Documents Card
                    VibrantCategoryCard(
                        title = "DOCUMENTS",
                        subtitle = "${formatFileSize(stats.documentsBytes)}",
                        icon = Icons.Rounded.Description,
                        iconBg = VibrantAzureContainer,
                        iconTint = VibrantAzureDark,
                        onClick = { onCategoryClick(FileType.DOCUMENT) },
                        modifier = Modifier.weight(1f)
                    )

                    // Audio & Media Card
                    VibrantCategoryCard(
                        title = "AUDIO",
                        subtitle = "${formatFileSize(stats.audioBytes)}",
                        icon = Icons.Rounded.Audiotrack,
                        iconBg = VibrantLavenderContainer,
                        iconTint = VibrantLavenderDark,
                        onClick = { onCategoryClick(FileType.AUDIO) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun VibrantCategoryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .cookieClickable(shape = RoundedCornerShape(26.dp), onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 42.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
