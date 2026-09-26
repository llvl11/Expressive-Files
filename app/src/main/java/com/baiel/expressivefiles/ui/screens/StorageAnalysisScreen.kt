package com.baiel.expressivefiles.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.baiel.expressivefiles.ui.components.SquigglyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baiel.expressivefiles.R
import com.baiel.expressivefiles.model.FileType
import com.baiel.expressivefiles.model.StorageStats
import com.baiel.expressivefiles.ui.components.ChunkyIconButton
import com.baiel.expressivefiles.ui.components.CookieIconContainer
import com.baiel.expressivefiles.ui.components.formatFileSize
import com.baiel.expressivefiles.ui.theme.ApkColor
import com.baiel.expressivefiles.ui.theme.ArchiveZipColor
import com.baiel.expressivefiles.ui.theme.AudioColor
import com.baiel.expressivefiles.ui.theme.ChunkyIconShape
import com.baiel.expressivefiles.ui.theme.ChunkyTileShape
import com.baiel.expressivefiles.ui.theme.DocumentColor
import com.baiel.expressivefiles.ui.theme.ImageColor
import com.baiel.expressivefiles.ui.components.fileTypeIcon
import com.baiel.expressivefiles.ui.theme.VideoColor
import com.baiel.expressivefiles.viewmodel.FileViewModel

private data class StorageCategorySpec(
    val title: String,
    val bytes: (StorageStats) -> Long,
    val icon: ImageVector,
    val color: Color,
    val type: FileType
)

@Composable
private fun storageCategories(): List<StorageCategorySpec> = listOf(
    StorageCategorySpec(stringResource(R.string.analysis_cat_archives), { it.archivesBytes }, fileTypeIcon(FileType.ARCHIVE), ArchiveZipColor, FileType.ARCHIVE),
    StorageCategorySpec(stringResource(R.string.analysis_cat_images), { it.imagesBytes }, fileTypeIcon(FileType.IMAGE), ImageColor, FileType.IMAGE),
    StorageCategorySpec(stringResource(R.string.analysis_cat_videos), { it.videosBytes }, fileTypeIcon(FileType.VIDEO), VideoColor, FileType.VIDEO),
    StorageCategorySpec(stringResource(R.string.analysis_cat_documents), { it.documentsBytes }, fileTypeIcon(FileType.DOCUMENT), DocumentColor, FileType.DOCUMENT),
    StorageCategorySpec(stringResource(R.string.analysis_cat_audio), { it.audioBytes }, fileTypeIcon(FileType.AUDIO), AudioColor, FileType.AUDIO),
    StorageCategorySpec(stringResource(R.string.analysis_cat_apks), { it.apkBytes }, fileTypeIcon(FileType.APK), ApkColor, FileType.APK)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalysisScreen(
    viewModel: FileViewModel,
    onNavigateBack: () -> Unit,
    onOpenCategory: (FileType) -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.loadStorageStats(deepScan = true)
    }

    val stats by viewModel.storageStats.collectAsStateWithLifecycle()

    val total = if (stats.totalBytes > 0) stats.totalBytes else 1L
    val usedRatio = (stats.usedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.analysis_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black
                    )
                },
                navigationIcon = {
                    ChunkyIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        onClick = onNavigateBack,
                        contentDescription = stringResource(R.string.breadcrumb_back),
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
                        text = stringResource(R.string.analysis_total_used_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatFileSize(stats.usedBytes),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.analysis_free_of_total, formatFileSize(stats.freeBytes), formatFileSize(stats.totalBytes)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    SquigglyProgressIndicator(
                        progress = usedRatio,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                        // Main card shows the squiggly stroke only - no
                        // straight track line behind it.
                        showTrack = false
                    )
                }
            }

            Text(
                text = stringResource(R.string.analysis_breakdown_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )
            // getStorageStats stops after 2 levels / 1200 files, so the
            // category numbers are a sample - say so instead of implying a
            // full accounting of every byte.
            Text(
                text = stringResource(R.string.analysis_breakdown_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
            )

            // Category breakdown cards
            storageCategories().forEach { spec ->
                StorageCategoryRow(
                    title = spec.title,
                    bytes = spec.bytes(stats),
                    icon = spec.icon,
                    color = spec.color,
                    onClick = { onOpenCategory(spec.type) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StorageCategoryRow(
    title: String,
    bytes: Long,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
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



