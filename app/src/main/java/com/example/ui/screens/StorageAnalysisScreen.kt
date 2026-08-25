package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.ui.components.ChunkyIconButton
import com.example.ui.components.CookieBadge
import com.example.ui.components.CookieIconContainer
import com.example.ui.components.formatFileSize
import com.example.ui.theme.ApkColor
import com.example.ui.theme.ArchiveZipColor
import com.example.ui.theme.AudioColor
import com.example.ui.theme.ChunkyIconShape
import com.example.ui.theme.ChunkyTileShape
import com.example.ui.theme.DocumentColor
import com.example.ui.theme.ImageColor
import com.example.ui.theme.OtherFileColor
import com.example.ui.theme.PillShape
import com.example.ui.theme.VideoColor
import com.example.viewmodel.FileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalysisScreen(
    viewModel: FileViewModel,
    onNavigateBack: () -> Unit,
    onOpenCategory: (FileType) -> Unit
) {
    val stats by viewModel.storageStats.collectAsState()

    val total = if (stats.totalBytes > 0) stats.totalBytes else 1L
    val usedRatio = (stats.usedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Storage Analysis",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                },
                navigationIcon = {
                    ChunkyIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        onClick = onNavigateBack,
                        size = 40.dp,
                        shape = ChunkyIconShape,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main Overview Card
            Surface(
                shape = ChunkyTileShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Total Space Used",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatFileSize(stats.usedBytes)}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Free space: ${formatFileSize(stats.freeBytes)} of ${formatFileSize(stats.totalBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    LinearProgressIndicator(
                        progress = { usedRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(PillShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                        strokeCap = StrokeCap.Round
                    )
                }
            }

            Text(
                text = "Categories Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )

            // Category breakdown cards
            StorageCategoryRow(
                title = "Archives & Backups (7z, Tar, Zip, Rar)",
                bytes = stats.archivesBytes,
                totalBytes = stats.totalBytes,
                icon = Icons.Rounded.FolderZip,
                color = ArchiveZipColor,
                onClick = { onOpenCategory(FileType.ARCHIVE) }
            )

            StorageCategoryRow(
                title = "Images & Photos",
                bytes = stats.imagesBytes,
                totalBytes = stats.totalBytes,
                icon = Icons.Rounded.Image,
                color = ImageColor,
                onClick = { onOpenCategory(FileType.IMAGE) }
            )

            StorageCategoryRow(
                title = "Videos & Movies",
                bytes = stats.videosBytes,
                totalBytes = stats.totalBytes,
                icon = Icons.Rounded.Movie,
                color = VideoColor,
                onClick = { onOpenCategory(FileType.VIDEO) }
            )

            StorageCategoryRow(
                title = "Documents & PDFs",
                bytes = stats.documentsBytes,
                totalBytes = stats.totalBytes,
                icon = Icons.Rounded.Description,
                color = DocumentColor,
                onClick = { onOpenCategory(FileType.DOCUMENT) }
            )

            StorageCategoryRow(
                title = "Audio & Music",
                bytes = stats.audioBytes,
                totalBytes = stats.totalBytes,
                icon = Icons.Rounded.Audiotrack,
                color = AudioColor,
                onClick = { onOpenCategory(FileType.AUDIO) }
            )

            StorageCategoryRow(
                title = "Installed Apps & APKs",
                bytes = stats.apkBytes,
                totalBytes = stats.totalBytes,
                icon = Icons.Rounded.Android,
                color = ApkColor,
                onClick = { onOpenCategory(FileType.APK) }
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StorageCategoryRow(
    title: String,
    bytes: Long,
    totalBytes: Long,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    val ratio = if (totalBytes > 0) (bytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    Surface(
        shape = ChunkyTileShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(ChunkyTileShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CookieIconContainer(
                backgroundColor = color,
                size = 46.dp,
                shape = ChunkyIconShape
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatFileSize(bytes),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
